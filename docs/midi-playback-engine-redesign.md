# MidiPlaybackEngine 테스트 보강 계획

**Date:** 2026-03-23
**Scope:** `src/test/java/com/fupfin/midiraja/engine/PlaybackEngineTest.java`

---

## 접근 방향

`MidiPlaybackEngine`의 private 메서드(`getTickForTime`, `processChaseEvent` 등)가
테스트되지 않는 근본 이유는 그것들을 **호출하는 `playLoop`가 테스트되기 어려워서**가 아니다.
실제로 `PlaybackEngineTest`는 이미 `start()` → `playLoop`을 통해 chase 로직과 이벤트 라우팅을
**간접적으로** 검증하고 있다.

빠진 것은 구조 분해가 아니라 **특정 시나리오를 커버하는 테스트**다.

기존 인프라 (`FakeClock`, `RecordingMidiProvider`, `MockTerminalIO`, `ScopedValue`)로
모든 미테스트 경로를 간접 검증할 수 있다. 클래스 분해는 하지 않는다.

---

## 기존 테스트 인프라

| 도구 | 역할 |
|------|------|
| `FakeClock` | `sleepMillis`/`onSpinWait`이 실제 대기 없이 가상 시간 진행 |
| `RecordingMidiProvider` | `sendMessage` 호출을 리스트에 기록 |
| `MockTerminalIO` | `injectKey`로 입력 루프에 키 이벤트 주입 |
| `ScopedValue.where(TerminalIO.CONTEXT, io).call(() -> engine.start(ui))` | 풀 플레이백 루프 실행 |
| `startTimeMicroseconds` 파라미터 | 특정 위치에서 seek 시작 → chase 로직 경유 |

---

## 미테스트 시나리오 목록

### 1. BPM 변경 이벤트 가로지르는 `getTickForTime` 정확도

**현재 상태**: 모든 seek 테스트가 120 BPM 단일 템포로만 검증.
**커버 방법**: 시퀀스에 템포 변경 메타 이벤트(`0xFF 0x51`) 삽입 →
`startTimeMicroseconds`로 템포 변경 지점 이후에서 시작 → chase 중 재생된 이벤트로 정확도 검증.

```
Track: [PC ch0 prog=3 @ tick 0] [Tempo=60BPM @ tick 48] [CC ch0 vol=80 @ tick 96] [padding @ tick 480]
startTimeMicroseconds = 2_500_000L  (tempo 변경 이후 위치)
기대: PC와 CC 모두 chase로 재생 (두 템포 구간을 모두 통과)
```

### 2. 일시정지 중 seek, 그 후 재개

**현재 상태**: pause + seek 조합 미테스트.
**커버 방법**: `setInitiallyPaused()` → `start()` → 별도 스레드에서 `seekRelative()` →
`togglePause()` → 종료. 재개 후 올바른 위치에서 재생됨을 `RecordingMidiProvider`로 확인.

### 3. 연속 seek (atomicity of `seekTarget`)

**현재 상태**: 단일 seek만 테스트.
**커버 방법**: `FakeClock`으로 빠른 플레이백 + 짧은 간격으로 `seekRelative` 여러 번 호출.
예외 없이 종료, 마지막 seek 방향이 반영됨을 확인.

### 4. `playLoop` 내 BPM 변경 → `getCurrentBpm` 갱신

**현재 상태**: `getCurrentBpm()`이 플레이백 중 업데이트되는지 미테스트.
**커버 방법**: 템포 메타 이벤트가 있는 시퀀스 재생 → `PlaybackEventListener` 리스너 등록 →
`onTempoChanged` 콜백에서 bpm 값 캡처 → 기대 BPM과 비교.

### 5. `addPlaybackEventListener` 알림

**현재 상태**: 리스너 등록 및 콜백 미테스트.
**커버 방법**: 리스너 등록 → `start()` 실행 → `onTick`, `onTempoChanged` 등이
실제로 호출됐는지 확인.

### 6. `decayChannelLevels` 동작

**현재 상태**: 미테스트. `getChannelLevels()`와 연동 확인 필요.
**커버 방법**: NoteOn 이벤트 포함 시퀀스 재생 → `start()` 후 `getChannelLevels()` 확인 →
`decayChannelLevels(0.5)` 호출 → 레벨이 감소했는지 확인.

---

## 구현 계획

### Task 1: BPM 변경 가로지르는 seek 테스트

**파일**: `PlaybackEngineTest.java`에 추가

