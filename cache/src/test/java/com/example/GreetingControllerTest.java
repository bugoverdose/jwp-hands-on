package com.example;

import static com.example.version.CacheBustingWebConfig.PREFIX_STATIC_RESOURCES;

import com.example.version.ResourceVersion;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GreetingControllerTest {

    private static final Logger log = LoggerFactory.getLogger(GreetingControllerTest.class);

    @Autowired
    private ResourceVersion version;

    @Autowired
    private WebTestClient webTestClient;

    /**
     * HTTP 응답 헤더에 Cache-Control가 없어도 웹 브라우저는 `휴리스틱 캐싱`에 따른 암시적 캐싱을 한다.
     * 의도하지 않은 캐싱을 막기 위해 모든 응답의 헤더에 Cache-Control: no-cache를 명시한다.
     * 또한, 쿠키나 사용자 개인 정보 유출을 막기 위해 private도 추가한다.
     *
     * 인터셉터를 통해 모든 응답 헤더의 기본 캐싱을 아래와 같이 설정한다.
     * `Cache-Control: no-cache, private`
     */
    @Test
    void testNoCachePrivate() {
        final var response = webTestClient
                .get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().cacheControl(CacheControl.noCache().cachePrivate())
                .expectBody(String.class).returnResult();

        log.info("response body\n{}", response.getResponseBody());
    }

    /**
     * HTTP 응답을 압축하면 웹 사이트의 성능을 높일 수 있다.
     * 스프링 부트 설정을 통해 gzip과 같은 HTTP 압축 알고리즘을 적용시킬 수 있다.
     * gzip이 적용됐는지 테스트 코드가 아닌 웹 브라우저에서 HTTP 응답의 헤더를 직접 확인한다.
     *
     * `server.compression.enabled=true`
     * `server.compression.min-response-size=10`
     * - 기본적으로 2KB(2048 바이트) 이상의 응답에 대해서만 압축 수행됨.
     * - 작은 크기의 html 파일도 압축되도록 `min-response-size` 값을 10 바이트로 설정.
     *
     * HTTP Compression 수행된 경우 브라우저 응답에 추가되는 헤더 값들
     * - `Content-Encoding: gzip`
     * - `Transfer-Encoding: chunked`
     */
    @Test
    void testCompression() {
        final var response = webTestClient
                .get()
                .uri("/")
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .exchange()
                .expectStatus().isOk()

                // gzip으로 요청 보내도 어떤 방식으로 압축할지 서버에서 결정한다.
                // 웹브라우저에서 localhost:8080으로 접근하면 응답 헤더에 "Content-Encoding: gzip" 포함되는 것 확인 가능.
                .expectHeader().valueEquals(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .expectBody(String.class).returnResult();

        log.info("response body\n{}", response.getResponseBody());
    }

    /**
     * ETag와 If-None-Match를 사용하여 HTTP 캐싱을 적용해보자.
     * Spring mvc에서 ShallowEtagHeaderFilter 클래스를 제공한다.
     * 필터를 사용하여 /etag 경로만 ETag를 적용하자.
     *
     * 서버측에서 응답 메시지에 Etag 헤더 값만 추가해줘도
     * 브라우저에서 자동으로 If-None-Match 헤더에 해당 값 추가하여 재검증 작업 수행!
     */
    @Test
    void testETag() {
        final var response = webTestClient
                .get()
                .uri("/etag")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.ETAG)
                .expectBody(String.class).returnResult();

        log.info("response body\n{}", response.getResponseBody());
    }

    /**
     * http://localhost:8080/resource-versioning
     * 위 url의 html 파일에서 사용하는 js, css와 같은 정적 파일에 캐싱을 적용한다.
     * 즉, main resource에서 호출하는 정적 리소스의 URI를 재배포될 때마다 다르게 설정한다.
     *
     * 보통 정적 파일을 캐싱 무효화하기 위해 캐싱과 함께 버전을 적용시킨다.
     * 정적 파일에 변경 사항이 생기면 배포할 때 버전을 바꿔주면 적용된 캐싱을 무효화(Caching Busting)할 수 있다.
     */
    @Test
    void testCacheBustingOfStaticResources() {
        final var uri = String.format("%s/%s/js/index.js", PREFIX_STATIC_RESOURCES, version.getVersion());

        // "/resource-versioning/js/index.js" 경로의 정적 파일에 ETag를 사용한 캐싱이 적용되었는지 확인한다.
        final var response = webTestClient
                .get()
                .uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.ETAG)
                .expectHeader().cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .expectBody(String.class).returnResult();

        log.info("response body\n{}", response.getResponseBody());

        final var etag = response.getResponseHeaders().getETag();

        // 캐싱되었다면 "/resource-versioning/js/index.js"로 다시 호출했을때 HTTP status는 304를 반환한다.
        webTestClient.get()
                .uri(uri)
                .header(HttpHeaders.IF_NONE_MATCH, etag)
                .exchange()
                .expectStatus()
                .isNotModified();
    }
}
