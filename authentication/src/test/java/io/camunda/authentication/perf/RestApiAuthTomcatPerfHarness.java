/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.perf;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Local performance harness for the REST + OIDC bearer-token chain that runs against a real
 * embedded Tomcat (unlike {@link RestApiAuthPerfHarness}, which uses MockMvc and skips Tomcat).
 *
 * <p>Use this harness to attach an external profiler (e.g. async-profiler in {@code wall} mode) and
 * capture the full request path including the connector, servlet, all filters, the controller, and
 * the auth chain. MockMvc cannot show connector / NIO behaviour or any I/O wait outside the
 * dispatcher servlet path; this harness can.
 *
 * <p>Reuses {@link RestApiAuthPerfHarness.PerfTestContext} for the {@link
 * io.camunda.authentication.service.MembershipService} variants, so the same {@code
 * harness.membership} property + {@code harness.simulatedDbLatencyMs} apply.
 *
 * <p><b>Profile from IntelliJ (recommended).</b> Right-click the {@code multiThreadThroughput} (or
 * {@code singleThreadLatency}) method → <em>Profile … with → Async Profiler</em>. Pick
 * <em>Wall-clock</em> for I/O wait, <em>CPU</em> for hot paths. The profile is captured for the
 * full test run. To make the run long enough for a meaningful flame graph, raise the load duration
 * via system properties (Run/Debug Configuration → VM options):
 *
 * <pre>
 *   -Dharness.durationSeconds=30
 *   -Dharness.membership=real-mocked
 *   -Dharness.simulatedDbLatencyMs=5
 *   -Dharness.threads=32
 * </pre>
 *
 * <p>{@code harness.durationSeconds &gt; 0} switches both scenarios to a tight time-bounded loop
 * (instead of fixed iteration counts), which is the right shape for profiling.
 *
 * <p><b>Profile from CLI with async-profiler.</b>
 *
 * <pre>
 *   # Terminal A — start the harness with profiler-wait enabled
 *   HARNESS_WAIT_FOR_PROFILER=true ./mvnw verify -pl authentication \
 *       -Dtest=RestApiAuthTomcatPerfHarness#multiThreadThroughput \
 *       -DskipTests=false -DskipITs -Dquickly \
 *       -Dharness.durationSeconds=30 \
 *       -Dharness.membership=real-mocked -Dharness.simulatedDbLatencyMs=5
 *
 *   # The test prints its PID and pauses on stdin. In Terminal B, attach a profiler:
 *   asprof -e wall -d 30 -f /tmp/perf-wall.html &lt;PID&gt;
 *
 *   # Then return to Terminal A and press ENTER to start the load loop.
 * </pre>
 *
 * <p>Disabled by default so it never runs in CI. Remove the {@link Disabled} annotation locally to
 * execute.
 */
@SuppressWarnings({"SpringBootApplicationProperties", "WrongPropertyKeyValueDelimiter"})
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      RestApiAuthPerfHarness.PerfTestContext.class,
      WebSecurityConfig.class,
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=perf-client",
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      "camunda.security.csrf.enabled=false",
      // server.tomcat.threads.max default is 200; expose so it can be tuned per run.
      "server.tomcat.threads.max=200",
      "server.tomcat.threads.min-spare=20",
    })
// Bring in just enough autoconfig for embedded Tomcat + Spring MVC; explicitly exclude DataSource
// and JPA so we don't need a database driver on the classpath.
@EnableAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@ActiveProfiles("consolidated-auth")
// @Disabled("Local perf harness — enable manually; see ROOT_CAUSE.md")
public class RestApiAuthTomcatPerfHarness {

  private static final int WARMUP_REQUESTS = 1_000;
  private static final int LATENCY_REQUESTS = 5_000;
  private static final int CONCURRENT_THREADS = Integer.getInteger("harness.threads", 16);
  private static final int CONCURRENT_REQUESTS_PER_THREAD = 1_000;

  /**
   * If set to a positive value, both scenarios run for at least this many seconds in a tight loop
   * instead of using the fixed iteration counts. Useful when attaching a profiler — async-profiler
   * needs ~10–30 s of sustained activity to produce a usable flame graph. Set via {@code
   * -Dharness.durationSeconds=30} (or higher) when launching.
   */
  private static final long DURATION_SECONDS = Long.getLong("harness.durationSeconds", 0L);

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .build();

