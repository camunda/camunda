/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.TestUserDetailsService;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityConfig.class,
    })
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@ActiveProfiles("consolidated-auth")
public class SessionAuthenticationRefreshFilterTest {
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
  void shouldOnlyRefreshOnceWhenMultipleConcurrentRequests() throws Exception {
    // Setup session with refresh needed
    final MockHttpSession session = new MockHttpSession();
    setSessionRefreshAttribute(session);
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(createMockAuthentication());

    // Simulate concurrent requests
    final AtomicInteger successfulRequests = new AtomicInteger(0);
    final Runnable requestTask =
        () -> {
          try {
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

    // Create multiple threads
    final Thread[] threads = new Thread[10];
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread(requestTask);
      threads[i].start();
    }

    // Wait for completion
    for (final Thread thread : threads) {
      thread.join();
    }

    // Verify results
    assertThat(successfulRequests.get()).isEqualTo(threads.length);
    verify(authenticationProvider, times(1)).refresh();
    final Instant newRefreshTime = (Instant) session.getAttribute("AUTH_LAST_REFRESH");
    assertThat(newRefreshTime).isAfter(Instant.now().minusMillis(refreshInterval.toMillis()));
  }

  @Test
  void shouldNotRefreshBeforeInterval() {
    final MockHttpSession session = new MockHttpSession();
    setSessionRefreshAttribute(session);
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(createMockAuthentication());

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
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(createMockAuthentication());

    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .session(session)
            .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
            .exchange();

    assertThat(testResult).hasStatusOk();
    final Instant lastRefreshTime = (Instant) session.getAttribute("AUTH_LAST_REFRESH");
    assertThat(lastRefreshTime).isNotNull();
    assertThat(lastRefreshTime)
        .isCloseTo(Instant.now(), within(refreshInterval.toMillis(), ChronoUnit.MILLIS));
    verify(authenticationProvider, never()).refresh();
  }

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
    final Instant lastRefreshTime = (Instant) session.getAttribute("AUTH_LAST_REFRESH");
    assertThat(lastRefreshTime).isNull();
  }

  @Test
  void shouldRefreshAfterInterval() {
    final MockHttpSession session = new MockHttpSession();
    setSessionRefreshAttribute(session);
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(createMockAuthentication());
    final Instant oldRefresh = (Instant) session.getAttribute("AUTH_LAST_REFRESH");

    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .session(session)
            .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
            .exchange();

    assertThat(testResult).hasStatusOk();
    verify(authenticationProvider, times(1)).refresh();
    final Instant lastRefreshTime = (Instant) session.getAttribute("AUTH_LAST_REFRESH");
    assertThat(lastRefreshTime).isAfter(oldRefresh);
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

  private void setSessionRefreshAttribute(final MockHttpSession session) {
    final Instant lastRefreshRef = Instant.now().minus(refreshInterval.multipliedBy(2));
    session.setAttribute("AUTH_LAST_REFRESH", lastRefreshRef);
    session.setAttribute("AUTH_LAST_REFRESH" + "_LOCK", new Object());
  }
}
