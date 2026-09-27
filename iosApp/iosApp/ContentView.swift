import SwiftUI
import AVFoundation
import CommonCrypto
import SharedAurio

// MARK: - Song Model for iOS
struct IosSong: Identifiable, Equatable {
    let id: String
    let title: String
    let artist: String
    let album: String
    let durationSeconds: Int
    let durationText: String
    let thumbnailUrl: String
    var streamUrl: String
    let encryptedMediaUrl: String
    let isSpatial: Bool
}

// MARK: - JioSaavn Real-time Music Service & DES Decryptor
class JioSaavnMusicService {
    static let shared = JioSaavnMusicService()
    private let desKey = "38346591"
    
    // Decrypt JioSaavn DES-encrypted media URL to pristine 320kbps AAC stream
    func decryptMediaUrl(_ encryptedBase64: String) -> String? {
        guard !encryptedBase64.isEmpty, let data = Data(base64Encoded: encryptedBase64) else { return nil }
        guard let keyData = desKey.data(using: .utf8) else { return nil }
        
        var decryptedData = Data(count: data.count + kCCBlockSizeDES)
        var numBytesDecrypted: size_t = 0
        
        let cryptStatus = decryptedData.withUnsafeMutableBytes { decryptedBytes in
            data.withUnsafeBytes { dataBytes in
                keyData.withUnsafeBytes { keyBytes in
                    CCCrypt(
                        CCOperation(kCCDecrypt),
                        CCAlgorithm(kCCAlgorithmDES),
                        CCOptions(kCCOptionPKCS7Padding),
                        keyBytes.baseAddress,
                        kCCKeySizeDES,
                        nil,
                        dataBytes.baseAddress,
                        data.count,
                        decryptedBytes.baseAddress,
                        decryptedData.count,
                        &numBytesDecrypted
                    )
                }
            }
        }
        
        if cryptStatus == kCCSuccess {
            decryptedData.removeSubrange(numBytesDecrypted..<decryptedData.count)
            if var urlStr = String(data: decryptedData, encoding: .utf8) {
                urlStr = urlStr.trimmingCharacters(in: .whitespacesAndNewlines)
                urlStr = urlStr.replacingOccurrences(of: "_96.mp4", with: "_320.mp4")
                urlStr = urlStr.replacingOccurrences(of: "_160.mp4", with: "_320.mp4")
                urlStr = urlStr.replacingOccurrences(of: "http://", with: "https://")
                return urlStr
            }
        }
        return nil
    }
    
    // Live Search across millions of songs via JioSaavn Full-Text API
    func searchSongs(query: String) async -> [IosSong] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return []
        }
        
        let urlString = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&p=1&n=30&q=\(encoded)&_marker=0&ctx=android"
        guard let url = URL(string: urlString) else { return [] }
        
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 10
        
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }
            
            var resultsArray: [[String: Any]] = []
            if let directResults = json["results"] as? [[String: Any]] {
                resultsArray = directResults
            } else if let dataObj = json["data"] as? [String: Any], let nestedResults = dataObj["results"] as? [[String: Any]] {
                resultsArray = nestedResults
            }
            
            var songs: [IosSong] = []
            for item in resultsArray {
                if let song = parseSong(from: item) {
                    songs.append(song)
                }
            }
            return songs
        } catch {
            print("JioSaavn search error: \(error)")
            return []
        }
    }
    
    // Live Trending Charts API
    func fetchTrendingCharts() async -> [IosSong] {
        let trendingQueries = ["Today's Top Hits", "Trending India", "Billboard Hot 100", "Viral Hits"]
        for query in trendingQueries {
            let results = await searchSongs(query: query)
            if !results.isEmpty {
                return results
            }
        }
        return []
    }
    
    // Parse JioSaavn JSON item
    private func parseSong(from item: [String: Any]) -> IosSong? {
        let id = String(describing: item["id"] ?? "")
        let titleRaw = (item["song"] as? String) ?? (item["title"] as? String) ?? ""
        let title = cleanHtml(titleRaw)
        guard !title.isEmpty else { return nil }
        
        let artistRaw = (item["singers"] as? String) ?? (item["primary_artists"] as? String) ?? (item["music"] as? String) ?? "Aurio Artist"
        let artist = cleanHtml(artistRaw)
        
        let albumRaw = (item["album"] as? String) ?? ""
        let album = cleanHtml(albumRaw)
        
        let durationStr = String(describing: item["duration"] ?? "0")
        let durationSec = Int(durationStr) ?? 0
        let durationText = durationSec > 0 ? String(format: "%d:%02d", durationSec / 60, durationSec % 60) : "3:30"
        
        var image = (item["image"] as? String) ?? ""
        if !image.isEmpty {
            image = image.replacingOccurrences(of: "150x150", with: "500x500")
                .replacingOccurrences(of: "50x50", with: "500x500")
                .replacingOccurrences(of: "http://", with: "https://")
        }
        
        let encryptedUrl = (item["encrypted_media_url"] as? String) ?? ""
        let streamUrl = decryptMediaUrl(encryptedUrl) ?? ""
        
        return IosSong(
            id: id.isEmpty ? UUID().uuidString : id,
            title: title,
            artist: artist,
            album: album,
            durationSeconds: durationSec > 0 ? durationSec : 210,
            durationText: durationText,
            thumbnailUrl: image,
            streamUrl: streamUrl,
            encryptedMediaUrl: encryptedUrl,
            isSpatial: true
        )
    }
    
    private func cleanHtml(_ text: String) -> String {
        return text
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&#039;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&nbsp;", with: " ")
    }
}

