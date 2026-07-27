/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.TestUserDetailsService;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.security.CamundaSecurityFilterChainConstants;
import io.camunda.service.RoleServices;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public class SessionAuthenticationRefreshTest {

  abstract class BaseTest {
    @Autowired MockMvcTester mockMvcTester;
    Duration refreshInterval;
    @MockitoBean RoleServices roleServices;
    @MockitoBean MembershipPort membershipPort;
    @Autowired private CamundaSecurityLibraryProperties cslProperties;

    @BeforeEach
    public void setup() {
      when(roleServices.hasMembersOfType(any(), any(), any())).thenReturn(true);
      refreshInterval =
          Duration.parse(cslProperties.getAuthentication().getAuthenticationRefreshInterval());
    }

    @Test
    void shouldNotRefreshBeforeInterval() {
      final Cookie sessionCookie = warmUpSession();
      final MvcTestResult first = hitWebapp(sessionCookie);
      assertThat(first).hasStatusOk();
      final Instant firstRefresh = lastRefresh(first);
      assertThat(firstRefresh).isNotNull();

      final MvcTestResult second = hitWebapp(sessionCookie);
      assertThat(second).hasStatusOk();

      assertThat(lastRefresh(second)).isEqualTo(firstRefresh);
    }

    @Test
    void shouldInitializeOnFirstRequest() {
      final Cookie sessionCookie = warmUpSession();
      final MvcTestResult testResult = hitWebapp(sessionCookie);

      assertThat(testResult).hasStatusOk();
      final Instant lastRefreshTime = lastRefresh(testResult);
      assertThat(lastRefreshTime).isNotNull();
      assertThat(lastRefreshTime)
          .isCloseTo(Instant.now(), within(refreshInterval.toMillis(), ChronoUnit.MILLIS));
    }

    @Test
    void shouldRefreshOnWebappAfterInterval() {
      final Cookie sessionCookie = warmUpSession();
      final MvcTestResult first = hitWebapp(sessionCookie);
      final Instant oldRefresh = lastRefresh(first);

      sleepPastRefreshInterval();

      final MvcTestResult second = hitWebapp(sessionCookie);

      assertThat(second).hasStatusOk();
      assertThat(lastRefresh(second)).isAfter(oldRefresh);
    }

    @Test
    void shouldRefreshOnApiAfterInterval() {
      final Cookie sessionCookie = warmUpSession();
      final MvcTestResult first = hitApi(sessionCookie);
      final Instant oldRefresh = lastRefresh(first);

      sleepPastRefreshInterval();

      // The API chain's SessionManagementFilter sees no SPRING_SECURITY_CONTEXT stored in the
      // session (nothing writes one; .with(authentication(...)) bypasses that) and treats each
      // call as a fresh login, rotating the session id via ChangeSessionIdAuthenticationStrategy.
      // Reuse the rotated cookie from the first response, mirroring a real client's cookie jar.
      final MvcTestResult second = hitApi(sessionCookieOf(first));

      assertThat(second).hasStatusOk();
      assertThat(lastRefresh(second)).isAfter(oldRefresh);
    }

    @Test
    void shouldOnlyRefreshOnceWhenMultipleConcurrentRequests() throws InterruptedException {
      final Cookie sessionCookie = warmUpSession();
      final MvcTestResult first = hitWebapp(sessionCookie);
      assertThat(first).hasStatusOk();
      final Instant oldRefresh = lastRefresh(first);

      sleepPastRefreshInterval();

      final var result = sendMultipleConcurrentRequests(sessionCookie);

      assertThat(result.successfulRequests().get()).isEqualTo(result.threads().length);
      // HttpSessionBasedAuthenticationHolder's JVM-local dedup guard (CSL ADR-0035) only
      // guarantees that at most one request performs the refresh; a request that loses the race
      // never rewrites its own HttpSession snapshot, so it can still legitimately report
      // oldRefresh for the remainder of its own request. Assert the guarantee the design actually
      // makes -- exactly one new, shared refresh occurs -- not that every concurrent request
      // observes it.
      final var refreshTimes = result.lastRefreshTimes();
      final var newRefreshTimes =
          refreshTimes.stream().filter(refresh -> refresh.isAfter(oldRefresh)).distinct().toList();
      assertThat(newRefreshTimes).as("only one refresh across all concurrent requests").hasSize(1);
    }

    /**
     * Establishes a real, Spring-Session-backed session before the request under test runs. See
     * {@link TestApiController#DUMMY_SESSION_WARMUP_ENDPOINT} for why this step is necessary.
     */
    private Cookie warmUpSession() {
      final MvcTestResult warmup =
          hit("https://localhost" + TestApiController.DUMMY_SESSION_WARMUP_ENDPOINT, null);
      assertThat(warmup).hasStatusOk();
      return sessionCookieOf(warmup);
    }

    private TestingAuthenticationToken testAuthentication() {
      final var mockAuthentication = createMockAuthentication();
      final var testAuthentication = new TestingAuthenticationToken(mockAuthentication, null);
      testAuthentication.setAuthenticated(true);
      return testAuthentication;
    }

    private CamundaAuthentication createMockAuthentication() {
      return new CamundaAuthentication(
          TestUserDetailsService.DEMO_USERNAME,
          null,
          false,
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          new HashMap<>());
    }

    private MvcTestResult hitWebapp(final Cookie sessionCookie) {
      return hit("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT, sessionCookie);
    }

    private MvcTestResult hitApi(final Cookie sessionCookie) {
      return hit("https://localhost" + TestApiController.DUMMY_V2_API_AUTH_ENDPOINT, sessionCookie);
    }

    private MvcTestResult hit(final String uri, final Cookie sessionCookie) {
      final var requestSpec =
          mockMvcTester
              .get()
              .with(SecurityMockMvcRequestPostProcessors.authentication(testAuthentication()))
              .uri(uri);
      return (sessionCookie == null ? requestSpec : requestSpec.cookie(sessionCookie)).exchange();
    }

    private Cookie sessionCookieOf(final MvcTestResult result) {
      final Cookie sessionCookie =
          result.getResponse().getCookie(CamundaSecurityFilterChainConstants.SESSION_COOKIE);
      assertThat(sessionCookie).isNotNull();
      return sessionCookie;
    }

    private Instant lastRefresh(final MvcTestResult result) {
      final var header = result.getResponse().getHeader(TestApiController.LAST_AUTH_REFRESH_HEADER);
      return header == null ? null : Instant.parse(header);
    }

    private void sleepPastRefreshInterval() {
      try {
        Thread.sleep(refreshInterval.plusMillis(500).toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    private ConcurrentRequestsResult sendMultipleConcurrentRequests(final Cookie sessionCookie)
        throws InterruptedException {
      final AtomicInteger successfulRequests = new AtomicInteger(0);
      final var lastRefreshTimes =
          java.util.Collections.synchronizedList(new java.util.ArrayList<Instant>());
      final Runnable requestTask =
          () -> {
            final MvcTestResult testResult = hitWebapp(sessionCookie);
            assertThat(testResult).hasStatusOk();
            lastRefreshTimes.add(lastRefresh(testResult));
            successfulRequests.incrementAndGet();
          };

      final Thread[] threads = new Thread[5];
      for (int i = 0; i < threads.length; i++) {
        threads[i] = new Thread(requestTask);
        threads[i].start();
        Thread.sleep(10);
      }

      for (final Thread thread : threads) {
        thread.join();
      }
      return new ConcurrentRequestsResult(successfulRequests, threads, lastRefreshTimes);
    }

    private record ConcurrentRequestsResult(
        AtomicInteger successfulRequests,
        Thread[] threads,
        java.util.List<Instant> lastRefreshTimes) {}
  }

  @Nested
  @SpringBootTest(
      classes = {WebSecurityConfigTestContext.class, WebSecurityConfig.class},
      properties = {"camunda.security.authentication.authentication-refresh-interval=PT1S"})
  @AutoConfigureMockMvc
  @AutoConfigureWebMvc
  @ActiveProfiles({"consolidated-auth"})
  class BasicAuthTest extends BaseTest {

    // Webapp's endpoints are accessible without authentication only in basic auth mode
    @Test
    void shouldNotInitializeWithoutAuthentication() {
      final MvcTestResult testResult =
          mockMvcTester
              .get()
              .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
              .exchange();

      assertThat(testResult).hasStatusOk();
      final var header =
          testResult.getResponse().getHeader(TestApiController.LAST_AUTH_REFRESH_HEADER);
      assertThat(header).isNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = {WebSecurityConfigTestContext.class, WebSecurityConfig.class},
      properties = {
        "camunda.security.authentication.method=oidc",
        "camunda.security.authentication.authentication-refresh-interval=PT1S"
      })
  @AutoConfigureMockMvc
  @AutoConfigureWebMvc
  @ActiveProfiles("consolidated-auth")
  class OidcAuthTest extends BaseTest {
    @TestBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private JwtDecoder jwtDecoder;

    static ClientRegistrationRepository clientRegistrationRepository() {
      final var dummyRegistration =
          ClientRegistration.withRegistrationId("test")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .clientId("test-client")
              .redirectUri("{baseUrl}/sso-callback")
              .authorizationUri("https://example.com/authorize")
              .tokenUri("https://example.com/token")
              .issuerUri("https://example.com")
              .build();
      return new InMemoryClientRegistrationRepository(dummyRegistration);
    }
  }
}
