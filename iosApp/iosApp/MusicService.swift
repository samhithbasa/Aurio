import Foundation
import CommonCrypto

// MARK: - JioSaavn Real-time Music Service & Live Stream Resolver
class JioSaavnMusicService {
    static let shared = JioSaavnMusicService()
    
    // Live Search across millions of songs via Saavn API
    func searchSongs(query: String) async -> [IosSong] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return []
        }
        
        // Primary API: Saavn.dev / Saavn.me JSON API (direct 320kbps stream URLs)
        if let results = await fetchFromSaavnDev(query: encoded), !results.isEmpty {
            return results
        }
        
        // Secondary API: Direct JioSaavn API with DES decryption
        return await fetchFromJioSaavnDirect(query: encoded)
    }
    
    private func fetchFromSaavnDev(query: String) async -> [IosSong]? {
        let endpoints = [
            "https://saavn.dev/api/search/songs?query=\(query)&page=1&limit=30",
            "https://saavn.me/api/search/songs?query=\(query)&page=1&limit=30"
        ]
        
        for urlString in endpoints {
            guard let url = URL(string: urlString) else { continue }
            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
            request.timeoutInterval = 8
            
            do {
                let (data, _) = try await URLSession.shared.data(for: request)
                guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let dataObj = json["data"] as? [String: Any],
                      let results = dataObj["results"] as? [[String: Any]] else { continue }
                
                var list: [IosSong] = []
                for item in results {
                    let id = String(describing: item["id"] ?? UUID().uuidString)
                    let name = cleanHtml((item["name"] as? String) ?? (item["title"] as? String) ?? "")
                    guard !name.isEmpty else { continue }
                    
                    var artistName = "Aurio Artist"
                    if let artists = item["artists"] as? [String: Any],
                       let primary = artists["primary"] as? [[String: Any]],
                       let first = primary.first,
                       let pName = first["name"] as? String {
                        artistName = cleanHtml(pName)
                    } else if let primaryArtists = item["primaryArtists"] as? String, !primaryArtists.isEmpty {
                        artistName = cleanHtml(primaryArtists)
                    }
                    
                    var albumName = ""
                    if let album = item["album"] as? [String: Any], let aName = album["name"] as? String {
                        albumName = cleanHtml(aName)
                    }
                    
                    let durationSec = Int(String(describing: item["duration"] ?? "210")) ?? 210
                    let durationText = String(format: "%d:%02d", durationSec / 60, durationSec % 60)
                    
                    var image = ""
                    if let imageArr = item["image"] as? [[String: Any]], let last = imageArr.last {
                        let link = (last["url"] as? String) ?? (last["link"] as? String) ?? ""
                        image = link.replacingOccurrences(of: "http://", with: "https://")
                    }
                    
                    var streamUrl = ""
                    if let downloadArr = item["downloadUrl"] as? [[String: Any]] {
                        // Pick 320kbps or highest quality available
                        if let best = downloadArr.last {
                            let urlLink = (best["url"] as? String) ?? (best["link"] as? String) ?? ""
                            streamUrl = urlLink.replacingOccurrences(of: "http://", with: "https://")
                        }
                    }
                    
                    // Fallback to encrypted_media_url if present
                    if streamUrl.isEmpty, let enc = item["encrypted_media_url"] as? String {
                        streamUrl = decryptSaavnMediaUrl(enc) ?? ""
                    }
                    
                    list.append(
                        IosSong(
                            id: id,
                            title: name,
                            artist: artistName,
                            album: albumName,
                            durationSeconds: durationSec,
                            durationText: durationText,
                            thumbnailUrl: image,
                            streamUrl: streamUrl,
                            isSpatial: true
                        )
                    )
                }
                if !list.isEmpty { return list }
            } catch {
                continue
            }
        }
        return nil
    }
    
    private func fetchFromJioSaavnDirect(query: String) async -> [IosSong] {
        let urlString = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&p=1&n=30&q=\(query)&_marker=0&ctx=android"
        guard let url = URL(string: urlString) else { return [] }
        
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 8
        
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
                let id = String(describing: item["id"] ?? UUID().uuidString)
                let titleRaw = (item["song"] as? String) ?? (item["title"] as? String) ?? ""
                let title = cleanHtml(titleRaw)
                guard !title.isEmpty else { continue }
                
                let artistRaw = (item["singers"] as? String) ?? (item["primary_artists"] as? String) ?? "Aurio Artist"
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
                // 1. Decrypt DES encrypted media url
                if let enc = item["encrypted_media_url"] as? String {
                    streamUrl = decryptSaavnMediaUrl(enc) ?? ""
                }
                
                // 2. Direct media_preview_url fallback
                if streamUrl.isEmpty, let preview = item["media_preview_url"] as? String, !preview.isEmpty {
                    streamUrl = preview.replacingOccurrences(of: "http://", with: "https://")
                }
                
                songs.append(
                    IosSong(
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
                )
            }
            return songs
        } catch {
            return []
        }
    }
    
    // Live Trending Charts
    func fetchTrendingCharts() async -> [IosSong] {
        let queries = ["Trending Now", "Today's Top Hits", "Viral 50", "Bollywood Top 20"]
        for q in queries {
            let res = await searchSongs(query: q)
            if !res.isEmpty { return res }
        }
        return []
    }
    
    // Decrypts JioSaavn's DES-encrypted media URL and upgrades it to 320kbps MP4/AAC
    func decryptSaavnMediaUrl(_ encrypted: String) -> String? {
        guard !encrypted.isEmpty, let data = Data(base64Encoded: encrypted) else { return nil }
        let key = "38346591"
        guard let keyData = key.data(using: .utf8) else { return nil }
        
        var numBytesDecrypted: size_t = 0
        var decryptedData = Data(count: data.count + kCCBlockSizeDES)
        
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
                        decryptedData.count,
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