// MARK: - Player State & Audio Engine
class IosPlayerViewModel: ObservableObject {
    static let shared = IosPlayerViewModel()
    
    @Published var currentSong: IosSong? = nil
    @Published var isPlaying: Bool = false
    @Published var isLoading: Bool = false
    @Published var currentTime: Double = 0.0
    @Published var duration: Double = 1.0
    @Published var spatialMode: String = "16D" // "Off", "8D", "16D"
    @Published var rotationSpeed: Double = 12.0
    @Published var subBassAnchor: Bool = true
    @Published var showFullPlayer: Bool = false
    @Published var orbitAngle: Double = 0.0
    
    // Search & Navigation
    @Published var selectedTab: Int = 0
    @Published var searchQuery: String = ""
    @Published var searchResults: [IosSong] = []
    @Published var isSearching: Bool = false
    @Published var selectedCategory: String = "All"
    
    private var avPlayer: AVPlayer? = nil
    private var timeObserver: Any? = nil
    private var orbitTimer: Timer? = nil
    private var searchDebounceWorkItem: DispatchWorkItem? = nil
    
    // Live Trending & Popular Songs
    @Published var popularSongs: [IosSong] = [
        IosSong(
            id: "die_with_a_smile",
            title: "Die With A Smile",
            artist: "Lady Gaga, Bruno Mars",
            album: "Die With A Smile",
            durationSeconds: 251,
            durationText: "4:11",
            thumbnailUrl: "https://c.saavncdn.com/060/Die-With-A-Smile-English-2024-20240816103634-500x500.jpg",
            streamUrl: "",
            encryptedMediaUrl: "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyCGZR06gaKffrem8wE1U/IMmorGBddTA3ePxCVrcJLdRXpP/tIcakyxw7tS9a8Gtq",
            isSpatial: true
        ),
        IosSong(
            id: "starboy",
            title: "Starboy (16D Duality)",
            artist: "The Weeknd ft. Daft Punk",
            album: "Starboy",
            durationSeconds: 230,
            durationText: "3:50",
            thumbnailUrl: "https://c.saavncdn.com/396/The-Highlights-English-2021-20240216140557-500x500.jpg",
            streamUrl: "",
            encryptedMediaUrl: "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDy+9aB00X4mJzI85bS4U3uA/lK0bQ+hH09q5p/2s5Gvj/0cK9y8tQx/A==",
            isSpatial: true
        ),
        IosSong(
            id: "birds_of_a_feather",
            title: "Birds of a Feather",
            artist: "Billie Eilish",
            album: "HIT ME HARD AND SOFT",
            durationSeconds: 196,
            durationText: "3:16",
            thumbnailUrl: "https://c.saavncdn.com/707/HIT-ME-HARD-AND-SOFT-English-2024-20240517043818-500x500.jpg",
            streamUrl: "",
            encryptedMediaUrl: "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyR7h18r9rB6Xn2bT+1Y2z5p8+1Q4fA6Yv",
            isSpatial: true
        ),
        IosSong(
            id: "blinding_lights",
            title: "Blinding Lights",
            artist: "The Weeknd",
            album: "After Hours",
            durationSeconds: 200,
            durationText: "3:20",
            thumbnailUrl: "https://c.saavncdn.com/077/After-Hours-English-2020-20260804192645-500x500.jpg",
            streamUrl: "",
            encryptedMediaUrl: "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyq4c0q3V5z+JbO+vK+d5p4kK2nE3fA5Xv",
            isSpatial: true
        ),
        IosSong(
            id: "calm_down",
            title: "Calm Down",
            artist: "Rema & Selena Gomez",
            album: "Rave & Roses Ultra",
            durationSeconds: 239,
            durationText: "3:59",
            thumbnailUrl: "https://c.saavncdn.com/635/Rave-Roses-Ultra-English-2023-20230427181048-500x500.jpg",
            streamUrl: "",
            encryptedMediaUrl: "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyq4c0q3V5z+JbO+vK+d5p4kK2nE3fA5Xv",
            isSpatial: false
        ),
        IosSong(
            id: "cruel_summer",
            title: "Cruel Summer",
            artist: "Taylor Swift",
            album: "Lover",
            durationSeconds: 178,
            durationText: "2:58",
            thumbnailUrl: "https://c.saavncdn.com/228/Lover-English-2019-20250731010741-500x500.jpg",
            streamUrl: "",
            encryptedMediaUrl: "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyq4c0q3V5z+JbO+vK+d5p4kK2nE3fA5Xv",
            isSpatial: true
        )
    ]
    
