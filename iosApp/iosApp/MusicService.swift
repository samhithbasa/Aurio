import Foundation
import CommonCrypto

// MARK: - JioSaavn Official Real-time Music Service & Live 320kbps Stream Resolver
class JioSaavnMusicService {
    static let shared = JioSaavnMusicService()
    private let saavnBase = "https://www.jiosaavn.com/api.php"
    private let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
    
    // Live Search across millions of songs via official Saavn API
    func searchSongs(query: String) async -> [IosSong] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return []
        }
        
        // 1. Primary: Direct JioSaavn Search API
        let results = await fetchFromJioSaavnSearch(query: encoded)
        if !results.isEmpty {
            return results
        }
        
        // 2. Fallback: JioSaavn Autocomplete + Batch Details API
        return await fetchFromJioSaavnAutocomplete(query: encoded)
    }
    
    // Direct JioSaavn search.getResults API
    private func fetchFromJioSaavnSearch(query: String) async -> [IosSong] {
        let urlString = "\(saavnBase)?__call=search.getResults&_format=json&p=1&n=30&q=\(query)&_marker=0&ctx=android"
        guard let url = URL(string: urlString) else { return [] }
        
        var request = URLRequest(url: url)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 10
        
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }
            
            var resultsArray: [[String: Any]] = []
            if let directResults = json["results"] as? [[String: Any]] {
                resultsArray = directResults
            } else if let dataObj = json["data"] as? [String: Any], let nested = dataObj["results"] as? [[String: Any]] {
                resultsArray = nested
            }
            
            var songs: [IosSong] = []
            for item in resultsArray {
                if let song = parseSongItem(item) {
                    songs.append(song)
                }
            }
            return songs
        } catch {
            return []
        }
    }
    
    // JioSaavn autocomplete.get API with song.getDetails resolution
    private func fetchFromJioSaavnAutocomplete(query: String) async -> [IosSong] {
        let urlString = "\(saavnBase)?__call=autocomplete.get&_format=json&query=\(query)&ctx=android"
        guard let url = URL(string: urlString) else { return [] }
        
        var request = URLRequest(url: url)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 10
        
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let songsObj = json["songs"] as? [String: Any],
                  let songData = songsObj["data"] as? [[String: Any]],
                  !songData.isEmpty else { return [] }
            
            var pids: [String] = []
            for item in songData {
                if let id = item["id"] as? String, !id.isEmpty {
                    pids.append(id)
                }
            }
            guard !pids.isEmpty else { return [] }
            
            let pidsStr = pids.joined(separator: ",")
            return await fetchBatchSongDetails(pids: pidsStr)
        } catch {
            return []
        }
    }
    
    // Batch fetch song details for high quality 320kbps streams
    func fetchBatchSongDetails(pids: String) async -> [IosSong] {
        guard let encodedPids = pids.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return [] }
        let urlString = "\(saavnBase)?__call=song.getDetails&_format=json&pids=\(encodedPids)&ctx=android"
        guard let url = URL(string: urlString) else { return [] }
        
        var request = URLRequest(url: url)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 10
        
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }
            
            var songs: [IosSong] = []
            for (_, value) in json {
                if let item = value as? [String: Any], let song = parseSongItem(item) {
                    songs.append(song)
                }
            }
            return songs
        } catch {
            return []
        }
    }
    
    // Direct 320kbps Stream URL Resolver for single song
    func fetchStreamUrl(songId: String) async -> String? {
        let details = await fetchBatchSongDetails(pids: songId)
        if let first = details.first, !first.streamUrl.isEmpty {
            return first.streamUrl
        }
        return nil
    }
    
    // Live Trending Charts
    func fetchTrendingCharts() async -> [IosSong] {
        let queries = ["Top Global Hits", "Arijit Singh", "Bollywood Top Hits", "The Weeknd", "Taylor Swift"]
        var allSongs: [IosSong] = []
        for q in queries {
            let res = await searchSongs(query: q)
            for song in res {
                if !allSongs.contains(where: { $0.id == song.id }) {
                    allSongs.append(song)
                }
            }
            if allSongs.count >= 20 { break }
        }
        return allSongs
    }
    
    // Helper to parse JioSaavn Song dictionary
    private func parseSongItem(_ item: [String: Any]) -> IosSong? {
        let id = String(describing: item["id"] ?? UUID().uuidString)
        let titleRaw = (item["song"] as? String) ?? (item["title"] as? String) ?? ""
        let title = cleanHtml(titleRaw)
        guard !title.isEmpty else { return nil }
        
        let artistRaw = (item["singers"] as? String) ?? (item["primary_artists"] as? String) ?? (item["music"] as? String) ?? "Aurio Artist"
        let artist = cleanHtml(artistRaw)
        let album = cleanHtml((item["album"] as? String) ?? "")
        
        let durationSec = Int(String(describing: item["duration"] ?? "210")) ?? 210
        let durationText = String(format: "%d:%02d", durationSec / 60, durationSec % 60)
        
        var image = (item["image"] as? String) ?? ""
        if !image.isEmpty {
            image = image.replacingOccurrences(of: "150x150", with: "500x500")
                .replacingOccurrences(of: "50x50", with: "500x500")
                .replacingOccurrences(of: "http://", with: "https://")
        }
        
        var streamUrl = ""
        // 1. Decrypt DES encrypted media url (JioSaavn Official Standard)
        if let enc = item["encrypted_media_url"] as? String, !enc.isEmpty {
            streamUrl = decryptSaavnMediaUrl(enc) ?? ""
        }
        
        // 2. Direct media_preview_url fallback
        if streamUrl.isEmpty, let preview = item["media_preview_url"] as? String, !preview.isEmpty {
            streamUrl = preview.replacingOccurrences(of: "http://", with: "https://")
                .replacingOccurrences(of: "_96_p.mp4", with: "_320.mp4")
        }
        
        return IosSong(
            id: id,
            title: title,
            artist: artist,
            album: album,
            durationSeconds: durationSec,
            durationText: durationText,
            thumbnailUrl: image,
            streamUrl: streamUrl,
            isSpatial: true
        )
    }
    
    // Decrypts JioSaavn's DES-encrypted media URL and upgrades it to 320kbps MP4/AAC
    func decryptSaavnMediaUrl(_ encrypted: String) -> String? {
        let cleanEnc = encrypted.replacingOccurrences(of: " ", with: "+").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanEnc.isEmpty, let data = Data(base64Encoded: cleanEnc) else { return nil }
        let key = "38346591"
        guard let keyData = key.data(using: .utf8) else { return nil }
        
        var numBytesDecrypted: size_t = 0
        let bufferCapacity = data.count + kCCBlockSizeDES
        var decryptedData = Data(count: bufferCapacity)
        
        let cryptStatus = decryptedData.withUnsafeMutableBytes { decryptedBytes in
            data.withUnsafeBytes { dataBytes in
                keyData.withUnsafeBytes { keyBytes in
                    CCCrypt(
                        CCOperation(kCCDecrypt),
                        CCAlgorithm(kCCAlgorithmDES),
                        CCOptions(kCCOptionPKCS7Padding | kCCOptionECBMode),
                        keyBytes.baseAddress,
                        kCCKeySizeDES,
                        nil,
                        dataBytes.baseAddress,
                        data.count,
                        decryptedBytes.baseAddress,
                        decryptedBytes.count,
                        &numBytesDecrypted
                    )
                }
            }
        }
        
        if cryptStatus == kCCSuccess {
            decryptedData.count = numBytesDecrypted
            if var rawUrl = String(data: decryptedData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) {
                rawUrl = rawUrl.replacingOccurrences(of: "_96.mp4", with: "_320.mp4")
                    .replacingOccurrences(of: "_160.mp4", with: "_320.mp4")
                    .replacingOccurrences(of: "http://", with: "https://")
                return rawUrl
            }
        }
        return nil
    }
    
    private func cleanHtml(_ text: String) -> String {
        return text
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&#039;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&nbsp;", with: " ")
    }
}
