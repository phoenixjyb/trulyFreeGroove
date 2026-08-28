import BackgroundTasks
import Foundation

enum PodcastBackgroundRefresh {
    static let identifier = "com.trulyfreemusic.opengroove.podcast-refresh"

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 12 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}