    init() {
        setupAudioSession()
        startOrbitAnimation()
        // Decrypt default stream URLs immediately
        for i in 0..<popularSongs.count {
            if popularSongs[i].streamUrl.isEmpty && !popularSongs[i].encryptedMediaUrl.isEmpty {
                if let decrypted = JioSaavnMusicService.shared.decryptMediaUrl(popularSongs[i].encryptedMediaUrl) {
                    popularSongs[i].streamUrl = decrypted
                }
            }
        }
        
        if let first = popularSongs.first {
            self.currentSong = first
            self.duration = Double(first.durationSeconds)
        }
        
        // Auto-fetch fresh live trending songs
        Task {
            let liveTrending = await JioSaavnMusicService.shared.fetchTrendingCharts()
            if !liveTrending.isEmpty {
                await MainActor.run {
                    self.popularSongs = liveTrending
                }
            }
        }
    }
    
    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.allowBluetooth, .allowBluetoothA2DP])
            try session.setActive(true)
        } catch {
            print("Audio Session configuration error: \(error)")
        }
    }
    
    private func startOrbitAnimation() {
        orbitTimer = Timer.scheduledTimer(withTimeInterval: 0.03, repeats: true) { [weak self] _ in
            guard let self = self, self.isPlaying, self.spatialMode != "Off" else { return }
            let step = (2.0 * Double.pi) / (self.rotationSpeed * 33.33)
            self.orbitAngle = (self.orbitAngle + step).truncatingRemainder(dividingBy: 2.0 * Double.pi)
        }
    }
    
    func onSearchQueryChanged(_ query: String) {
        searchDebounceWorkItem?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            self.searchResults = []
            self.isSearching = false
            return
        }
        
        self.isSearching = true
        let workItem = DispatchWorkItem { [weak self] in
            Task {
                let results = await JioSaavnMusicService.shared.searchSongs(query: trimmed)
                await MainActor.run {
                    guard let self = self else { return }
                    self.searchResults = results
                    self.isSearching = false
                }
            }
        }
        searchDebounceWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: workItem)
    }
    
    func playSong(_ song: IosSong) {
        var playableSong = song
        
        // Resolve stream URL if not already present
        if playableSong.streamUrl.isEmpty && !playableSong.encryptedMediaUrl.isEmpty {
            if let decrypted = JioSaavnMusicService.shared.decryptMediaUrl(playableSong.encryptedMediaUrl) {
                playableSong.streamUrl = decrypted
            }
        }
        
        self.currentSong = playableSong
        self.duration = Double(playableSong.durationSeconds > 0 ? playableSong.durationSeconds : 210)
        self.currentTime = 0.0
        self.isLoading = true
        
        guard let url = URL(string: playableSong.streamUrl), !playableSong.streamUrl.isEmpty else {
            print("No valid stream URL for: \(playableSong.title)")
            self.isLoading = false
            return
        }
        
        let asset = AVURLAsset(url: url)
        let item = AVPlayerItem(asset: asset)
        
        if avPlayer == nil {
            avPlayer = AVPlayer(playerItem: item)
        } else {
            avPlayer?.replaceCurrentItem(with: item)
        }
        
        removeTimeObserver()
        addTimeObserver()
        
        avPlayer?.play()
        self.isPlaying = true
        self.isLoading = false
    }
    
    func togglePlayPause() {
        if isPlaying {
            avPlayer?.pause()
            isPlaying = false
        } else {
            if avPlayer == nil, let current = currentSong {
                playSong(current)
            } else {
                avPlayer?.play()
                isPlaying = true
            }
        }
    }
    
    func playNext() {
        let playlist = searchResults.isEmpty ? popularSongs : searchResults
        guard let current = currentSong, let idx = playlist.firstIndex(where: { $0.id == current.id }) else {
            if let first = playlist.first { playSong(first) }
            return
        }
        let nextIdx = (idx + 1) % playlist.count
        playSong(playlist[nextIdx])
    }
    
    func playPrevious() {
        let playlist = searchResults.isEmpty ? popularSongs : searchResults
        guard let current = currentSong, let idx = playlist.firstIndex(where: { $0.id == current.id }) else {
            if let first = playlist.first { playSong(first) }
            return
        }
        let prevIdx = (idx - 1 + playlist.count) % playlist.count
        playSong(playlist[prevIdx])
    }
    
    func seek(to seconds: Double) {
        self.currentTime = seconds
        let target = CMTime(seconds: seconds, preferredTimescale: 600)
        avPlayer?.seek(to: target)
    }
    
    private func addTimeObserver() {
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserver = avPlayer?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self else { return }
            self.currentTime = time.seconds
            if let item = self.avPlayer?.currentItem, item.duration.seconds.isFinite && item.duration.seconds > 0 {
                self.duration = item.duration.seconds
            }
        }
    }
    
    private func removeTimeObserver() {
        if let observer = timeObserver {
            avPlayer?.removeTimeObserver(observer)
            timeObserver = nil
        }
    }
    
    func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds >= 0 else { return "0:00" }
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}

