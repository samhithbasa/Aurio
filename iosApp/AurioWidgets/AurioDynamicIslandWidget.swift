import ActivityKit
import WidgetKit
import SwiftUI

/**
 * Native Apple Dynamic Island & Lock Screen Live Activity Widget for Aurio.
 */
struct AurioDynamicIslandWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AurioPlaybackAttributes.self) { context in
            // MARK: - Lock Screen & Banner Presentation
            LockScreenLiveActivityView(state: context.state)
                .activityBackgroundTint(Color(red: 0.08, green: 0.08, blue: 0.12).opacity(0.85))
                .activitySystemActionForegroundColor(Color.white)
        } dynamicIsland: { context in
            DynamicIsland {
                // MARK: - Expanded Dynamic Island (Long Press)
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 10) {
                        AsyncImage(url: URL(string: context.state.thumbnailUrl)) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Image(systemName: "music.note")
                                .foregroundColor(.white.opacity(0.6))
                        }
                        .frame(width: 44, height: 44)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(context.state.title)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            Text(context.state.artist)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.white.opacity(0.7))
                                .lineLimit(1)
                        }
                    }
                    .padding(.leading, 6)
                }
                
                DynamicIslandExpandedRegion(.trailing) {
                    if context.state.spatialModeName != "OFF" {
                        Text(context.state.spatialModeName)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(red: 0.20, green: 0.60, blue: 1.00))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(Color(red: 0.20, green: 0.60, blue: 1.00).opacity(0.2))
                            .clipShape(Capsule())
                    }
                }
                
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 8) {
                        // Progress Bar
                        let progress = context.state.durationSeconds > 0 
                            ? context.state.playbackPositionSeconds / context.state.durationSeconds 
                            : 0.0
                        
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(Color.white.opacity(0.2))
                                    .frame(height: 4)
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(Color(red: 0.20, green: 0.60, blue: 1.00))
                                    .frame(width: geo.size.width * CGFloat(progress), height: 4)
                            }
                        }
                        .frame(height: 4)
                        
                        // Control Buttons
                        HStack {
                            Text(formatTime(context.state.playbackPositionSeconds))
                                .font(.system(size: 10, weight: .medium, design: .monospaced))
                                .foregroundColor(.white.opacity(0.5))
                            
                            Spacer()
                            
                            HStack(spacing: 24) {
                                Button(intent: AurioSkipPreviousIntent()) {
                                    Image(systemName: "backward.fill")
                                        .font(.system(size: 16))
                                        .foregroundColor(.white)
                                }
                                .buttonStyle(.plain)
                                
                                Button(intent: AurioPlayPauseIntent()) {
                                    Image(systemName: context.state.isPlaying ? "pause.fill" : "play.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(.white)
                                }
                                .buttonStyle(.plain)
                                
                                Button(intent: AurioSkipNextIntent()) {
                                    Image(systemName: "forward.fill")
                                        .font(.system(size: 16))
                                        .foregroundColor(.white)
                                }
                                .buttonStyle(.plain)
                            }
                            
                            Spacer()
                            
                            Text(formatTime(context.state.durationSeconds))
                                .font(.system(size: 10, weight: .medium, design: .monospaced))
                                .foregroundColor(.white.opacity(0.5))
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.bottom, 6)
                }
            } compactLeading: {
                // MARK: - Compact Leading View (Pill Left)
                HStack(spacing: 4) {
                    AsyncImage(url: URL(string: context.state.thumbnailUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Image(systemName: "music.note")
                            .foregroundColor(.white)
                    }
                    .frame(width: 18, height: 18)
                    .clipShape(Circle())
                }
                .padding(.leading, 4)
            } compactTrailing: {
                // MARK: - Compact Trailing View (Pill Right)
                HStack(spacing: 3) {
                    if context.state.spatialModeName != "OFF" {
                        Text(context.state.spatialModeName)
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Color(red: 0.20, green: 0.60, blue: 1.00))
                    }
                    
                    // Animated Equalizer Waveform Bars
                    EqualizerWaveBar(height: context.state.isPlaying ? 12 : 4)
                    EqualizerWaveBar(height: context.state.isPlaying ? 16 : 6)
                    EqualizerWaveBar(height: context.state.isPlaying ? 10 : 3)
                }
                .padding(.trailing, 4)
            } minimal: {
                // MARK: - Minimal Presentation
                Image(systemName: "music.note")
                    .font(.system(size: 11))
                    .foregroundColor(Color(red: 0.20, green: 0.60, blue: 1.00))
            }
        }
    }
}

/** Lock Screen Live Activity Card View */
private struct LockScreenLiveActivityView: View {
    let state: AurioPlaybackAttributes.ContentState
    
    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: URL(string: state.thumbnailUrl)) { image in
                image.resizable().aspectRatio(contentMode: .fill)
            } placeholder: {
                Image(systemName: "music.note")
                    .foregroundColor(.white.opacity(0.6))
            }
            .frame(width: 52, height: 52)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            
            VStack(alignment: .leading, spacing: 3) {
                Text(state.title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                Text(state.artist)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.white.opacity(0.7))
                    .lineLimit(1)
            }
            
            Spacer()
            
            if state.spatialModeName != "OFF" {
                Text(state.spatialModeName)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(red: 0.20, green: 0.60, blue: 1.00))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color(red: 0.20, green: 0.60, blue: 1.00).opacity(0.25))
                    .clipShape(Capsule())
            }
        }
        .padding(14)
    }
}

private struct EqualizerWaveBar: View {
    let height: CGFloat
    
    var body: some View {
        RoundedRectangle(cornerRadius: 1)
            .fill(Color(red: 0.20, green: 0.60, blue: 1.00))
            .frame(width: 2.5, height: height)
            .animation(.easeInOut(duration: 0.35).repeatForever(autoreverses: true), value: height)
    }
}

private func formatTime(_ seconds: Double) -> String {
    guard !seconds.isNaN && !seconds.isInfinite && seconds > 0 else { return "0:00" }
    let totalSecs = Int(seconds)
    let mins = totalSecs / 60
    let secs = totalSecs % 60
    return String(format: "%d:%02d", mins, secs)
}

// App Intents for Interactive Dynamic Island Controls
import AppIntents

struct AurioPlayPauseIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Play/Pause"
    func perform() async throws -> some IntentResult {
        return .result()
    }
}

struct AurioSkipNextIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Next Track"
    func perform() async throws -> some IntentResult {
        return .result()
    }
}

struct AurioSkipPreviousIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Previous Track"
    func perform() async throws -> some IntentResult {
        return .result()
    }
}
