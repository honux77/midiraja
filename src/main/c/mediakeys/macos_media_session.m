#import <Foundation/Foundation.h>
#import <MediaPlayer/MediaPlayer.h>

static void (*g_callback)(int) = NULL;
static NSThread *g_thread = NULL;
static int g_guard = 0;  // 1 = registered

@interface MediaSessionRunner : NSObject
- (void)runLoop:(id)arg;
@end

@implementation MediaSessionRunner
- (void)runLoop:(id)arg {
    @autoreleasepool {
        // Spin the run loop so MPRemoteCommandCenter can deliver events
        [[NSRunLoop currentRunLoop] runUntilDate:[NSDate distantFuture]];
    }
}
@end

void macos_register_commands(void (*callback)(int command))
{
    if (g_guard) return;
    g_guard = 1;
    g_callback = callback;

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
    // Do NOT register nextTrackCommand/previousTrackCommand: when those are present,
    // macOS shows ⏮/⏭ (track-change) buttons and hides the skip buttons.
    // With only skipForward/skipBackward registered, macOS shows the skip buttons (⏩/⏪)
    // in Control Center, and the keyboard ⏭/⏮ keys are routed to skip as well.
    // preferredIntervals must be set or macOS renders these buttons as disabled.
    cc.skipForwardCommand.preferredIntervals = @[@10];
    cc.skipBackwardCommand.preferredIntervals = @[@10];
    [cc.skipForwardCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (g_callback) g_callback(3); return MPRemoteCommandHandlerStatusSuccess;
    }];
    [cc.skipBackwardCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *e) {
        if (g_callback) g_callback(4); return MPRemoteCommandHandlerStatusSuccess;
    }];

    MediaSessionRunner *runner = [[MediaSessionRunner alloc] init];
    g_thread = [[NSThread alloc] initWithTarget:runner selector:@selector(runLoop:) object:nil];
    [g_thread start];
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
    [cc.skipForwardCommand removeTarget:nil];
    [cc.skipBackwardCommand removeTarget:nil];
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;
    [g_thread cancel];
    g_thread = nil;
}