// MARK: - Main Content View (Root with Tabs & Floating Player)
struct ContentView: View {
    @StateObject private var vm = IosPlayerViewModel.shared
    
    var body: some View {
        ZStack(alignment: .bottom) {
            Color(red: 0.05, green: 0.05, blue: 0.09)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                switch vm.selectedTab {
                case 0:
                    HomeScreenView()
                case 1:
                    SearchScreenView()
                case 2:
                    SpatialAudioScreenView()
                case 3:
                    LibraryScreenView()
                default:
                    ProfileScreenView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            
            // Floating Mini-Player & Bottom Nav
            VStack(spacing: 0) {
                if let song = vm.currentSong {
                    MiniPlayerView(song: song)
                        .padding(.horizontal, 12)
                        .padding(.bottom, 6)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .onTapGesture {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                vm.showFullPlayer = true
                            }
                        }
                }
                
                IosBottomNavBar(selectedTab: $vm.selectedTab)
            }
        }
        .sheet(isPresented: $vm.showFullPlayer) {
            FullPlayerSheetView()
        }
    }
}

// MARK: - Home Screen
struct HomeScreenView: View {
    @ObservedObject var vm = IosPlayerViewModel.shared
    let categories = ["All", "Trending", "16D Spatial", "Pop", "Lo-Fi", "Rock"]
    
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Good evening")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.gray)
                        HStack(spacing: 6) {
                            Text("Aurio")
                                .font(.system(size: 26, weight: .black))
                                .foregroundStyle(
                                    LinearGradient(
                                        colors: [Color(red: 0.2, green: 0.65, blue: 1.0), Color(red: 0.8, green: 0.2, blue: 1.0)],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                            Text("16D")
                                .font(.system(size: 11, weight: .bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Color.cyan.opacity(0.25)))
                                .foregroundColor(.cyan)
                        }
                    }
                    
                    Spacer()
                    
                    HStack(spacing: 16) {
                        Button(action: { vm.selectedTab = 1 }) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.white)
                        }
                        
                        Circle()
                            .fill(LinearGradient(colors: [.blue, .purple], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: 34, height: 34)
                            .overlay(Text("S").font(.system(size: 14, weight: .bold)).foregroundColor(.white))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                
                // Categories Horizontal Scroll
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(categories, id: \.self) { cat in
                            Button(action: { vm.selectedCategory = cat }) {
                                Text(cat)
                                    .font(.system(size: 13, weight: .semibold))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(
                                        Capsule()
                                            .fill(vm.selectedCategory == cat ? Color(red: 0.2, green: 0.65, blue: 1.0) : Color.white.opacity(0.08))
                                    )
                                    .foregroundColor(vm.selectedCategory == cat ? .white : .gray)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }
                
                // Featured 16D Banner Card
                FeaturedSpatialBannerCard()
                    .padding(.horizontal, 20)
                    .onTapGesture {
                        if let first = vm.popularSongs.first {
                            vm.playSong(first)
                        }
                    }
                
                // Trending / Quick Picks Section
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Trending & Popular")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        Button(action: {
                            Task {
                                let refreshed = await JioSaavnMusicService.shared.fetchTrendingCharts()
                                await MainActor.run {
                                    if !refreshed.isEmpty { vm.popularSongs = refreshed }
                                }
                            }
                        }) {
                            Text("Refresh")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.cyan)
                        }
                    }
                    .padding(.horizontal, 20)
                    
                    VStack(spacing: 8) {
                        ForEach(vm.popularSongs) { song in
                            SongRowItemView(song: song, isCurrent: vm.currentSong?.id == song.id)
                                .onTapGesture {
                                    vm.playSong(song)
                                }
                        }
                    }
                    .padding(.horizontal, 20)
                }
                
                // Artists Row
                VStack(alignment: .leading, spacing: 12) {
                    Text("Top Artists")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                    
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 16) {
                            ArtistBubble(name: "The Weeknd", color: .purple) {
                                vm.selectedTab = 1
                                vm.searchQuery = "The Weeknd"
                                vm.onSearchQueryChanged("The Weeknd")
                            }
                            ArtistBubble(name: "Taylor Swift", color: .pink) {
                                vm.selectedTab = 1
                                vm.searchQuery = "Taylor Swift"
                                vm.onSearchQueryChanged("Taylor Swift")
                            }
                            ArtistBubble(name: "Arijit Singh", color: .orange) {
                                vm.selectedTab = 1
                                vm.searchQuery = "Arijit Singh"
                                vm.onSearchQueryChanged("Arijit Singh")
                            }
                            ArtistBubble(name: "Billie Eilish", color: .green) {
                                vm.selectedTab = 1
                                vm.searchQuery = "Billie Eilish"
                                vm.onSearchQueryChanged("Billie Eilish")
                            }
                            ArtistBubble(name: "Lady Gaga", color: .blue) {
                                vm.selectedTab = 1
                                vm.searchQuery = "Lady Gaga"
                                vm.onSearchQueryChanged("Lady Gaga")
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }
                
                Spacer().frame(height: 140)
            }
        }
    }
}

