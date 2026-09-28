import SwiftUI
import UIKit

// MARK: - Safe Bundle Image Loader Helper
func loadAurioImage(named name: String) -> UIImage? {
    if let img = UIImage(named: name) {
        return img
    }
    if let path = Bundle.main.path(forResource: name, ofType: "png"), let img = UIImage(contentsOfFile: path) {
        return img
    }
    if let path = Bundle.main.path(forResource: name, ofType: "png", inDirectory: "Assets"), let img = UIImage(contentsOfFile: path) {
        return img
    }
    // Also check current directory for simulator bundle
    let currentDir = Bundle.main.bundlePath
    let directPath = (currentDir as NSString).appendingPathComponent("\(name).png")
    if let img = UIImage(contentsOfFile: directPath) {
        return img
    }
    return nil
}

// MARK: - Auth State Enum
enum IosAuthMode {
    case login
    case signUp
    case otpVerification
}

// MARK: - Main Auth Screen View
struct AuthScreenView: View {
    @ObservedObject var vm = IosPlayerViewModel.shared
    @State private var authMode: IosAuthMode = .login
    
    // Form States
    @State private var loginEmail: String = ""
    @State private var loginPassword: String = ""
    @State private var rememberMe: Bool = true
    @State private var isLoginPasswordVisible: Bool = false
    
    @State private var signUpEmail: String = ""
    @State private var signUpPassword: String = ""
    @State private var signUpConfirmPassword: String = ""
    @State private var isSignUpPasswordVisible: Bool = false
    @State private var isSignUpConfirmPasswordVisible: Bool = false
    
    // OTP States
    @State private var otpDigits: [String] = ["", "", "", ""]
    @FocusState private var focusedOtpIndex: Int?
    @State private var isOtpVerifying: Bool = false
    @State private var isOtpSuccess: Bool = false
    @State private var otpErrorMessage: String? = nil
    @State private var resendTimer: Int = 45
    @State private var timerRunning: Bool = false
    
    // UI Feedback
    @State private var errorMessage: String? = nil
    @State private var flipRotation: Double = 0.0
    
