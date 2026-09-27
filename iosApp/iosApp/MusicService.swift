import Foundation

// MARK: - JioSaavn Real-time Music Service & Live Stream Resolver
class JioSaavnMusicService {
    static let shared = JioSaavnMusicService()
    
    // Live Search across millions of songs via Saavn API
    func searchSongs(query: String) async -> [IosSong] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return []
        }
        
        // Primary API: Saavn.me direct JSON API (provides direct 320kbps MP4/AAC stream URLs)
        if let results = await fetchFromSaavnMe(query: encoded), !results.isEmpty {
            return results
        }
        
        // Fallback API: JioSaavn Search API
        return await fetchFromJioSaavnDirect(query: encoded)
    }
    
    private func fetchFromSaavnMe(query: String) async -> [IosSong]? {
        let urlString = "https://saavn.me/api/search/songs?query=\(query)&page=1&limit=30"
        guard let url = URL(string: urlString) else { return nil }
        
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 8
        
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let dataObj = json["data"] as? [String: Any],
                  let results = dataObj["results"] as? [[String: Any]] else { return nil }
            
            var list: [IosSong] = []
            for item in results {
                let id = String(describing: item["id"] ?? UUID().uuidString)
                let name = cleanHtml((item["name"] as? String) ?? "")
                guard !name.isEmpty else { continue }
                
                var artistName = "Aurio Artist"
                if let artists = item["artists"] as? [String: Any],
                   let primary = artists["primary"] as? [[String: Any]],
                   let first = primary.first,
                   let pName = first["name"] as? String {
                    artistName = cleanHtml(pName)
                }
                
                var albumName = ""
                if let album = item["album"] as? [String: Any], let aName = album["name"] as? String {
                    albumName = cleanHtml(aName)
                }
                
                let durationSec = Int(String(describing: item["duration"] ?? "210")) ?? 210
                let durationText = String(format: "%d:%02d", durationSec / 60, durationSec % 60)
                
                var image = ""
                if let imageArr = item["image"] as? [[String: Any]], let last = imageArr.last, let link = last["url"] as? String {
                    image = link.replacingOccurrences(of: "http://", with: "https://")
                }
                
                var streamUrl = ""
                if let downloadArr = item["downloadUrl"] as? [[String: Any]], let last = downloadArr.last, let url = last["url"] as? String {
                    streamUrl = url.replacingOccurrences(of: "http://", with: "https://")
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
            return list
        } catch {
            return nil
        }
    }
    
    private func fetchFromJioSaavnDirect(query: String) async -> [IosSong] {
        let urlString = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&p=1&n=30&q=\(query)&_marker=0&ctx=android"
        guard let url = URL(string: urlString) else { return [] }
        
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
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
                
                let streamUrl = (item["media_preview_url"] as? String)?.replacingOccurrences(of: "preview.saavncdn.com", with: "aac.saavncdn.com")
                    .replacingOccurrences(of: "_96_p.mp4", with: "_320.mp4")
                    .replacingOccurrences(of: "http://", with: "https://") ?? ""
                
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
