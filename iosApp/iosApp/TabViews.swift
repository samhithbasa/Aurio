import SwiftUI

// MARK: - Home Screen View
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

// MARK: - Spatial Audio Studio Screen
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
                                    x: CGFloat(85.0 * cos(vm.orbitAngle)),
                                    y: CGFloat(85.0 * sin(vm.orbitAngle))
                                )
                            
                            Circle()
                                .fill(Color.pink)
                                .frame(width: 16, height: 16)
                                .shadow(color: .pink, radius: 10)
                                .offset(
                                    x: CGFloat(85.0 * cos(vm.orbitAngle + Double.pi)),
                                    y: CGFloat(85.0 * sin(vm.orbitAngle + Double.pi))
                                )
                        }
                        .frame(height: 190)
                        
                        HStack(spacing: 20) {
                            VStack {
                                Text("Vocal Angle")
                                    .font(.system(size: 11))
                                    .foregroundColor(.gray)
                                Text(String(format: "%.1f°", vm.orbitAngle * 180.0 / Double.pi))
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.cyan)
                            }
                            
                            Divider().frame(height: 24)
                            
                            VStack {
                                Text("Bass Angle")
                                    .font(.system(size: 11))
                                    .foregroundColor(.gray)
                                Text(String(format: "%.1f°", (vm.orbitAngle + Double.pi).truncatingRemainder(dividingBy: 2.0 * Double.pi) * 180.0 / Double.pi))
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

// MARK: - Search Screen View
struct SearchScreenView: View {
    @ObservedObject var vm = IosPlayerViewModel.shared
    
    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.gray)
                TextField("Search any song, artist, album in the world...", text: Binding(
                    get: { vm.searchQuery },
                    set: { newVal in
                        vm.searchQuery = newVal
                        vm.onSearchQueryChanged(newVal)
                    }
                ))
                .foregroundColor(.white)
                
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