    var body: some View {
        ZStack {
            // Layer 1: Background Studio Art & Lighting
            backgroundLayer
            
            // Layer 2: Ambient Text Scribbles ("Feel Every Beat ♡" & "GOOD MUSIC BRIGHTER DAYS")
            ambientTextLayer
            
            // Layer 3: Scrollable Content with Centered Frosted Glass Card
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    Spacer().frame(height: 180)
                    
                    // The 3D Floating Mascot & Frosted Card
                    ZStack(alignment: .top) {
                        // Frosted Glass Card Body
                        frostedCardBody
                            .padding(.top, 40)
                        
                        // 3D Mascot Logo resting in the top notch
                        mascotLogoView
                            .offset(y: -42)
                    }
                    .padding(.horizontal, 20)
                    
                    Spacer().frame(height: 20)
                    
                    // Bottom Footer: "STREAM • DISCOVER • BELONG"
                    VStack(spacing: 8) {
                        Text("STREAM     •     DISCOVER     •     BELONG")
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(2.8)
                            .foregroundColor(Color(red: 0.35, green: 0.50, blue: 0.70))
                            .multilineTextAlignment(.center)
                        
                        Capsule()
                            .fill(
                                LinearGradient(
                                    colors: [Color(red: 0.2, green: 0.55, blue: 1.0), Color(red: 0.4, green: 0.8, blue: 1.0)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(width: 44, height: 4)
                    }
                    .padding(.bottom, 36)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .ignoresSafeArea(.keyboard, edges: .bottom)
    }
    
    // MARK: - Background Layer
    @ViewBuilder
    private var backgroundLayer: some View {
        if let bgImage = loadAurioImage(named: "Login_Background") {
            Image(uiImage: bgImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .ignoresSafeArea()
        } else {
            // High-fidelity dynamic gradient fallback
            LinearGradient(
                colors: [
                    Color(red: 0.82, green: 0.90, blue: 0.98),
                    Color(red: 0.60, green: 0.78, blue: 0.96),
                    Color(red: 0.25, green: 0.52, blue: 0.88),
                    Color(red: 0.08, green: 0.18, blue: 0.38)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        }
    }
    
    // MARK: - Ambient Text Overlays
    private var ambientTextLayer: some View {
        VStack {
            HStack(alignment: .top) {
                // Top Left: "Feel Every Beat ♡"
                VStack(alignment: .leading, spacing: -2) {
                    Text("Feel")
                        .font(.system(size: 26, weight: .light, design: .serif))
                        .italic()
                        .foregroundColor(Color(red: 0.35, green: 0.60, blue: 0.95).opacity(0.85))
                    Text("Every")
                        .font(.system(size: 28, weight: .light, design: .serif))
                        .italic()
                        .foregroundColor(Color(red: 0.35, green: 0.60, blue: 0.95).opacity(0.85))
                    Text("Beat")
                        .font(.system(size: 32, weight: .semibold, design: .serif))
                        .italic()
                        .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.90).opacity(0.9))
                    Text("♡")
                        .font(.system(size: 22))
                        .foregroundColor(Color(red: 0.25, green: 0.50, blue: 0.90).opacity(0.85))
                        .padding(.leading, 18)
                        .padding(.top, 2)
                }
                .rotationEffect(.degrees(-16))
                .padding(.top, 56)
                .padding(.leading, 16)
                
                Spacer()
                
                // Top Right: "GOOD MUSIC BRIGHTER DAYS"
                VStack(alignment: .trailing, spacing: 4) {
                    ForEach(["GOOD", "MUSIC", "BRIGHTER", "DAYS"], id: \.self) { word in
                        Text(word)
                            .font(.system(size: 13, weight: .black))
                            .tracking(2.5)
                            .foregroundColor(Color(red: 0.30, green: 0.50, blue: 0.75).opacity(0.75))
                    }
                }
                .rotationEffect(.degrees(12))
                .padding(.top, 72)
                .padding(.trailing, 16)
            }
            Spacer()
        }
        .allowsHitTesting(false)
    }
    
    // MARK: - 3D Mascot Logo View
    private var mascotLogoView: some View {
        Group {
            if let logoImg = loadAurioImage(named: "Aurio_Logo") {
                Image(uiImage: logoImg)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 140, height: 140)
                    .shadow(color: Color.blue.opacity(0.3), radius: 16, y: 8)
            } else {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Color(red: 0.3, green: 0.65, blue: 1.0), Color(red: 0.1, green: 0.35, blue: 0.85)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 90, height: 90)
                        .shadow(color: Color.blue.opacity(0.4), radius: 12, y: 6)
                    
                    Image(systemName: "headphones")
                        .font(.system(size: 42, weight: .bold))
                        .foregroundColor(.white)
                }
            }
        }
    }
    
    // MARK: - Frosted Glass Card Body
    private var frostedCardBody: some View {
        ZStack {
            // Ultra-premium Frosted Glass Background
            RoundedRectangle(cornerRadius: 38)
                .fill(Color.white.opacity(0.85))
                .background(
                    RoundedRectangle(cornerRadius: 38)
                        .fill(.ultraThinMaterial)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 38)
                        .stroke(
                            LinearGradient(
                                colors: [
                                    Color.white,
                                    Color.white.opacity(0.8),
                                    Color.cyan.opacity(0.35),
                                    Color.blue.opacity(0.2)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1.5
                        )
                )
                .shadow(color: Color(red: 0.15, green: 0.35, blue: 0.70).opacity(0.20), radius: 28, x: 0, y: 14)
            
            // Card Content
            VStack(spacing: 0) {
                // Brand Header inside card
                VStack(spacing: 2) {
                    HStack(spacing: 0) {
                        Text("Aur")
                            .font(.system(size: 34, weight: .heavy))
                            .foregroundColor(Color(red: 0.06, green: 0.09, blue: 0.16))
                        Text("io")
                            .font(.system(size: 34, weight: .heavy))
                            .foregroundColor(Color(red: 0.20, green: 0.48, blue: 0.96))
                    }
                    
                    Text("M U S I C   M A K E S   A   B E T T E R   Y O U")
                        .font(.system(size: 9.5, weight: .semibold))
                        .tracking(2.2)
                        .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
                }
                .padding(.top, 48)
                .padding(.bottom, 22)
                
                // Form Switches with Smooth Animation
                Group {
                    switch authMode {
                    case .login:
                        loginFormView
                    case .signUp:
                        signUpFormView
                    case .otpVerification:
                        otpVerificationFormView
                    }
                }
                .transition(.asymmetric(insertion: .opacity.combined(with: .scale(scale: 0.98)), removal: .opacity))
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 26)
        }
    }
    
    // MARK: - Login Form View
    private var loginFormView: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Header
            VStack(alignment: .leading, spacing: 3) {
                Text("Welcome Back")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(Color(red: 0.06, green: 0.09, blue: 0.16))
                
                Text("Sign in to continue your music journey")
                    .font(.system(size: 13, weight: .regular))
                    .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
            }
            .padding(.bottom, 4)
            
            // Email Input Field
            AurioInputField(
                iconName: "envelope.fill",
                placeholder: "Email address",
                text: $loginEmail,
                keyboardType: .emailAddress
            )
            
            // Password Input Field
            AurioPasswordField(
                iconName: "lock.fill",
                placeholder: "Password",
                text: $loginPassword,
                isVisible: $isLoginPasswordVisible
            )
            
            // Remember Me & Forgot Password Row
            HStack {
                Button(action: { rememberMe.toggle() }) {
                    HStack(spacing: 8) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 5)
                                .fill(rememberMe ? Color(red: 0.20, green: 0.48, blue: 0.96) : Color(red: 0.88, green: 0.92, blue: 0.97))
                                .frame(width: 18, height: 18)
                            
                            if rememberMe {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.white)
                            }
                        }
                        
                        Text("Remember me")
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(Color(red: 0.20, green: 0.25, blue: 0.35))
                    }
                }
                .buttonStyle(PlainButtonStyle())
                
