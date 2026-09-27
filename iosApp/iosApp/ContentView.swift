import SwiftUI
import SharedAurio

// MARK: - Root Content View
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
