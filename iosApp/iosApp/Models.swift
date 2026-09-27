import Foundation

// MARK: - Cross-platform Song Model for iOS
struct IosSong: Identifiable, Equatable {
    let id: String
    let title: String
    let artist: String
    let album: String
    let durationSeconds: Int
    let durationText: String
    let thumbnailUrl: String
    var streamUrl: String
    let isSpatial: Bool
}