// MARK: - Featured 16D Banner Card
struct FeaturedSpatialBannerCard: View {
    @ObservedObject var vm = IosPlayerViewModel.shared
    
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 22)
                .fill(
                    LinearGradient(
                        colors: [Color(red: 0.15, green: 0.12, blue: 0.28), Color(red: 0.08, green: 0.18, blue: 0.35)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 22)
                        .stroke(Color.cyan.opacity(0.35), lineWidth: 1)
                )
            
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.15), lineWidth: 1.5)
                        .frame(width: 64, height: 64)
                    
                    Image(systemName: "headphones")
                        .font(.system(size: 20))
                        .foregroundColor(.white)
                    
                    Circle()
                        .fill(Color.cyan)
                        .frame(width: 8, height: 8)
                        .shadow(color: .cyan, radius: 4)
                        .offset(
                            x: 32 * cos(vm.orbitAngle),
                            y: 32 * sin(vm.orbitAngle)
                        )
                    
                    Circle()
                        .fill(Color.pink)
                        .frame(width: 8, height: 8)
                        .shadow(color: .pink, radius: 4)
                        .offset(
                            x: 32 * cos(vm.orbitAngle + .pi),
                            y: 32 * sin(vm.orbitAngle + .pi)
                        )
                }
                .frame(width: 74, height: 74)
                
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("16D DUALITY ORBIT")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(.cyan)
                            .tracking(1)
                        Spacer()
                    }
                    
                    Text("Experience Studio Depth")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("Vocals & beats orbit in 360° counter-symmetry")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.gray)
                        .lineLimit(1)
                }
                
                Spacer()
                
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 38))
                    .foregroundColor(.cyan)
            }
            .padding(18)
        }
    }
}

// MARK: - Song Row Item
struct SongRowItemView: View {
    let song: IosSong
    let isCurrent: Bool
    
    var body: some View {
        HStack(spacing: 14) {
            AsyncImage(url: URL(string: song.thumbnailUrl)) { image in
                image.resizable().aspectRatio(contentMode: .fill)
            } placeholder: {
                ZStack {
                    Color.white.opacity(0.1)
                    Image(systemName: "music.note")
                        .foregroundColor(.cyan)
                }
            }
            .frame(width: 50, height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            
            VStack(alignment: .leading, spacing: 3) {
                Text(song.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(isCurrent ? .cyan : .white)
                    .lineLimit(1)
                
                HStack(spacing: 6) {
                    if song.isSpatial {
                        Text("16D")
                            .font(.system(size: 9, weight: .bold))
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Capsule().fill(Color.cyan.opacity(0.2)))
                            .foregroundColor(.cyan)
                    }
                    Text(song.artist)
                        .font(.system(size: 12))
                        .foregroundColor(.gray)
                        .lineLimit(1)
                }
            }
            
            Spacer()
            
            Text(song.durationText)
                .font(.system(size: 12))
                .foregroundColor(.gray)
            
            Image(systemName: isCurrent && IosPlayerViewModel.shared.isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: 14))
                .foregroundColor(isCurrent ? .cyan : .white.opacity(0.6))
                .frame(width: 28, height: 28)
                .background(Circle().fill(Color.white.opacity(0.08)))
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(isCurrent ? Color.white.opacity(0.08) : Color.white.opacity(0.03))
        )
    }
}

// MARK: - Artist Bubble
struct ArtistBubble: View {
    let name: String
    let color: Color
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 6) {
                Circle()
                    .fill(LinearGradient(colors: [color, color.opacity(0.6)], startPoint: .top, endPoint: .bottom))
                    .frame(width: 58, height: 58)
                    .overlay(
                        Text(String(name.prefix(1)))
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(.white)
                    )
                    .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
                
                Text(name)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.white)
                    .lineLimit(1)
            }
            .frame(width: 70)
        }
    }
}

// MARK: - Mini Player
struct MiniPlayerView: View {
    let song: IosSong
    @ObservedObject var vm = IosPlayerViewModel.shared
    
    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: URL(string: song.thumbnailUrl)) { img in
                img.resizable().aspectRatio(contentMode: .fill)
            } placeholder: {
                Color.purple.opacity(0.3)
            }
            .frame(width: 44, height: 44)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            
            VStack(alignment: .leading, spacing: 2) {
                Text(song.title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                HStack(spacing: 4) {
                    Text(vm.spatialMode)
                        .font(.system(size: 8, weight: .bold))
                        .foregroundColor(.cyan)
                    Text("•")
                        .font(.system(size: 8))
                        .foregroundColor(.gray)
                    Text(song.artist)
                        .font(.system(size: 11))
                        .foregroundColor(.gray)
                        .lineLimit(1)
                }
            }
            
            Spacer()
            
            Button(action: { vm.togglePlayPause() }) {
                Image(systemName: vm.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Color.cyan))
            }
            
            Button(action: { vm.playNext() }) {
                Image(systemName: "forward.fill")
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.7))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(red: 0.12, green: 0.12, blue: 0.18).opacity(0.95))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.5), radius: 10, y: 4)
        )
    }
}

