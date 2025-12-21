# CalorieHunter

영양 관리를 RPG 게임으로 만든 Android 앱입니다. 건강하지 않은 음식은 몬스터가 되어 전투하고, 건강한 음식은 무기와 포션이 됩니다.

## 주요 기능

### 음식 스캔 & 분석
- **바코드 스캔**: ML Kit을 활용한 실시간 바코드 인식
- **AI 분석**: Google Gemini AI로 음식 사진/이름 기반 영양 정보 추정
- **Open Food Facts API**: 바코드 기반 영양 정보 조회

### 게임화 시스템
- **몬스터 시스템**: 당류, 나트륨, 포화지방이 높은 음식은 몬스터로 변환
- **아이템 시스템**: 건강한 음식은 무기/포션/버프 아이템으로 획득
- **턴제 전투**: 전략적인 아이템 사용과 전투 시스템
- **레벨 & 경험치**: 전투 승리 시 경험치 획득 및 레벨업

### 기타 기능
- **일일 퀘스트**: 매일 갱신되는 5가지 퀘스트
- **출석 체크**: 연속 출석 보상 시스템
- **영양 통계**: 일별 영양소 섭취량 및 기록 확인
- **인벤토리**: 무기/포션/버프 아이템 관리

## 기술 스택

| 분류 | 기술 |
|------|------|
| 플랫폼 | Android (Min SDK 26, Target SDK 36) |
| 언어 | Java |
| 백엔드 | Firebase (Auth, Realtime Database) |
| AI | Google Gemini AI |
| 바코드 | ML Kit Barcode Scanning |
| 카메라 | CameraX |
| 네트워크 | Retrofit2, OkHttp |
| UI | Material Design 3, Lottie Animation |

## 앱 구조

```
com.example.caloriehunter/
├── activity/          # 화면 (MainActivity, BattleActivity 등)
├── api/               # Gemini AI 서비스
├── data/
│   ├── api/           # Open Food Facts API
│   ├── model/         # 데이터 모델 (User, Monster, Item 등)
│   └── repository/    # Firebase Repository
└── util/              # 유틸리티 클래스
```

## 음식 분석 로직

### 1단계: 클래스 결정
영양소 비율에 따라 RPG 클래스 부여:
- **전사(Warrior)**: 고단백 → 무기 생성
- **마법사(Mage)**: 고탄수화물 → 공격 버프
- **버서커(Berserker)**: 고지방 → 강력한 무기
- **사제(Priest)**: 고식이섬유 → 포션/방어 버프

### 2단계: 타락 판정
나쁜 영양소 기반 타락 점수 계산:
- 당류: 1점/g
- 나트륨: 0.01점/mg
- 포화지방: 3점/g
- 트랜스지방: 20점/g

### 3단계: 최종 결정
- **타락 점수 > 20** 또는 **패스트푸드 감지** → 몬스터 생성
- **그 외** → 아이템 생성

## 설치 및 실행

1. 프로젝트 클론
2. `local.properties`에 API 키 추가:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key
   ```
3. Firebase 프로젝트 연결 (`google-services.json`)
4. Android Studio에서 빌드 및 실행

## 라이선스

이 프로젝트는 학습 목적으로 제작되었습니다.
