import SwiftUI

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
                            x: CGFloat(32.0 * cos(vm.orbitAngle)),
                            y: CGFloat(32.0 * sin(vm.orbitAngle))
                        )
                    
                    Circle()
                        .fill(Color.pink)
                        .frame(width: 8, height: 8)
                        .shadow(color: .pink, radius: 4)
                        .offset(
                            x: CGFloat(32.0 * cos(vm.orbitAngle + Double.pi)),
                            y: CGFloat(32.0 * sin(vm.orbitAngle + Double.pi))
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
                                x: CGFloat(45.0 * cos(vm.orbitAngle)),
                                y: CGFloat(45.0 * sin(vm.orbitAngle))
                            )
                        
                        Circle()
                            .fill(Color.pink)
                            .frame(width: 10, height: 10)
                            .shadow(color: .pink, radius: 6)
                            .offset(
                                x: CGFloat(45.0 * cos(vm.orbitAngle + Double.pi)),
                                y: CGFloat(45.0 * sin(vm.orbitAngle + Double.pi))
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