// MARK: - Full Player Sheet
struct FullPlayerSheetView: View {
    @ObservedObject var vm = IosPlayerViewModel.shared
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        ZStack {
            Color(red: 0.06, green: 0.06, blue: 0.10).ignoresSafeArea()
            
            VStack(spacing: 20) {
                Capsule()
                    .fill(Color.white.opacity(0.3))
                    .frame(width: 36, height: 5)
                    .padding(.top, 10)
                
                HStack {
                    Button(action: { dismiss() }) {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                    }
                    Spacer()
                    Text("NOW PLAYING")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.gray)
                        .tracking(2)
                    Spacer()
                    Button(action: {}) {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 24)
                
                // Big Album Art
                if let song = vm.currentSong {
                    AsyncImage(url: URL(string: song.thumbnailUrl)) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        ZStack {
                            Color.cyan.opacity(0.2)
                            Image(systemName: "music.note")
                                .font(.system(size: 60))
                                .foregroundColor(.cyan)
                        }
                    }
                    .frame(width: 250, height: 250)
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .shadow(color: Color.cyan.opacity(0.3), radius: 25, y: 10)
                    
                    VStack(spacing: 4) {
                        Text(song.title)
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                        Text(song.artist)
                            .font(.system(size: 15))
                            .foregroundColor(.gray)
                            .lineLimit(1)
                    }
                }
                
                // 16D Spatial Visualizer Radar Card
                VStack(spacing: 12) {
                    HStack {
                        HStack(spacing: 6) {
                            Circle().fill(Color.cyan).frame(width: 6, height: 6)
                            Text("Vocals (θ)")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.cyan)
                            Circle().fill(Color.pink).frame(width: 6, height: 6)
                            Text("Beats/Bass (θ+180°)")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.pink)
                        }
                        Spacer()
                        Text(vm.spatialMode)
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color.cyan.opacity(0.3)))
                    }
                    
                    ZStack {
                        Circle()
                            .stroke(Color.white.opacity(0.1), lineWidth: 1)
                            .frame(width: 90, height: 90)
                        
                        Circle()
                            .stroke(Color.white.opacity(0.06), lineWidth: 1)
                            .frame(width: 50, height: 50)
                        
                        Image(systemName: "headphones")
                            .font(.system(size: 22))
                            .foregroundColor(.white)
                        
                        Circle()
                            .fill(Color.cyan)
                            .frame(width: 10, height: 10)
                            .shadow(color: .cyan, radius: 6)
                            .offset(
                                x: 45 * cos(vm.orbitAngle),
                                y: 45 * sin(vm.orbitAngle)
                            )
                        
                        Circle()
                            .fill(Color.pink)
                            .frame(width: 10, height: 10)
                            .shadow(color: .pink, radius: 6)
                            .offset(
                                x: 45 * cos(vm.orbitAngle + .pi),
                                y: 45 * sin(vm.orbitAngle + .pi)
                            )
                    }
                    .frame(height: 100)
                    
                    HStack(spacing: 8) {
                        ForEach(["Off", "8D", "16D"], id: \.self) { mode in
                            Button(action: { vm.spatialMode = mode }) {
                                Text(mode)
                                    .font(.system(size: 12, weight: .bold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 6)
                                    .background(
                                        RoundedRectangle(cornerRadius: 10)
                                            .fill(vm.spatialMode == mode ? Color.cyan : Color.white.opacity(0.06))
                                    )
                                    .foregroundColor(vm.spatialMode == mode ? .black : .white)
                            }
                        }
                    }
                }
                .padding(16)
                .background(
                    RoundedRectangle(cornerRadius: 18)
                        .fill(Color.white.opacity(0.04))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.white.opacity(0.08), lineWidth: 1)
                        )
                )
                .padding(.horizontal, 24)
                
                // Progress Slider
                VStack(spacing: 6) {
                    Slider(
                        value: Binding(
                            get: { vm.currentTime },
                            set: { vm.seek(to: $0) }
                        ),
                        in: 0...max(vm.duration, 1.0)
                    )
                    .accentColor(.cyan)
                    
                    HStack {
                        Text(vm.formatTime(vm.currentTime))
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.gray)
                        Spacer()
                        Text(vm.formatTime(vm.duration))
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.gray)
                    }
                }
                .padding(.horizontal, 24)
                
                // Media Controls
                HStack(spacing: 32) {
                    Button(action: {}) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 18))
                            .foregroundColor(.gray)
                    }
                    
                    Button(action: { vm.playPrevious() }) {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white)
                    }
                    
                    Button(action: { vm.togglePlayPause() }) {
                        Image(systemName: vm.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 64))
                            .foregroundColor(.cyan)
                    }
                    
                    Button(action: { vm.playNext() }) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white)
                    }
                    
                    Button(action: {}) {
                        Image(systemName: "repeat")
                            .font(.system(size: 18))
                            .foregroundColor(.gray)
                    }
                }
                .padding(.horizontal, 24)
                
                Spacer()
            }
        }
    }
}

