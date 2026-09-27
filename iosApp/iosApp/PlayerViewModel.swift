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
    
    // Default High Quality Popular Songs
    @Published var popularSongs: [IosSong] = [
        IosSong(
            id: "die_with_a_smile",
            title: "Die With A Smile",
            artist: "Lady Gaga, Bruno Mars",
            album: "Die With A Smile",
            durationSeconds: 251,
            durationText: "4:11",
            thumbnailUrl: "https://c.saavncdn.com/060/Die-With-A-Smile-English-2024-20240816103634-500x500.jpg",
            streamUrl: "https://aac.saavncdn.com/060/ba610a711f7c11f7e025ec0d39e2307f_320.mp4",
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
            streamUrl: "https://aac.saavncdn.com/396/45452f1026027a4d55b0a7dbf2d96c9c_320.mp4",
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
            streamUrl: "https://aac.saavncdn.com/707/04e9a03fc5b9ea463a566f12015df389_320.mp4",
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
            streamUrl: "https://aac.saavncdn.com/077/8bbff563bc750ea75e6d6eb52670e3ad_320.mp4",
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
            streamUrl: "https://aac.saavncdn.com/635/2aa7080f4f728c31cbdd17e33528b80b_320.mp4",
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
            streamUrl: "https://aac.saavncdn.com/228/1bc9ce4876b51c142c9433e2182be57c_320.mp4",
            isSpatial: true
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
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
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
        
        guard !song.streamUrl.isEmpty, let url = URL(string: song.streamUrl) else {
            Task { @MainActor in
                let searchMatch = await JioSaavnMusicService.shared.searchSongs(query: "\(song.title) \(song.artist)")
                if let found = searchMatch.first, !found.streamUrl.isEmpty, let directUrl = URL(string: found.streamUrl) {
                    var updated = song
                    updated.streamUrl = found.streamUrl
                    self.currentSong = updated
                    self.startPlayback(with: directUrl)
                }
            }
            return
        }
        
        startPlayback(with: url)
    }
    
    private func startPlayback(with url: URL) {
        let item = AVPlayerItem(url: url)
        if avPlayer == nil {
            avPlayer = AVPlayer(playerItem: item)
        } else {
            avPlayer?.replaceCurrentItem(with: item)
        }
        
        removeTimeObserver()
        addTimeObserver()
        
        avPlayer?.play()
        self.isPlaying = true
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
