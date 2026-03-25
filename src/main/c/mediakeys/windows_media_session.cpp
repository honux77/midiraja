#include <windows.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Media.Playback.h>
#include <winrt/Windows.Foundation.h>
#include <systemmediatransportcontrolsinterop.h>

using namespace winrt::Windows::Media;

static void (*g_callback)(int) = nullptr;
static SystemMediaTransportControls g_smtc{nullptr};
static bool g_registered = false;

extern "C" {

void windows_smtc_start(void (*callback)(int command))
{
    if (g_registered) return;
    g_callback = callback;

    auto interop = winrt::get_activation_factory<SystemMediaTransportControls,
        ISystemMediaTransportControlsInterop>();
    HWND hwnd = GetConsoleWindow();
    if (!hwnd) hwnd = GetDesktopWindow();
    winrt::check_hresult(interop->GetForWindow(hwnd,
        winrt::guid_of<SystemMediaTransportControls>(),
        winrt::put_abi(g_smtc)));

    g_smtc.IsPlayEnabled(true);
    g_smtc.IsPauseEnabled(true);
    g_smtc.IsNextEnabled(true);
    g_smtc.IsPreviousEnabled(true);

    g_smtc.ButtonPressed([](auto&&, SystemMediaTransportControlsButtonPressedEventArgs const& args) {
        if (!g_callback) return;
        switch (args.Button()) {
            case SystemMediaTransportControlsButton::Play:
            case SystemMediaTransportControlsButton::Pause:  g_callback(0); break;
            case SystemMediaTransportControlsButton::Next:   g_callback(1); break;
            case SystemMediaTransportControlsButton::Previous: g_callback(2); break;
            case SystemMediaTransportControlsButton::FastForward: g_callback(3); break;
            case SystemMediaTransportControlsButton::Rewind:      g_callback(4); break;
            default: break;
        }
    });

    g_smtc.PlaybackStatus(MediaPlaybackStatus::Playing);
    g_registered = true;
}

void windows_smtc_update(const wchar_t *title, const wchar_t *artist,
                          int64_t duration_100ns, int64_t position_100ns,
                          int is_playing)
{
    if (!g_registered || !g_smtc) return;

    auto updater = g_smtc.DisplayUpdater();
    updater.Type(MediaPlaybackType::Music);
    if (title)  updater.MusicProperties().Title(title);
    if (artist && wcslen(artist) > 0) updater.MusicProperties().Artist(artist);
    updater.Update();

    g_smtc.PlaybackStatus(is_playing
        ? MediaPlaybackStatus::Playing
        : MediaPlaybackStatus::Paused);

    SystemMediaTransportControlsTimelineProperties timeline;
    timeline.StartTime(winrt::Windows::Foundation::TimeSpan{0});
    timeline.EndTime(winrt::Windows::Foundation::TimeSpan{duration_100ns});
    timeline.Position(winrt::Windows::Foundation::TimeSpan{position_100ns});
    g_smtc.UpdateTimelineProperties(timeline);
}

void windows_smtc_stop(void)
{
    if (!g_registered) return;
    g_registered = false;
    g_callback = nullptr;
    if (g_smtc) {
        g_smtc.PlaybackStatus(MediaPlaybackStatus::Stopped);
        g_smtc = nullptr;
    }
}

} // extern "C"
