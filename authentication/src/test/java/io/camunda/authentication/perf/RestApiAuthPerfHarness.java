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
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

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
import io.camunda.authentication.service.MembershipService;
import io.camunda.authentication.service.NoDBMembershipService;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.configuration.SecurityConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Local performance harness for the REST + OIDC bearer-token chain.
 *
 * <p>Boots the real {@link WebSecurityConfig#oidcApiSecurity} chain with a real {@code
 * NimbusJwtDecoder} talking to an in-process WireMock JWKS endpoint, drives requests through
 * MockMvc against {@code /v2/auth} (which calls {@code getCamundaAuthentication()}), and reports
 * latency percentiles + ops/sec.
 *
 * <p>Use to iterate on candidate optimisations (cache placement, JWT-decoder swaps,
 * MembershipService variants) before promoting to a full cluster benchmark.
 *
 * <p>To switch the {@link MembershipService} variant, run with one of:
 *
 * <ul>
 *   <li>{@code -Dharness.membership=nodb} (default) — anonymous-style, near-zero cost
 *   <li>{@code -Dharness.membership=simulated-db} — sleeps {@code harness.simulatedDbLatencyMs} (4
 *       roundtrips simulated)
 * </ul>
 *
 * <p>To run a single scenario:
 *
 * <pre>
 *   ./mvnw verify -pl authentication \
 *       -Dtest=RestApiAuthPerfHarness#singleThreadLatency \
 *       -DskipTests=false -DskipITs -Dquickly \
 *       -Dharness.membership=simulated-db -Dharness.simulatedDbLatencyMs=1
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
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=perf-client",
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      "camunda.security.csrf.enabled=false",
    })
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@ActiveProfiles("consolidated-auth")
@Disabled("Local perf harness — enable manually; see ROOT_CAUSE.md")
public class RestApiAuthPerfHarness {

  private static final int WARMUP_REQUESTS = 1_000;
  private static final int LATENCY_REQUESTS = 5_000;
  private static final int CONCURRENT_THREADS = 16;
  private static final int CONCURRENT_REQUESTS_PER_THREAD = 1_000;

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().configureStaticDsl(true).build();

  private static RSAKey rsaJwk;
  private static String issuerUri;

  @Autowired private MockMvcTester mockMvc;

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    issuerUri = "http://localhost:" + wireMock.getPort() + "/issuer";
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
    registry.add(
        "camunda.security.authentication.oidc.jwk-set-uri", () -> issuerUri + "/jwks.json");
  }

  @BeforeAll
  static void publishJwks() throws JOSEException {
    rsaJwk =
        new RSAKeyGenerator(2048)
            .keyID("perf-kid")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
    final var jwksBody =
        JSONObjectUtils.toJSONString(new JWKSet(rsaJwk.toPublicJWK()).toJSONObject());
    stubFor(get(urlMatching(".*/issuer/jwks.json")).willReturn(okJson(jwksBody)));
    // Spring auto-config probes /.well-known/openid-configuration if jwk-set-uri is unset; we
    // set it explicitly above, but stub it anyway so any probe doesn't 404 noisily.
    stubFor(
        get(urlMatching(".*/issuer/.well-known/openid-configuration"))
            .willReturn(okJson(openIdConfigBody())));
  }

  @Test
  void singleThreadLatency() throws Exception {
    final var token = signedAccessToken();

    for (int i = 0; i < WARMUP_REQUESTS; i++) {
      hit(token);
    }

    final var timings = new long[LATENCY_REQUESTS];
    final var startWall = System.nanoTime();
    for (int i = 0; i < LATENCY_REQUESTS; i++) {
      final var t0 = System.nanoTime();
      hit(token);
      timings[i] = System.nanoTime() - t0;
    }
    final var elapsedNanos = System.nanoTime() - startWall;
    printStats("single-thread latency", timings, elapsedNanos);
  }

  @Test
  void multiThreadThroughput() throws Exception {
    final var token = signedAccessToken();

    for (int i = 0; i < 200; i++) {
      hit(token);
    }

    final var pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
    final var latch = new CountDownLatch(CONCURRENT_THREADS);
    final var perThreadTimings = Collections.synchronizedList(new ArrayList<long[]>());
    final var failures = Collections.synchronizedList(new ArrayList<Throwable>());

    final var startWall = System.nanoTime();
    for (int t = 0; t < CONCURRENT_THREADS; t++) {
      pool.submit(
          () -> {
            try {
              final var local = new long[CONCURRENT_REQUESTS_PER_THREAD];
              for (int i = 0; i < CONCURRENT_REQUESTS_PER_THREAD; i++) {
                final var t0 = System.nanoTime();
                hit(token);
                local[i] = System.nanoTime() - t0;
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
    final var flat = perThreadTimings.stream().flatMapToLong(Arrays::stream).toArray();
    printStats(CONCURRENT_THREADS + "-thread throughput", flat, elapsedNanos);
  }

  private void hit(final String token) throws Exception {
    final var result =
        mockMvc
            .get()
            .uri(TestApiController.DUMMY_V2_API_AUTH_ENDPOINT)
            .header("Authorization", "Bearer " + token)
            .exchange();
    final var status = result.getResponse().getStatus();
    if (status != 200) {
      throw new IllegalStateException(
          "Expected 200 but got " + status + " body=" + result.getResponse().getContentAsString());
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
    return "{\"issuer\":\""
        + "http://localhost:"
        + wireMock.getPort()
        + "/issuer"
        + "\",\"jwks_uri\":\""
        + "http://localhost:"
        + wireMock.getPort()
        + "/issuer/jwks.json"
        + "\",\"subject_types_supported\":[\"public\"]}";
  }

  /**
   * Spring config for the harness. Provides a {@link MembershipService} variant selected by the
   * {@code harness.membership} property — defaults to a no-op (NoDB) so the baseline measures
   * filter-chain + JWT decode + delegating-converter + holder cost only.
   */
  @Configuration
  static class PerfTestContext {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "harness.membership", havingValue = "nodb", matchIfMissing = true)
    public MembershipService noDbMembershipService(
        final SecurityConfiguration securityConfiguration) {
      return new NoDBMembershipService(securityConfiguration);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "harness.membership", havingValue = "simulated-db")
    public MembershipService simulatedDbMembershipService(
        final SecurityConfiguration securityConfiguration,
        @Value("${harness.simulatedDbLatencyMs:1}") final long latencyMs) {
      final var delegate = new NoDBMembershipService(securityConfiguration);
      return (claims, principalId, principalType) -> {
        // Simulates the four secondary-storage roundtrips DefaultMembershipService issues:
        // mappingRules, groups (when no groups claim), roles, tenants.
        for (int i = 0; i < 4; i++) {
          try {
            Thread.sleep(latencyMs);
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return CamundaAuthentication.anonymous();
          }
        }
        return delegate.resolveMemberships(claims, principalId, principalType);
      };
    }

    /** Avoids Spring complaining about an empty list of converters when nothing else publishes. */
    @Bean
    public List<Object> placeholderToSatisfyAutowire() {
      return List.of();
    }
  }
}