                Spacer()
                
                Button(action: {
                    // Pre-fill email and switch to OTP for demo/recovery
                    authMode = .otpVerification
                }) {
                    Text("Forgot Password?")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Color(red: 0.20, green: 0.48, blue: 0.96))
                }
            }
            .padding(.vertical, 2)
            
            if let err = errorMessage {
                Text(err)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.red)
            }
            
            // Log In Action Button
            AurioPrimaryButton(title: "Log In", showArrow: true) {
                performLogin()
            }
            .padding(.top, 4)
            
            // "OR CONTINUE WITH" Divider
            orDividerView
            
            // Google Social Login
            GoogleSocialButton {
                performGoogleAuth()
            }
            
            // Sign Up Switch Footer
            HStack {
                Spacer()
                Text("Don't have an account? ")
                    .font(.system(size: 13, weight: .regular))
                    .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
                
                Button(action: {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                        errorMessage = nil
                        authMode = .signUp
                    }
                }) {
                    Text("Sign Up")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(red: 0.20, green: 0.48, blue: 0.96))
                }
                Spacer()
            }
            .padding(.top, 4)
        }
    }
    
    // MARK: - Sign Up Form View
    private var signUpFormView: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            VStack(alignment: .leading, spacing: 3) {
                Text("Create Account")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(Color(red: 0.06, green: 0.09, blue: 0.16))
                
                Text("Start your high-fidelity music journey")
                    .font(.system(size: 12.5, weight: .regular))
                    .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
            }
            .padding(.bottom, 2)
            
            // Email Input Field
            AurioInputField(
                iconName: "envelope.fill",
                placeholder: "Email address",
                text: $signUpEmail,
                keyboardType: .emailAddress
            )
            
            // Password Field
            AurioPasswordField(
                iconName: "lock.fill",
                placeholder: "Password",
                text: $signUpPassword,
                isVisible: $isSignUpPasswordVisible
            )
            
            // Confirm Password Field
            AurioPasswordField(
                iconName: "lock.fill",
                placeholder: "Confirm Password",
                text: $signUpConfirmPassword,
                isVisible: $isSignUpConfirmPasswordVisible
            )
            
            if let err = errorMessage {
                Text(err)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.red)
            }
            
            // Verify / Continue Button
            AurioPrimaryButton(title: "Verify", showArrow: true) {
                performSignUpValidation()
            }
            .padding(.top, 2)
            
            // "OR CONTINUE WITH" Divider
            orDividerView
            
            // Google Social Sign Up
            GoogleSocialButton {
                performGoogleAuth()
            }
            
            // Back to Log In Footer
            HStack {
                Spacer()
                Text("Already have an account? ")
                    .font(.system(size: 13, weight: .regular))
                    .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
                
                Button(action: {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                        errorMessage = nil
                        authMode = .login
                    }
                }) {
                    Text("Log In")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(red: 0.20, green: 0.48, blue: 0.96))
                }
                Spacer()
            }
            .padding(.top, 2)
        }
    }
    
    // MARK: - OTP Verification Form View
    private var otpVerificationFormView: some View {
        VStack(spacing: 16) {
            // Top Navigation Back + Title
            HStack {
                Button(action: {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                        errorMessage = nil
                        authMode = .signUp
                    }
                }) {
                    Image(systemName: "arrow.left")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(red: 0.10, green: 0.15, blue: 0.25))
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(Color(red: 0.90, green: 0.94, blue: 0.98)))
                }
                
                Text("Verification Code")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Color(red: 0.06, green: 0.09, blue: 0.16))
                    .padding(.leading, 8)
                
                Spacer()
            }
            
            // Subtitle
            Text("Enter the 4-digit code sent to\n\(signUpEmail.isEmpty ? "your registered email" : signUpEmail)")
                .font(.system(size: 13, weight: .regular))
                .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
                .multilineTextAlignment(.center)
                .lineSpacing(3)
            
            // Live OTP Inputs (4 Boxes)
            HStack(spacing: 12) {
                ForEach(0..<4, id: \.self) { idx in
                    OtpDigitBoxView(
                        digit: otpDigits[idx],
                        isFocused: focusedOtpIndex == idx,
                        onDigitEntered: { val in
                            handleOtpDigitInput(val, at: idx)
                        }
                    )
                }
            }
            .padding(.vertical, 8)
            
            if let err = otpErrorMessage {
                Text(err)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.red)
            }
            
            if isOtpSuccess {
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                    Text("Account Verified! Entering Aurio...")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.green)
                }
                .padding(.vertical, 4)
            } else {
                // Verify OTP Button
                AurioPrimaryButton(
                    title: isOtpVerifying ? "Verifying..." : "Verify & Proceed",
                    showArrow: !isOtpVerifying
                ) {
                    verifyOtpCode()
                }
                .disabled(otpDigits.contains(where: { $0.isEmpty }) || isOtpVerifying)
                
                // Resend Timer Row
                HStack(spacing: 4) {
                    Text("Didn't receive code? ")
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
                    
                    if resendTimer > 0 {
                        Text("Resend in \(resendTimer)s")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
                    } else {
                        Button(action: {
                            resendOtpCode()
                        }) {
                            Text("Resend Code")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(red: 0.20, green: 0.48, blue: 0.96))
                        }
                    }
                }
                .padding(.top, 4)
            }
        }
    }
    
    // MARK: - "OR CONTINUE WITH" Divider View
    private var orDividerView: some View {
        HStack(spacing: 12) {
            Rectangle()
                .fill(Color(red: 0.85, green: 0.90, blue: 0.96))
                .frame(height: 1)
            
            Text("OR CONTINUE WITH")
                .font(.system(size: 10, weight: .bold))
                .tracking(1.2)
                .foregroundColor(Color(red: 0.55, green: 0.65, blue: 0.76))
            
            Rectangle()
                .fill(Color(red: 0.85, green: 0.90, blue: 0.96))
                .frame(height: 1)
        }
        .padding(.vertical, 4)
    }
    
    // MARK: - Logic Handlers
    private func performLogin() {
        let email = loginEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        if email.isEmpty {
            errorMessage = "Please enter your email address"
            return
        }
        errorMessage = nil
        withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
            vm.currentUserEmail = email
            vm.isAuthenticated = true
        }
    }
    
    private func performSignUpValidation() {
        let email = signUpEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        if email.isEmpty || signUpPassword.isEmpty || signUpConfirmPassword.isEmpty {
            errorMessage = "Please fill in all fields"
            return
        }
        if signUpPassword != signUpConfirmPassword {
            errorMessage = "Passwords do not match"
            return
        }
        errorMessage = nil
        startResendTimer()
        withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
            authMode = .otpVerification
        }
    }
    
    private func handleOtpDigitInput(_ value: String, at index: Int) {
        if value.count <= 1 {
            otpDigits[index] = value
            otpErrorMessage = nil
            if !value.isEmpty && index < 3 {
                focusedOtpIndex = index + 1
            }
        } else if value.count == 4 {
            // Pasted 4-digit code
            for (i, char) in value.prefix(4).enumerated() {
                otpDigits[i] = String(char)
            }
            focusedOtpIndex = nil
        }
    }
    
    private func verifyOtpCode() {
        isOtpVerifying = true
        otpErrorMessage = nil
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            isOtpVerifying = false
            isOtpSuccess = true
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                    vm.currentUserEmail = signUpEmail.isEmpty ? "user@aurio.app" : signUpEmail
                    vm.isAuthenticated = true
                }
            }
        }
    }
    
    private func resendOtpCode() {
        resendTimer = 45
        otpDigits = ["", "", "", ""]
        focusedOtpIndex = 0
        startResendTimer()
    }
    
    private func startResendTimer() {
        resendTimer = 45
        timerRunning = true
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if self.resendTimer > 0 {
                self.resendTimer -= 1
            } else {
                timer.invalidate()
                self.timerRunning = false
            }
        }
    }
    
    private func performGoogleAuth() {
        withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
            vm.currentUserEmail = "google.user@aurio.app"
            vm.isAuthenticated = true
        }
    }
}

