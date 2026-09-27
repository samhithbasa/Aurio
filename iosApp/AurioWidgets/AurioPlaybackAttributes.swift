import ActivityKit
import WidgetKit
import SwiftUI

/**
 * ActivityKit attributes for Aurio Live Activity and Dynamic Island.
 */
public struct AurioPlaybackAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var title: String
        public var artist: String
        public var thumbnailUrl: String
        public var isPlaying: Bool
        public var playbackPositionSeconds: Double
        public var durationSeconds: Double
        public var spatialModeName: String // "OFF", "8D", "16D"
        
        public init(
            title: String,
            artist: String,
            thumbnailUrl: String,
            isPlaying: Bool,
            playbackPositionSeconds: Double,
            durationSeconds: Double,
            spatialModeName: String
        ) {
            self.title = title
            self.artist = artist
            self.thumbnailUrl = thumbnailUrl
            self.isPlaying = isPlaying
            self.playbackPositionSeconds = playbackPositionSeconds
            self.durationSeconds = durationSeconds
            self.spatialModeName = spatialModeName
        }
    }
    
    public var songId: String
    
    public init(songId: String) {
        self.songId = songId
    }
}