```java
@Test void getTickForTime_acrossTempoChange_seeksCorrectly() throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, 480);
    Track track = seq.createTrack();
    // tick 0: PC ch0 prog=3  (120 BPM 구간)
    track.add(new MidiEvent(new ShortMessage(0xC0, 0, 3, 0), 0L));
    // tick 480 = 1s @ 120BPM: 템포 변경 → 60 BPM (500,000 µs/beat → 1,000,000 µs/beat)
    byte[] tempo60 = {(byte)0xFF, 0x51, 0x03, 0x0F, 0x42, 0x40}; // 1,000,000 µs/qn
    MetaMessage tempoMsg = new MetaMessage(); tempoMsg.setMessage(0x51, new byte[]{0x0F, 0x42, 0x40}, 3);
    track.add(new MidiEvent(tempoMsg, 480L));
    // tick 960 = 1s + 1s @ 60BPM = 2s total: CC ch0 vol=80
    track.add(new MidiEvent(new ShortMessage(0xB0, 0, 7, 80), 960L));
    // padding
    MetaMessage pad = new MetaMessage(0x01, new byte[]{}, 0);
    track.add(new MidiEvent(pad, 1920L));

    RecordingMidiProvider recording = new RecordingMidiProvider();
    // seek to 2.5s (1s@120BPM + 1.5s@60BPM) → 두 구간 모두 통과
    PlaybackEngine engine = new MidiPlaybackEngine(
        seq, recording, ctx(), 100, 1000.0, Optional.of(2_500_000L), Optional.empty());

    ScopedValue.where(TerminalIO.CONTEXT, new MockTerminalIO()).call(() -> {
        engine.start(new DumbUI()); return null;
    });

    assertTrue(recording.messages.stream().anyMatch(m -> (m[0] & 0xF0) == 0xC0),
        "PC should be chased (before tempo change)");
    assertTrue(recording.messages.stream().anyMatch(m -> (m[0] & 0xF0) == 0xB0),
        "CC should be chased (after tempo change)");
}
```

### Task 2: 일시정지 중 seek, 재개 테스트

```java
@Test @Timeout(5) void seekWhilePaused_thenUnpause_resumesFromNewPosition() throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, 24);
    Track track = seq.createTrack();
    track.add(new MidiEvent(new ShortMessage(0xC0, 0, 7, 0), 0L));   // PC @ tick 0
    track.add(new MidiEvent(new ShortMessage(0xB0, 0, 7, 80), 100L)); // CC @ tick 100
    MetaMessage pad = new MetaMessage(0x01, new byte[]{}, 0);
    track.add(new MidiEvent(pad, 500L));

    RecordingMidiProvider recording = new RecordingMidiProvider();
    PlaybackEngine engine = new MidiPlaybackEngine(
        seq, recording, ctx(), 100, 1000.0, Optional.empty(), Optional.empty());
    engine.setInitiallyPaused();

    CompletableFuture<Void> ready = new CompletableFuture<>();
    var mockIO2 = new MockTerminalIO();
    Thread.ofVirtual().start(() -> {
        try {
            ScopedValue.where(TerminalIO.CONTEXT, mockIO2).call(() -> {
                engine.start(new DumbUI()); return null;
            });
        } catch (Exception e) { ready.completeExceptionally(e); }
    });

    // 일시정지 상태에서 seek 후 재개
    Thread.sleep(600); // startup delay 통과 대기
    engine.seekRelative(2_000_000L); // forward 2s
    engine.togglePause();            // 재개

    // 재생이 완료될 때까지 대기
    Thread.sleep(500);
    engine.requestStop(PlaybackStatus.FINISHED);

    // seek 이후에도 정상 종료되어야 함
    assertDoesNotThrow(() -> {});
}
```

### Task 3: 연속 seek 안전성 테스트

```java
@Test @Timeout(5) void rapidConsecutiveSeeks_doNotThrow() throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, 480);
    Track track = seq.createTrack();
    for (int i = 0; i < 20; i++)
        track.add(new MidiEvent(new ShortMessage(0xB0, 0, 7, i * 5), i * 100L));
    MetaMessage pad = new MetaMessage(0x01, new byte[]{}, 0);
    track.add(new MidiEvent(pad, 5000L));

    PlaybackEngine engine = new MidiPlaybackEngine(
        seq, mockProvider, ctx(), 100, 1000.0, Optional.empty(), Optional.empty());

    CompletableFuture<PlaybackStatus> future = new CompletableFuture<>();
    var mockIO2 = new MockTerminalIO();
    Thread.ofVirtual().start(() -> {
        try {
            future.complete(ScopedValue.where(TerminalIO.CONTEXT, mockIO2)
                .call(() -> engine.start(new DumbUI())));
        } catch (Exception e) { future.completeExceptionally(e); }
    });

    Thread.sleep(600); // startup delay
    // 빠르게 seek 10회
    for (int i = 0; i < 10; i++) engine.seekRelative(200_000L);
    for (int i = 0; i < 10; i++) engine.seekRelative(-200_000L);
    engine.requestStop(PlaybackStatus.FINISHED);

    assertDoesNotThrow(() -> future.get());
}
```