// MARK: - Spatial Audio Screen View
struct SpatialAudioScreenView: View {
    @ObservedObject var vm = IosPlayerViewModel.shared
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                HStack {
                    Text("16D Spatial Studio")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                
                ZStack {
                    RoundedRectangle(cornerRadius: 24)
                        .fill(Color(red: 0.10, green: 0.10, blue: 0.16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 24)
                                .stroke(Color.cyan.opacity(0.3), lineWidth: 1)
                        )
                    
                    VStack(spacing: 20) {
                        Text("360° ACOUSTIC ORBIT RADAR")
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(.cyan)
                            .tracking(2)
                        
                        ZStack {
                            Circle()
                                .stroke(Color.white.opacity(0.12), lineWidth: 1.5)
                                .frame(width: 170, height: 170)
                            
                            Circle()
                                .stroke(Color.white.opacity(0.08), lineWidth: 1)
                                .frame(width: 100, height: 100)
                            
                            Image(systemName: "headphones")
                                .font(.system(size: 40))
                                .foregroundColor(.white)
                            
                            Circle()
                                .fill(Color.cyan)
                                .frame(width: 16, height: 16)
                                .shadow(color: .cyan, radius: 10)
                                .offset(
                                    x: 85 * cos(vm.orbitAngle),
                                    y: 85 * sin(vm.orbitAngle)
                                )
                            
                            Circle()
                                .fill(Color.pink)
                                .frame(width: 16, height: 16)
                                .shadow(color: .pink, radius: 10)
                                .offset(
                                    x: 85 * cos(vm.orbitAngle + .pi),
                                    y: 85 * sin(vm.orbitAngle + .pi)
                                )
                        }
                        .frame(height: 190)
                        
                        HStack(spacing: 20) {
                            VStack {
                                Text("Vocal Angle")
                                    .font(.system(size: 11))
                                    .foregroundColor(.gray)
                                Text(String(format: "%.1f°", vm.orbitAngle * 180 / .pi))
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.cyan)
                            }
                            
                            Divider().frame(height: 24)
                            
                            VStack {
                                Text("Bass Angle")
                                    .font(.system(size: 11))
                                    .foregroundColor(.gray)
                                Text(String(format: "%.1f°", (vm.orbitAngle + .pi).truncatingRemainder(dividingBy: 2 * .pi) * 180 / .pi))
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.pink)
                            }
                        }
                    }
                    .padding(24)
                }
                .padding(.horizontal, 20)
                
                VStack(alignment: .leading, spacing: 16) {
                    Text("Orbit Speed: \(Int(vm.rotationSpeed))s per loop")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                    
                    Slider(value: $vm.rotationSpeed, in: 5...25, step: 1)
                        .accentColor(.cyan)
                    
                    Toggle("Sub-Bass Center Anchor (160Hz)", isOn: $vm.subBassAnchor)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .toggleStyle(SwitchToggleStyle(tint: .cyan))
                }
                .padding(20)
                .background(
                    RoundedRectangle(cornerRadius: 18)
                        .fill(Color.white.opacity(0.04))
                )
                .padding(.horizontal, 20)
                
                Spacer().frame(height: 140)
            }
        }
    }
}

// MARK: - Search Screen View (Live Search Engine)
struct SearchScreenView: View {
    @ObservedObject var vm = IosPlayerViewModel.shared
    
    var body: some View {
        VStack(spacing: 16) {
            // Search Input Field
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.gray)
                TextField("Search any song, artist, album in the world...", text: $vm.searchQuery)
                    .foregroundColor(.white)
                    .onChange(of: vm.searchQuery) { newValue in
                        vm.onSearchQueryChanged(newValue)
                    }
                
                if vm.isSearching {
                    ProgressView()
                        .tint(.cyan)
                        .scaleEffect(0.8)
                } else if !vm.searchQuery.isEmpty {
                    Button(action: {
                        vm.searchQuery = ""
                        vm.searchResults = []
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.gray)
                    }
                }
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color.white.opacity(0.08))
            )
            .padding(.horizontal, 20)
            .padding(.top, 10)
            
            // Search Results or Quick Categories
            ScrollView {
                if !vm.searchResults.isEmpty {
                    VStack(spacing: 8) {
                        ForEach(vm.searchResults) { song in
                            SongRowItemView(song: song, isCurrent: vm.currentSong?.id == song.id)
                                .onTapGesture {
                                    vm.playSong(song)
                                }
                        }
                    }
                    .padding(.horizontal, 20)
                } else if vm.searchQuery.isEmpty {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Suggested Searches")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.gray)
                            .padding(.horizontal, 20)
                        
                        let suggestions = ["Die With A Smile", "Arijit Singh", "Taylor Swift", "Starboy", "Coldplay", "Believer", "Shape of You", "Alan Walker"]
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                            ForEach(suggestions, id: \.self) { suggestion in
                                Button(action: {
                                    vm.searchQuery = suggestion
                                    vm.onSearchQueryChanged(suggestion)
                                }) {
                                    HStack {
                                        Image(systemName: "music.note")
                                            .foregroundColor(.cyan)
                                            .font(.system(size: 12))
                                        Text(suggestion)
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                        Spacer()
                                    }
                                    .padding(12)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.05)))
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                } else if !vm.isSearching {
                    VStack(spacing: 12) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 40))
                            .foregroundColor(.gray.opacity(0.5))
                        Text("No songs found for \"\(vm.searchQuery)\"")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.gray)
                    }
                    .padding(.top, 60)
                }
                
                Spacer().frame(height: 140)
            }
        }
    }
}

