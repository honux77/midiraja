#import <Foundation/Foundation.h>
#import <MediaPlayer/MediaPlayer.h>

static void (*g_callback)(int) = NULL;
static NSThread *g_thread = NULL;
static int g_guard = 0;  // 1 = registered
static dispatch_semaphore_t g_registered_sem = NULL;

// MediaSessionRunner starts a dedicated ObjC RunLoop thread.
// All MPRemoteCommandCenter work happens on this thread — Java FFM threads do not
// have an NSAutoreleasePool or ObjC RunLoop which macOS requires for media commands.
@interface MediaSessionRunner : NSObject
- (void)runLoop:(id)arg;
@end

@implementation MediaSessionRunner
- (void)runLoop:(id)arg {
    @autoreleasepool {
        MPRemoteCommandCenter *cc = [MPRemoteCommandCenter sharedCommandCenter];

        [cc.playCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
            if (g_callback) g_callback(0); return MPRemoteCommandHandlerStatusSuccess;
        }];
        [cc.pauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
            if (g_callback) g_callback(0); return MPRemoteCommandHandlerStatusSuccess;
        }];
        [cc.togglePlayPauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
            if (g_callback) g_callback(0); return MPRemoteCommandHandlerStatusSuccess;
        }];
        // Map ⏭/⏮ to seek ±10s.
        [cc.nextTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
            if (g_callback) g_callback(3); return MPRemoteCommandHandlerStatusSuccess;
        }];
        [cc.previousTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
            if (g_callback) g_callback(4); return MPRemoteCommandHandlerStatusSuccess;
        }];

        // Signal that registration is complete before entering the run loop.
        dispatch_semaphore_signal(g_registered_sem);

        // Spin the run loop so MPRemoteCommandCenter keeps delivering events.
        [[NSRunLoop currentRunLoop] runUntilDate:[NSDate distantFuture]];
    }
}
@end

void macos_register_commands(void (*callback)(int command))
{
    if (g_guard) return;
    g_guard = 1;
    g_callback = callback;

    g_registered_sem = dispatch_semaphore_create(0);

    MediaSessionRunner *runner = [[MediaSessionRunner alloc] init];
    g_thread = [[NSThread alloc] initWithTarget:runner
                                       selector:@selector(runLoop:)
                                         object:nil];
    [g_thread start];

    // Wait (up to 2 s) for the RunLoop thread to finish registering commands
    // before returning — ensures handlers are active when the caller proceeds.
    dispatch_semaphore_wait(g_registered_sem,
                            dispatch_time(DISPATCH_TIME_NOW, 2 * NSEC_PER_SEC));
}

void macos_update_now_playing(const char *title, const char *artist,
                               double duration_sec, double position_sec,
                               int is_playing)
{
    if (!g_guard) return;
    NSMutableDictionary *info = [NSMutableDictionary dictionary];
    if (title && strlen(title) > 0)
        info[MPMediaItemPropertyTitle] = @(title);
    if (artist && strlen(artist) > 0)
        info[MPMediaItemPropertyArtist] = @(artist);
    info[MPMediaItemPropertyPlaybackDuration] = @(duration_sec);
    info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(position_sec);
    info[MPNowPlayingInfoPropertyPlaybackRate] = @(is_playing ? 1.0 : 0.0);
    info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = @(1.0);
    // A queue with items before and after the current track activates ⏮/⏭ buttons.
    info[MPNowPlayingInfoPropertyPlaybackQueueCount] = @(9999);
    info[MPNowPlayingInfoPropertyPlaybackQueueIndex] = @(1);
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = info;
}

void macos_unregister(void)
{
    if (!g_guard) return;
    g_guard = 0;
    g_callback = NULL;
    MPRemoteCommandCenter *cc = [MPRemoteCommandCenter sharedCommandCenter];
    [cc.playCommand removeTarget:nil];
    [cc.pauseCommand removeTarget:nil];
    [cc.togglePlayPauseCommand removeTarget:nil];
    [cc.nextTrackCommand removeTarget:nil];
    [cc.previousTrackCommand removeTarget:nil];
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;
    [g_thread cancel];
    g_thread = nil;
}
