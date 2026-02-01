# 실습을 위한 개발 환경 세팅
* https://github.com/slipp/web-application-server 프로젝트를 자신의 계정으로 Fork한다. Github 우측 상단의 Fork 버튼을 클릭하면 자신의 계정으로 Fork된다.
* Fork한 프로젝트를 eclipse 또는 터미널에서 clone 한다.
* Fork한 프로젝트를 eclipse로 import한 후에 Maven 빌드 도구를 활용해 eclipse 프로젝트로 변환한다.(mvn eclipse:clean eclipse:eclipse)
* 빌드가 성공하면 반드시 refresh(fn + f5)를 실행해야 한다.

# 웹 서버 시작 및 테스트
* webserver.WebServer 는 사용자의 요청을 받아 RequestHandler에 작업을 위임하는 클래스이다.
* 사용자 요청에 대한 모든 처리는 RequestHandler 클래스의 run() 메서드가 담당한다.
* WebServer를 실행한 후 브라우저에서 http://localhost:8080으로 접속해 "Hello World" 메시지가 출력되는지 확인한다.

# 각 요구사항별 학습 내용 정리
* 구현 단계에서는 각 요구사항을 구현하는데 집중한다. 
* 구현을 완료한 후 구현 과정에서 새롭게 알게된 내용, 궁금한 내용을 기록한다.
* 각 요구사항을 구현하는 것이 중요한 것이 아니라 구현 과정을 통해 학습한 내용을 인식하는 것이 배움에 중요하다. 

### 요구사항 1 - http://localhost:8080/index.html로 접속시 응답
#### 핵심 로그 확인
* 정상 로그 예시
  * Request Line: GET /index.html HTTP/1.1
  * Request Line: GET /favicon.ico HTTP/1.1
* 브라우저는 페이지 렌더링을 위해 여러 리소스를 동시에 요청한다.
  * HTML 외에 CSS/JS/이미지/favicon 등 추가 요청이 따라옴
  * 요청 개수만큼 스레드와 로그가 늘어나는 것이 정상

#### 요청과 소켓 관계 정리
* 요청은 “이미 열린 소켓(TCP 연결) 위로 HTTP 데이터를 보내는 것”
* 연결이 없으면 먼저 새 소켓을 열고 요청을 보냄
* 현재 서버 구조는 요청 1개 처리 후 연결을 닫으므로
  * 브라우저 입장에선 리소스마다 새 연결이 만들어지는 것처럼 보임

#### 경로 처리 포인트
* 요청 경로는 항상 “/”로 시작한다. (예: /index.html, /login)
* 파일 경로로 만들 때는 앞의 “/”를 제거해야 webapp 기준으로 합쳐짐
  * 예: Path.of("webapp", path.substring(1)) → webapp/login


### 요구사항 2 - get 방식으로 회원가입
#### 핵심 흐름 요약
* 회원가입 폼 제출 시 서버로 “HTTP 요청 메시지”가 들어온다.
  * 요청 첫 줄 형식: METHOD URL HTTP/버전
  * 예: GET /user/create?userId=...&password=...&name=...&email=... HTTP/1.1
* 서버는 첫 줄에서 URL만 뽑고, `?` 기준으로 경로와 쿼리스트링을 분리한다.
  * 경로: /user/create
  * 쿼리스트링: userId=...&password=...&name=...&email=...
* 쿼리스트링은 `HttpRequestUtils.parseQueryString`으로 Map으로 파싱한다.
  * params.get("userId") 형태로 값 추출 가능
* 추출한 값으로 `User` 객체를 생성해 파싱이 정상인지 로그로 확인한다.
  * URL 인코딩(%40 = @)은 디코딩 단계에서 처리 가능

#### 현재 응답 방식
* 회원가입 처리 후에는 `/index.html`을 내려주도록 URL을 바꿔 정적 파일을 응답한다.
* 정적 파일 응답은 `Files.readAllBytes`로 읽고 200 OK 헤더 + 바디를 전송한다.

### 요구사항 3 - post 방식으로 회원가입
#### 요구사항 2(GET)와 달라진 점
* 파라미터 위치가 **URL → Body**로 이동한다.
  * GET: `/user/create?userId=...&password=...`
  * POST: `POST /user/create` + body에 `userId=...&password=...`
* 따라서 URL이 아니라 **요청 바디를 읽어 파싱**해야 한다.
  * 헤더 끝(빈 줄)까지 읽고 `Content-Length` 값을 얻는다.
  * 그 길이만큼 body를 읽어서 `parseQueryString`으로 Map 변환한다.
* 메서드 분기가 필요하다.
  * GET/POST를 구분해서 **POST일 때만** body를 읽는다.

### 요구사항 4 - redirect 방식으로 이동
#### 핵심 규칙 1가지
* HTTP는 “요청 1번 → 응답 1번”이 기본 단위다.
* 302는 “/index.html로 가라”는 **새 요청 유도 응답**이지, index.html을 직접 담아 보내는 응답이 아니다.

#### 헷갈렸던 질문에 대한 결론
* Q: “302에는 바디가 없다면서, index.html 바이트는 어디서 읽어 보내나?”
* A: **/index.html 요청을 처리하는 정적 파일 로직에서 읽어 보낸다.**

#### 실제 흐름은 ‘두 번 요청’
* (1) 회원가입 요청
  * 브라우저 → 서버: `POST /user/create`
  * 서버는 회원가입 처리 후 **302 응답**만 보냄
* (2) 리다이렉트로 인한 새 요청
  * 브라우저가 `Location: /index.html`을 보고 **GET /index.html**을 새로 요청
  * 서버가 여기서 `index.html`을 읽어 200 OK로 응답

#### 비교 정리
* 200으로 index.html을 바로 내려주면: 요청 1번으로 끝
* 302 리다이렉트이면: **요청이 2번 발생**

### 요구사항 5 - cookie
* 

### 요구사항 6 - stylesheet 적용
* 

### heroku 서버에 배포 후
* 