// MARK: - Aurio Frosted Text Input Field
struct AurioInputField: View {
    let iconName: String
    let placeholder: String
    @Binding var text: String
    var keyboardType: UIKeyboardType = .default
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: iconName)
                .font(.system(size: 16))
                .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
                .frame(width: 22)
            
            TextField(placeholder, text: $text)
                .font(.system(size: 14.5, weight: .medium))
                .foregroundColor(Color(red: 0.10, green: 0.15, blue: 0.25))
                .keyboardType(keyboardType)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(red: 0.93, green: 0.95, blue: 0.98))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.8), lineWidth: 1)
        )
    }
}

// MARK: - Aurio Frosted Password Input Field
struct AurioPasswordField: View {
    let iconName: String
    let placeholder: String
    @Binding var text: String
    @Binding var isVisible: Bool
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: iconName)
                .font(.system(size: 16))
                .foregroundColor(Color(red: 0.45, green: 0.55, blue: 0.68))
                .frame(width: 22)
            
            if isVisible {
                TextField(placeholder, text: $text)
                    .font(.system(size: 14.5, weight: .medium))
                    .foregroundColor(Color(red: 0.10, green: 0.15, blue: 0.25))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
            } else {
                SecureField(placeholder, text: $text)
                    .font(.system(size: 14.5, weight: .medium))
                    .foregroundColor(Color(red: 0.10, green: 0.15, blue: 0.25))
            }
            
            Button(action: { isVisible.toggle() }) {
                Image(systemName: isVisible ? "eye.slash.fill" : "eye.fill")
                    .font(.system(size: 15))
                    .foregroundColor(Color(red: 0.50, green: 0.60, blue: 0.72))
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(red: 0.93, green: 0.95, blue: 0.98))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.8), lineWidth: 1)
        )
    }
}

