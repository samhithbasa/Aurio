import SwiftUI
import AVFoundation
import CoreMedia

// MARK: - Player ViewModel
class IosPlayerViewModel: ObservableObject {
    static let shared = IosPlayerViewModel()
    
    @Published var currentSong: IosSong? = nil
    @Published var isPlaying: Bool = false
    @Published var currentTime: Double = 0.0
    @Published var duration: Double = 1.0
    @Published var spatialMode: String = "16D"
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
    private var searchTask: Task<Void, Never>? = nil
    
    // Verified 320kbps High Quality Popular Songs (200 OK)
    @Published var popularSongs: [IosSong] = [
        IosSong(
            id: "VaNhRJHr",
            title: "Die With A Smile",
            artist: "Lady Gaga, Bruno Mars",
            album: "Die With A Smile",
            durationSeconds: 250,
            durationText: "4:10",
            thumbnailUrl: "https://c.saavncdn.com/060/Die-With-A-Smile-English-2024-20240816103634-500x500.webp",
            streamUrl: "https://aac.saavncdn.com/060/05bb6ae7a01edcbd8e0d859d2fa1d83d_320.mp4",
            isSpatial: true
        ),
        IosSong(
            id: "TcDP-KUl",
            title: "Starboy",
            artist: "The Weeknd ft. Daft Punk",
            album: "Starboy",
            durationSeconds: 230,
            durationText: "3:50",
            thumbnailUrl: "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.webp",
            streamUrl: "https://aac.saavncdn.com/396/b4e570050007b056c662f2a98c9f28ec_320.mp4",
            isSpatial: true
        ),
        IosSong(
            id: "JR8ew5Yw",
            title: "BIRDS OF A FEATHER",
            artist: "Billie Eilish",
            album: "HIT ME HARD AND SOFT",
            durationSeconds: 210,
            durationText: "3:30",
            thumbnailUrl: "https://c.saavncdn.com/707/HIT-ME-HARD-AND-SOFT-English-2024-20240517063536-500x500.webp",
            streamUrl: "https://aac.saavncdn.com/707/761fa325ce0600e1463336f0431d82b3_320.mp4",
            isSpatial: true
        ),
        IosSong(
            id: "fW-Mxsnu",
            title: "Blinding Lights",
            artist: "The Weeknd",
            album: "After Hours",
            durationSeconds: 200,
            durationText: "3:20",
            thumbnailUrl: "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.webp",
            streamUrl: "https://aac.saavncdn.com/077/0b02a92687d1ae3369b6859f44872e52_320.mp4",
            isSpatial: true
        ),
        IosSong(
            id: "rjkrTnma",
            title: "Kesariya",
            artist: "Pritam, Arijit Singh",
            album: "Brahmastra",
            durationSeconds: 268,
            durationText: "4:28",
            thumbnailUrl: "https://c.saavncdn.com/871/Brahmastra-Original-Motion-Picture-Soundtrack-Hindi-2022-20221006155213-500x500.webp",
            streamUrl: "https://aac.saavncdn.com/871/c2febd353f3a076a406fa37510f31f9f_320.mp4",
            isSpatial: true
        ),
        IosSong(
            id: "4SQmljgZ",
            title: "Cruel Summer",
            artist: "Taylor Swift",
            album: "Lover",
            durationSeconds: 178,
            durationText: "2:58",
            thumbnailUrl: "https://c.saavncdn.com/243/The-Cruelest-Summer-English-2023-20231109123211-500x500.webp",
            streamUrl: "https://aac.saavncdn.com/243/cf6b522de1390996fdbe109298873c72_320.mp4",
            isSpatial: true
        ),
        IosSong(
            id: "fy1SYD17",
            title: "Calm Down",
            artist: "Rema, Selena Gomez",
            album: "Rave & Roses",
            durationSeconds: 239,
            durationText: "3:59",
            thumbnailUrl: "https://c.saavncdn.com/596/Calm-Down-English-2022-20220826054039-500x500.webp",
            streamUrl: "https://aac.saavncdn.com/596/0044bdbc00972a8496e65a68b1444597_320.mp4",
            isSpatial: false
        )
    ]
    
    init() {
        setupAudioSession()
        startOrbitAnimation()
        if let first = popularSongs.first {
            self.currentSong = first
            self.duration = Double(first.durationSeconds)
        }
        
        Task { @MainActor in
            let live = await JioSaavnMusicService.shared.fetchTrendingCharts()
            if !live.isEmpty {
                self.popularSongs = live
            }
        }
    }
    
    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .moviePlayback, options: [.allowAirPlay, .allowBluetooth, .allowBluetoothA2DP])
            try session.setActive(true, options: [])
        } catch {
            print("Audio Session error: \(error)")
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
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            self.searchResults = []
            self.isSearching = false
            return
        }
        
        self.isSearching = true
        searchTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }
            let results = await JioSaavnMusicService.shared.searchSongs(query: trimmed)
            guard !Task.isCancelled else { return }
            self.searchResults = results
            self.isSearching = false
        }
    }
    
    func playSong(_ song: IosSong) {
        self.currentSong = song
        self.duration = Double(song.durationSeconds > 0 ? song.durationSeconds : 210)
        self.currentTime = 0.0
        
        if !song.streamUrl.isEmpty, let url = URL(string: song.streamUrl) {
            startPlayback(with: url)
            return
        }
        
        Task { @MainActor in
            // 1. Direct song detail stream resolution by ID
            if let stream = await JioSaavnMusicService.shared.fetchStreamUrl(songId: song.id), let directUrl = URL(string: stream) {
                var updated = song
                updated.streamUrl = stream
                self.currentSong = updated
                self.startPlayback(with: directUrl)
                return
            }
            
            // 2. Search fallback
            let searchMatch = await JioSaavnMusicService.shared.searchSongs(query: "\(song.title) \(song.artist)")
            if let found = searchMatch.first, !found.streamUrl.isEmpty, let directUrl = URL(string: found.streamUrl) {
                var updated = song
                updated.streamUrl = found.streamUrl
                self.currentSong = updated
                self.startPlayback(with: directUrl)
            }
        }
    }
    
    private func startPlayback(with url: URL) {
        setupAudioSession()
        
        let asset = AVURLAsset(url: url)
        let item = AVPlayerItem(asset: asset)
        
        if self.avPlayer == nil {
            let player = AVPlayer(playerItem: item)
            player.automaticallyWaitsToMinimizeStalling = false
            player.volume = 1.0
            player.isMuted = false
            self.avPlayer = player
        } else {
            self.avPlayer?.replaceCurrentItem(with: item)
            self.avPlayer?.volume = 1.0
            self.avPlayer?.isMuted = false
        }
        
        removeTimeObserver()
        addTimeObserver()
        
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: nil)
        NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            self?.playNext()
        }
        
        self.avPlayer?.playImmediately(atRate: 1.0)
        self.avPlayer?.play()
        self.isPlaying = true
    }
    
    func togglePlayPause() {
        if isPlaying {
            avPlayer?.pause()
            isPlaying = false
        } else {
            if let current = currentSong {
                if avPlayer == nil || avPlayer?.currentItem == nil {
                    playSong(current)
                } else {
                    avPlayer?.play()
                    isPlaying = true
                }
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
            let currentSec = CMTimeGetSeconds(time)
            if currentSec.isFinite && currentSec >= 0 {
                self.currentTime = currentSec
            }
            if let item = self.avPlayer?.currentItem {
                let durSec = CMTimeGetSeconds(item.duration)
                if durSec.isFinite && durSec > 0 {
                    self.duration = durSec
                }
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
