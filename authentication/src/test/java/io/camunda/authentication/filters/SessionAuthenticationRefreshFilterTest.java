/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static io.camunda.authentication.filters.SessionAuthenticationRefreshFilter.LAST_REFRESH_ATTR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.TestUserDetailsService;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.service.MembershipService;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public class SessionAuthenticationRefreshFilterTest {

  abstract class BaseTest {
    @Autowired MockMvcTester mockMvcTester;
    Duration refreshInterval;
    @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
    @Autowired private SecurityConfiguration securityConfiguration;

    @BeforeEach
    public void setup() {
      reset(authenticationProvider);
      refreshInterval =
          Duration.parse(
              securityConfiguration.getAuthentication().getAuthenticationRefreshInterval());
    }

    @Test
    void shouldNotRefreshBeforeInterval() {
      final MockHttpSession session = new MockHttpSession();
      setSessionRefreshAttribute(session, refreshInterval.minus(Duration.ofSeconds(5)));
      setupAuthentication();

      final MvcTestResult testResult =
          mockMvcTester
              .get()
              .session(session)
              .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
              .exchange();

      assertThat(testResult).hasStatusOk();
      verify(authenticationProvider, never()).refresh();
    }

    @Test
    void shouldInitializeOnFirstRequest() {
      final MockHttpSession session = new MockHttpSession();
      setupAuthentication();

      final MvcTestResult testResult =
          mockMvcTester
              .get()
              .session(session)
              .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
              .exchange();

      assertThat(testResult).hasStatusOk();
      final Instant lastRefreshTime = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
      assertThat(lastRefreshTime).isNotNull();
      assertThat(lastRefreshTime)
          .isCloseTo(Instant.now(), within(refreshInterval.toMillis(), ChronoUnit.MILLIS));
      verify(authenticationProvider, never()).refresh();
    }

    @Test
    void shouldRefreshOnWebappAfterInterval() {
      final MockHttpSession session = new MockHttpSession();
      setSessionRefreshAttribute(session, refreshInterval.multipliedBy(2));
      setupAuthentication();
      final Instant oldRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);

      final MvcTestResult testResult =
          mockMvcTester
              .get()
              .session(session)
              .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
              .exchange();

      assertThat(testResult).hasStatusOk();
      verify(authenticationProvider, times(1)).refresh();
      final Instant lastRefreshTime = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
      assertThat(lastRefreshTime).isAfter(oldRefresh);
    }

    @Test
    void shouldRefreshOnApiAfterInterval() {
      final MockHttpSession session = new MockHttpSession();
      setSessionRefreshAttribute(session, refreshInterval.multipliedBy(2));
      setupAuthentication();
      final Instant oldRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);

      final MvcTestResult testResult =
          mockMvcTester
              .get()
              .session(session)
              .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
              .exchange();

      assertThat(testResult).hasStatusOk();
      verify(authenticationProvider, times(1)).refresh();
      final Instant lastRefreshTime = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
      assertThat(lastRefreshTime).isAfter(oldRefresh);
    }

    @Test
    void shouldOnlyRefreshOnceWhenMultipleConcurrentRequests() throws Exception {
      final MockHttpSession session = new MockHttpSession();
      setSessionRefreshAttribute(session, refreshInterval.multipliedBy(2));

      final var result =
          getSendMultipleRequest(session, authenticationProvider, mockMvcTester);

      assertThat(result.successfulRequests().get()).isEqualTo(result.threads().length);
      verify(authenticationProvider, times(1)).refresh();
      final Instant newRefreshTime = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
      assertThat(newRefreshTime).isAfter(Instant.now().minusMillis(refreshInterval.toMillis()));
    }

    private void setupAuthentication() {
      final var mockAuthentication = createMockAuthentication();
      final var testAuthentication = new TestingAuthenticationToken(mockAuthentication, null);
      testAuthentication.setAuthenticated(true);
      SecurityContextHolder.getContext().setAuthentication(testAuthentication);
      when(authenticationProvider.getCamundaAuthentication()).thenReturn(mockAuthentication);
    }

    private CamundaAuthentication createMockAuthentication() {
      return new CamundaAuthentication(
          TestUserDetailsService.DEMO_USERNAME,
          "",
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          new HashMap<>());
    }

    private void setSessionRefreshAttribute(
        final MockHttpSession session, final Duration refreshInterval) {
      final Instant lastRefreshRef = Instant.now().minus(refreshInterval);
      session.setAttribute(LAST_REFRESH_ATTR, lastRefreshRef);
      session.setAttribute(LAST_REFRESH_ATTR + "_LOCK", session.getId() + "LOCK");
    }

    private SendMultipleRequest getSendMultipleRequest(
        final MockHttpSession session,
        final CamundaAuthenticationProvider authenticationProvider,
        final MockMvcTester mockMvcTester)
        throws InterruptedException {
      doAnswer(
              invocation -> {
                Thread.sleep(100);
                return null; // because it's a void method
              })
          .when(authenticationProvider)
          .refresh();
      final AtomicInteger successfulRequests = new AtomicInteger(0);
      final Runnable requestTask =
          () -> {
            try {
              setupAuthentication();
              final MvcTestResult testResult =
                  mockMvcTester
                      .get()
                      .session(session)
                      .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
                      .exchange();
              assertThat(testResult).hasStatusOk();
              successfulRequests.incrementAndGet();
            } catch (final Exception e) {
              throw new RuntimeException(e);
            }
          };

      final Thread[] threads = new Thread[10];
      for (int i = 0; i < threads.length; i++) {
        threads[i] = new Thread(requestTask);
        threads[i].start();
      }

      for (final Thread thread : threads) {
        thread.join();
      }
      return new SendMultipleRequest(successfulRequests, threads);
    }

    private record SendMultipleRequest(AtomicInteger successfulRequests, Thread[] threads) {}
  }

  @Nested
  @SpringBootTest(
      classes = {
        WebSecurityConfigTestContext.class,
        WebSecurityConfig.class,
      })
  @AutoConfigureMockMvc
  @AutoConfigureWebMvc
  @ActiveProfiles("consolidated-auth")
  class BasicAuthTest extends BaseTest {

    // Webapp's endpoints are accessible without authentication only in basic auth mode
    @Test
    void shouldNotInitializeWithoutAuthentication() {
      final MockHttpSession session = new MockHttpSession();

      final MvcTestResult testResult =
          mockMvcTester
              .get()
              .session(session)
              .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
              .exchange();

      assertThat(testResult).hasStatusOk();
      final Instant lastRefreshTime = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
      assertThat(lastRefreshTime).isNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = {
        WebSecurityConfigTestContext.class,
        WebSecurityConfig.class,
      },
      properties = {"camunda.security.authentication.method=oidc"})
  @AutoConfigureMockMvc
  @AutoConfigureWebMvc
  @ActiveProfiles("consolidated-auth")
  class OidcAuthTest extends BaseTest {
    @MockitoBean private MembershipService membershipService;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private JwtDecoder jwtDecoder;
  }
}