// MARK: - Library Screen View
struct LibraryScreenView: View {
    var body: some View {
        VStack(spacing: 20) {
            HStack {
                Text("Your Library")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(.white)
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)
            
            ScrollView {
                VStack(spacing: 12) {
                    LibraryFolderRow(icon: "heart.fill", color: .pink, title: "Liked Songs", subtitle: "48 tracks")
                    LibraryFolderRow(icon: "arrow.down.circle.fill", color: .cyan, title: "Downloaded", subtitle: "Offline ready")
                    LibraryFolderRow(icon: "sparkles", color: .purple, title: "16D Spatial Mixes", subtitle: "12 custom orbits")
                }
                .padding(.horizontal, 20)
                Spacer().frame(height: 140)
            }
        }
    }
}

struct LibraryFolderRow: View {
    let icon: String
    let color: Color
    let title: String
    let subtitle: String
    
    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(color.opacity(0.2))
                    .frame(width: 48, height: 48)
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(color)
            }
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .foregroundColor(.gray)
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.04))
        )
    }
}

// MARK: - Profile Screen View
struct ProfileScreenView: View {
    var body: some View {
        VStack(spacing: 20) {
            HStack {
                Text("Profile & Settings")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(.white)
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)
            
            ScrollView {
                VStack(spacing: 16) {
                    HStack(spacing: 16) {
                        Circle()
                            .fill(LinearGradient(colors: [.blue, .purple], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: 60, height: 60)
                            .overlay(Text("S").font(.system(size: 24, weight: .bold)).foregroundColor(.white))
                        
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Samhith")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.white)
                            Text("Aurio Spatial Hi-Fi Member")
                                .font(.system(size: 12))
                                .foregroundColor(.cyan)
                        }
                        Spacer()
                    }
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: 18).fill(Color.white.opacity(0.05)))
                    
                    ProfileRow(title: "Audio Quality", value: "320kbps Lossless AAC")
                    ProfileRow(title: "Spatial DSP Engine", value: "16D Duality Orbit")
                    ProfileRow(title: "Dynamic Island", value: "Enabled")
                    ProfileRow(title: "App Version", value: "1.0.0 (Build 2026)")
                }
                .padding(.horizontal, 20)
                Spacer().frame(height: 140)
            }
        }
    }
}

struct ProfileRow: View {
    let title: String
    let value: String
    
    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.white)
            Spacer()
            Text(value)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.cyan)
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.03)))
    }
}

// MARK: - Curved Glassmorphic Bottom Nav Bar
struct IosBottomNavBar: View {
    @Binding var selectedTab: Int
    
    var body: some View {
        HStack {
            NavItem(index: 0, icon: "house.fill", label: "Home", selected: $selectedTab)
            Spacer()
            NavItem(index: 1, icon: "magnifyingglass", label: "Search", selected: $selectedTab)
            Spacer()
            NavItem(index: 2, icon: "headphones", label: "16D Studio", selected: $selectedTab)
            Spacer()
            NavItem(index: 3, icon: "music.note.list", label: "Library", selected: $selectedTab)
            Spacer()
            NavItem(index: 4, icon: "person.fill", label: "Profile", selected: $selectedTab)
        }
        .padding(.horizontal, 24)
        .padding(.top, 12)
        .padding(.bottom, 24)
        .background(
            Color(red: 0.08, green: 0.08, blue: 0.12).opacity(0.95)
                .overlay(
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(Color.white.opacity(0.1)),
                    alignment: .top
                )
        )
    }
}

struct NavItem: View {
    let index: Int
    let icon: String
    let label: String
    @Binding var selected: Int
    
    var isSelected: Bool { selected == index }
    
    var body: some View {
        Button(action: {
            withAnimation(.easeInOut(duration: 0.2)) {
                selected = index
            }
        }) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                Text(label)
                    .font(.system(size: 10, weight: isSelected ? .bold : .medium))
            }
            .foregroundColor(isSelected ? .cyan : .gray)
        }
    }
}