### Task 4: 템포 변경 → `getCurrentBpm` 갱신 테스트

```java
@Test void tempoChangeMeta_updateCurrentBpm() throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, 480);
    Track track = seq.createTrack();
    MetaMessage tempo60 = new MetaMessage();
    tempo60.setMessage(0x51, new byte[]{0x0F, 0x42, 0x40}, 3); // 60 BPM
    track.add(new MidiEvent(tempo60, 0L));
    MetaMessage pad = new MetaMessage(0x01, new byte[]{}, 0);
    track.add(new MidiEvent(pad, 100L));

    var clock = new FakeClock();
    PlaybackEngine engine = new MidiPlaybackEngine(
        seq, mockProvider, ctx(), 100, 1000.0, Optional.empty(), Optional.empty(), clock);

    ScopedValue.where(TerminalIO.CONTEXT, mockIO).call(() -> {
        engine.start(new DumbUI()); return null;
    });

    assertEquals(60.0f, engine.getCurrentBpm(), 0.1f,
        "getCurrentBpm() should reflect the tempo meta event");
}
```

### Task 5: `PlaybackEventListener` 알림 테스트

```java
@Test void addPlaybackEventListener_receivesOnTickNotification() throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, 480);
    Track track = seq.createTrack();
    track.add(new MidiEvent(new ShortMessage(0x90, 0, 60, 64), 0L));
    MetaMessage pad = new MetaMessage(0x01, new byte[]{}, 0);
    track.add(new MidiEvent(pad, 100L));

    var clock = new FakeClock();
    PlaybackEngine engine = new MidiPlaybackEngine(
        seq, mockProvider, ctx(), 100, 1000.0, Optional.empty(), Optional.empty(), clock);

    List<Long> ticks = new ArrayList<>();
    engine.addPlaybackEventListener(new PlaybackEventListener() {
        @Override public void onTick(long microseconds) { ticks.add(microseconds); }
    });

    ScopedValue.where(TerminalIO.CONTEXT, mockIO).call(() -> {
        engine.start(new DumbUI()); return null;
    });

    assertFalse(ticks.isEmpty(), "onTick should have been called at least once");
}
```

### Task 6: `decayChannelLevels` 동작 테스트

```java
@Test void decayChannelLevels_reducesLevelAfterNoteOn() throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, 480);
    Track track = seq.createTrack();
    track.add(new MidiEvent(new ShortMessage(0x90, 0, 60, 127), 0L)); // NoteOn ch0 vel=127
    MetaMessage pad = new MetaMessage(0x01, new byte[]{}, 0);
    track.add(new MidiEvent(pad, 100L));

    var clock = new FakeClock();
    PlaybackEngine engine = new MidiPlaybackEngine(
        seq, mockProvider, ctx(), 100, 1000.0, Optional.empty(), Optional.empty(), clock);

    ScopedValue.where(TerminalIO.CONTEXT, mockIO).call(() -> {
        engine.start(new DumbUI()); return null;
    });

    double[] levels = engine.getChannelLevels();
    double levelBefore = levels[0];

    engine.decayChannelLevels(0.5);
    double levelAfter = engine.getChannelLevels()[0];

    assertTrue(levelAfter <= levelBefore,
        "Channel level should not increase after decay");
}
```

---

## 예상 효과

| 시나리오 | 커버되는 private 코드 |
|----------|----------------------|
| BPM 변경 가로지르는 seek | `getTickForTime` (템포 누적 경로) |
| 일시정지 중 seek + 재개 | `seekTarget` 처리, 재개 후 타이밍 리셋 |
| 연속 seek | `seekTarget` atomic 업데이트 경쟁 조건 |
| 템포 변경 메타 | `extractTempoMspqn`, `currentBpm` 갱신 경로 |
| 리스너 알림 | `notificationScheduler` 경로 |
| `decayChannelLevels` | `channelLevels[]` 갱신 |

클래스 구조 변경 없이 `MidiPlaybackEngine`의 미테스트 경로를 모두 간접 커버한다.