  private static RSAKey rsaJwk;
  private static String issuerUri;

  @LocalServerPort private int serverPort;

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    issuerUri = "http://localhost:" + wireMock.getPort() + "/issuer";
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
    registry.add(
        "camunda.security.authentication.oidc.jwk-set-uri", () -> issuerUri + "/jwks.json");
  }

  @BeforeAll
  static void generateKeyAndStubForContextLoad() throws JOSEException {
    rsaJwk =
        new RSAKeyGenerator(2048)
            .keyID("perf-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
    stubIdpEndpoints();
  }

  @BeforeEach
  void rePublishStubsAfterPerTestReset() {
    // WireMockExtension resets stubs between tests; the JWT decoder fetches the JWKS on every
    // test, so re-register the stubs here as well.
    stubIdpEndpoints();
  }

  private static void stubIdpEndpoints() {
    final var jwksBody =
        JSONObjectUtils.toJSONString(new JWKSet(rsaJwk.toPublicJWK()).toJSONObject());
    stubFor(get(urlEqualTo("/issuer/jwks.json")).willReturn(okJson(jwksBody)));
    stubFor(
        get(urlEqualTo("/issuer/.well-known/openid-configuration"))
            .willReturn(okJson(openIdConfigBody())));
  }

  @Test
  void singleThreadLatency() throws Exception {
    waitForProfilerIfRequested();
    final var token = signedAccessToken();
    final var http = newHttpClient();
    final var endpoint = endpointUri();

    for (int i = 0; i < WARMUP_REQUESTS; i++) {
      hit(http, endpoint, token);
    }

    final var timings = new java.util.ArrayList<Long>();
    final var startWall = System.nanoTime();
    final var deadline =
        DURATION_SECONDS > 0
            ? startWall + java.time.Duration.ofSeconds(DURATION_SECONDS).toNanos()
            : Long.MAX_VALUE;
    final var iterCap = DURATION_SECONDS > 0 ? Integer.MAX_VALUE : LATENCY_REQUESTS;
    for (int i = 0; i < iterCap && System.nanoTime() < deadline; i++) {
      final var t0 = System.nanoTime();
      hit(http, endpoint, token);
      timings.add(System.nanoTime() - t0);
    }
    final var elapsedNanos = System.nanoTime() - startWall;
    printStats(
        "single-thread latency (Tomcat)",
        timings.stream().mapToLong(Long::longValue).toArray(),
        elapsedNanos);
  }

  @Test
  void multiThreadThroughput() throws Exception {
    waitForProfilerIfRequested();
    final var token = signedAccessToken();
    final var http = newHttpClient();
    final var endpoint = endpointUri();

    for (int i = 0; i < 200; i++) {
      hit(http, endpoint, token);
    }

    final var pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
    final var latch = new CountDownLatch(CONCURRENT_THREADS);
    final var perThreadTimings =
        Collections.synchronizedList(new ArrayList<java.util.ArrayList<Long>>());
    final var failures = Collections.synchronizedList(new ArrayList<Throwable>());

    final var startWall = System.nanoTime();
    final var deadline =
        DURATION_SECONDS > 0
            ? startWall + java.time.Duration.ofSeconds(DURATION_SECONDS).toNanos()
            : Long.MAX_VALUE;
    final var iterCap = DURATION_SECONDS > 0 ? Integer.MAX_VALUE : CONCURRENT_REQUESTS_PER_THREAD;
    for (int t = 0; t < CONCURRENT_THREADS; t++) {
      pool.submit(
          () -> {
            try {
              final var local = new java.util.ArrayList<Long>();
              for (int i = 0; i < iterCap && System.nanoTime() < deadline; i++) {
                final var t0 = System.nanoTime();
                hit(http, endpoint, token);
                local.add(System.nanoTime() - t0);
              }
              perThreadTimings.add(local);
            } catch (final Throwable e) {
              failures.add(e);
            } finally {
              latch.countDown();
            }
          });
    }
    latch.await();
    final var elapsedNanos = System.nanoTime() - startWall;
    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.SECONDS);

    if (!failures.isEmpty()) {
      throw new AssertionError("Concurrent run had failures", failures.getFirst());
    }
    final var flat =
        perThreadTimings.stream()
            .flatMap(java.util.Collection::stream)
            .mapToLong(Long::longValue)
            .toArray();
    printStats(CONCURRENT_THREADS + "-thread throughput (Tomcat)", flat, elapsedNanos);
  }

  private URI endpointUri() {
    return URI.create(
        "http://localhost:" + serverPort + TestApiController.DUMMY_V2_API_AUTH_ENDPOINT);
  }

  private static HttpClient newHttpClient() {
    // Shared client across threads — the JDK HttpClient is thread-safe and pools connections
    // per origin, mirroring how a real client would talk to the API.
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(2))
        .build();
  }

  private static void hit(final HttpClient http, final URI endpoint, final String token)
      throws Exception {
    final var request =
        HttpRequest.newBuilder(endpoint)
            .header("Authorization", "Bearer " + token)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    final HttpResponse<Void> response = http.send(request, BodyHandlers.discarding());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Expected 200 but got " + response.statusCode() + " from " + endpoint);
    }
  }

  private String signedAccessToken() throws JOSEException {
    final var now = Instant.now();
    final var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuerUri)
            .subject("perf-user")
            .audience("perf-client")
            .claim("scope", "openid")
            .claim("jti", UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(3600)))
            .build();
    final var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(rsaJwk.toPrivateKey()));
    return jwt.serialize();
  }

  /**
   * If {@code HARNESS_WAIT_FOR_PROFILER=true} is set in the environment, print the JVM PID and
   * block on stdin so an external profiler can attach before the load loop starts.
   */
  private static void waitForProfilerIfRequested() {
    if (!Boolean.parseBoolean(System.getenv("HARNESS_WAIT_FOR_PROFILER"))) {
      return;
    }
    final long pid = ProcessHandle.current().pid();
    System.out.println();
    System.out.println("=================================================================");
    System.out.println(" JVM PID: " + pid);
    System.out.println(" Attach a profiler now, e.g.:");
    System.out.println("   asprof -e wall -d 30 -f /tmp/perf-wall.html " + pid);
    System.out.println(" Then press ENTER here to start the load loop.");
    System.out.println("=================================================================");
    try {
      while (System.in.read() != '\n') {
        // discard
      }
    } catch (final Exception ignored) {
      // not interactive — proceed anyway
    }
  }

  private static void printStats(final String label, final long[] timings, final long totalNanos) {
    final var sorted = timings.clone();
    Arrays.sort(sorted);
    final var n = sorted.length;
    final long p50 = sorted[(int) (n * 0.50)];
    final long p95 = sorted[(int) (n * 0.95)];
    final long p99 = sorted[(int) (n * 0.99)];
    final long p999 = sorted[(int) (n * 0.999)];
    final long min = sorted[0];
    final long max = sorted[n - 1];
    final var opsPerSec = n / (totalNanos / 1_000_000_000.0);

    System.out.println();
    System.out.println("=== " + label + " ===");
    System.out.printf("  ops:        %d%n", n);
    System.out.printf("  ops/sec:    %.1f%n", opsPerSec);
    System.out.printf("  min:        %.3f ms%n", min / 1_000_000.0);
    System.out.printf("  p50:        %.3f ms%n", p50 / 1_000_000.0);
    System.out.printf("  p95:        %.3f ms%n", p95 / 1_000_000.0);
    System.out.printf("  p99:        %.3f ms%n", p99 / 1_000_000.0);
    System.out.printf("  p99.9:      %.3f ms%n", p999 / 1_000_000.0);
    System.out.printf("  max:        %.3f ms%n", max / 1_000_000.0);
  }

  private static String openIdConfigBody() {
    final var base = "http://localhost:" + wireMock.getPort() + "/issuer";
    return "{"
        + "\"issuer\":\""
        + base
        + "\","
        + "\"authorization_endpoint\":\""
        + base
        + "/oauth/authorize\","
        + "\"token_endpoint\":\""
        + base
        + "/oauth/token\","
        + "\"userinfo_endpoint\":\""
        + base
        + "/userinfo\","
        + "\"jwks_uri\":\""
        + base
        + "/jwks.json\","
        + "\"response_types_supported\":[\"code\"],"
        + "\"subject_types_supported\":[\"public\"],"
        + "\"id_token_signing_alg_values_supported\":[\"RS256\"]"
        + "}";
  }
}
