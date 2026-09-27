import ActivityKit
import Foundation

/**
 * Bridge manager to start, update, and end iOS Live Activities / Dynamic Island for Aurio.
 */
@objc public class AurioLiveActivityBridge: NSObject {
    @objc public static let shared = AurioLiveActivityBridge()
    
    private var currentActivity: Activity<AurioPlaybackAttributes>?
    
    @objc public func startLiveActivity(
        songId: String,
        title: String,
        artist: String,
        thumbnailUrl: String,
        isPlaying: Bool,
        playbackPositionSeconds: Double,
        durationSeconds: Double,
        spatialModeName: String
    ) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        
        let attributes = AurioPlaybackAttributes(songId: songId)
        let initialContentState = AurioPlaybackAttributes.ContentState(
            title: title,
            artist: artist,
            thumbnailUrl: thumbnailUrl,
            isPlaying: isPlaying,
            playbackPositionSeconds: playbackPositionSeconds,
            durationSeconds: durationSeconds,
            spatialModeName: spatialModeName
        )
        
        do {
            let activity = try Activity<AurioPlaybackAttributes>.request(
                attributes: attributes,
                content: .init(state: initialContentState, staleDate: nil)
            )
            self.currentActivity = activity
        } catch {
            print("Failed to start Aurio Live Activity: \(error)")
        }
    }
    
    @objc public func updateLiveActivity(
        title: String,
        artist: String,
        thumbnailUrl: String,
        isPlaying: Bool,
        playbackPositionSeconds: Double,
        durationSeconds: Double,
        spatialModeName: String
    ) {
        guard let activity = currentActivity else { return }
        
        let updatedState = AurioPlaybackAttributes.ContentState(
            title: title,
            artist: artist,
            thumbnailUrl: thumbnailUrl,
            isPlaying: isPlaying,
            playbackPositionSeconds: playbackPositionSeconds,
            durationSeconds: durationSeconds,
            spatialModeName: spatialModeName
        )
        
        Task {
            await activity.update(.init(state: updatedState, staleDate: nil))
        }
    }
    
    @objc public func endLiveActivity() {
        guard let activity = currentActivity else { return }
        Task {
            await activity.end(nil, dismissalPolicy: .immediate)
            self.currentActivity = nil
        }
    }
}
