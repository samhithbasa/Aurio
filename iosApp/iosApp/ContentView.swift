import SwiftUI
import SharedAurio

struct ContentView: View {
    @StateObject private var playerViewModel = IosPlayerObservable()

    var body: some View {
        ZStack {
            Color(red: 0.05, green: 0.05, blue: 0.08).ignoresSafeArea()
            
            VStack(spacing: 20) {
                // Aurio iOS Header
                HStack {
                    Text("Aurio")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                
                Spacer()
                
                // Active Track & Dynamic Island Preview Card
                VStack(spacing: 14) {
                    Image(systemName: "music.note.list")
                        .font(.system(size: 48))
                        .foregroundColor(Color(red: 0.20, green: 0.60, blue: 1.00))
                    
                    Text("Aurio Spatial Engine Active")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("16D Duality Orbit & Dynamic Island Ready")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white.opacity(0.6))
                }
                .padding(30)
                .background(
                    RoundedRectangle(cornerRadius: 24)
                        .fill(Color.white.opacity(0.06))
                        .overlay(
                            RoundedRectangle(cornerRadius: 24)
                                .stroke(Color(red: 0.20, green: 0.60, blue: 1.00).opacity(0.3), lineWidth: 1)
                        )
                )
                .padding(.horizontal, 20)
                
                Spacer()
            }
        }
    }
}

class IosPlayerObservable: ObservableObject {
    let playerManager = IosAudioPlayerManager()
}
