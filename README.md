# Arena Agent

LMArena(`https://arena.ai/text/direct?model_a=max`) 웹을 AI 백엔드로 쓰고, Android 접근성 서비스로 기기를 직접 조작하는 에이전트 앱.

## 빌드 (GitHub Actions)
1. 이 폴더를 GitHub 저장소에 push (main/master)
2. Actions 탭 → **Build APK** → Artifacts 의 `arena-agent-debug-apk` 다운로드
3. APK 설치

## 첫 실행
1. 앱 실행 → 로딩 UI → Google 로그인 창 → 로그인 (쿠키는 저장되어 재실행 시 유지)
2. 상단 좌측 버튼 또는 "접근성 서비스 켜기" → **Arena Agent** 활성화
   - Android 13+ 사이드로드 앱: 앱 정보 → ⋮ → **제한된 설정 허용** 후 활성화
3. 명령 입력 (예: "설정 앱에서 Wi-Fi 상태 알려줘")

## v2 변경점
- **빌드 오류 수정**: `AgentPrompt` 가 참조하는 `DeviceState` 가 없던 문제(파일에 `DeviceActions` 만 존재)를 해결. `DeviceActions.kt` / `DeviceState.kt` 로 분리하고, `WRITE_SETTINGS`·`SET_ALARM` 권한 추가, 외부 의존성(backdrop) 제거.
- **속도**
  - 한 번에 최대 12개 명령 배치 + `tap`/`wait_for` 를 *텍스트 기반*으로 지원 → 화면이 바뀌어도 한 턴에 연쇄 실행
  - 고정 `delay` 대신 접근성 이벤트가 잠잠해질 때까지만 대기(`settle`)
  - 완성된 `work` 블록이 보이면 답변 생성을 즉시 중단하고 다음 단계로 진행(`ArenaController.EARLY_STOP`)
  - 직접 명령만 실행한 턴은 화면 덤프를 다시 보내지 않고, `done` 을 같은 턴에 반환
  - 폴링 250→150ms, 설치 앱 목록은 라벨만 전송
- **시스템 API 직접 명령**: `volume` `brightness` `torch` `media` `set_alarm` `set_timer` `open_settings` `search` `open_url` `intent`(범용) `dial` `sms` `clipboard_set` `launch_app`(퍼지 매칭) `list_apps` `info` `read_screen`
- **UI 전면 재구성**: 순수 검정 / 포인트 블루(#3B82F6) / 블랙그레이 / 화이트, Liquid Glass 제거, Claude 모바일 앱 스타일 레이아웃
  - 스트리밍: 글자 단위로 풀어내며 *블러→선명* 페이드, 마크다운(굵게/코드/목록/제목) 렌더
  - 입력창: 스프링 팽창, 모서리 변형, 전송↔중지 아이콘 전환 + 색 전환
  - 인사말: 랜덤(시간대 반영) + 페이드/블러/떠오름, 입력창과 별도 영역에 배치되어 가려지지 않음

## 구조
- `ArenaController` : WebView 로 LMArena 로그인/전송/스트리밍/중단/새 세션 (DOM 주입)
- `WorkParser` : 응답의 ```work JSON 블록 해석
- `AgentViewModel` : 목표 달성까지 반복하는 에이전트 루프 (최대 40 step)
- `AgentAccessibilityService` : 화면 덤프 + UI 조작 + 직접 명령 라우팅
- `DeviceActions` : UI 없이 시스템 API/인텐트로 즉시 수행하는 명령
- `DeviceState` : 프롬프트용 기기 정보 / 앱 목록 / 화면 덤프
- `ui/` : `Theme`(팔레트·아이콘·블러), `ChatScreen`, `StreamingText`, `Markdown`, `AppRoot`, `AgentCursorOverlay`