// MARK: - Aurio Primary Gradient Button
struct AurioPrimaryButton: View {
    let title: String
    var showArrow: Bool = false
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Text(title)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                
                if showArrow {
                    Image(systemName: "arrow.right")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(
                LinearGradient(
                    colors: [
                        Color(red: 0.28, green: 0.55, blue: 0.98),
                        Color(red: 0.18, green: 0.42, blue: 0.92)
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .clipShape(Capsule())
            .shadow(color: Color(red: 0.20, green: 0.45, blue: 0.95).opacity(0.40), radius: 14, x: 0, y: 7)
        }
    }
}

// MARK: - Google Social Button
struct GoogleSocialButton: View {
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(Color.white)
                    .frame(width: 52, height: 52)
                    .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 3)
                    .overlay(Circle().stroke(Color(red: 0.90, green: 0.93, blue: 0.97), lineWidth: 1))
                
                // Pure SwiftUI Crisp Google 'G' Vector Icon
                GoogleIconView()
                    .frame(width: 24, height: 24)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 2)
    }
}

// MARK: - Google Multi-color Icon Vector
struct GoogleIconView: View {
    var body: some View {
        ZStack {
            // Blue Bar
            Text("G")
                .font(.system(size: 22, weight: .black, design: .rounded))
                .foregroundStyle(
                    LinearGradient(
                        colors: [
                            Color(red: 0.92, green: 0.26, blue: 0.21), // Red
                            Color(red: 0.98, green: 0.73, blue: 0.02), // Yellow
                            Color(red: 0.20, green: 0.66, blue: 0.33), // Green
                            Color(red: 0.26, green: 0.52, blue: 0.96)  // Blue
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        }
    }
}

// MARK: - Single OTP Digit Box
struct OtpDigitBoxView: View {
    let digit: String
    let isFocused: Bool
    let onDigitEntered: (String) -> Void
    
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(red: 0.93, green: 0.95, blue: 0.98))
                .frame(width: 56, height: 60)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(
                            isFocused ? Color(red: 0.20, green: 0.48, blue: 0.96) : Color(red: 0.85, green: 0.90, blue: 0.96),
                            lineWidth: isFocused ? 2 : 1
                        )
                )
                .shadow(color: isFocused ? Color.blue.opacity(0.25) : Color.clear, radius: 8)
            
            TextField("", text: Binding(
                get: { digit },
                set: { newVal in
                    if let last = newVal.last {
                        onDigitEntered(String(last))
                    } else {
                        onDigitEntered("")
                    }
                }
            ))
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .font(.system(size: 24, weight: .bold))
            .foregroundColor(Color(red: 0.08, green: 0.12, blue: 0.22))
            .frame(width: 56, height: 60)
        }
    }
}
