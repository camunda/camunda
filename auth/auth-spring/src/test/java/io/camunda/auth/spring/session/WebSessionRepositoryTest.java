/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.SessionData;
import io.camunda.auth.domain.spi.SessionPersistencePort;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebSessionRepositoryTest {

  @Mock private SessionPersistencePort sessionPersistencePort;
  @Mock private WebSessionMapper webSessionMapper;
  @Mock private HttpServletRequest request;

  @InjectMocks private WebSessionRepository webSessionRepository;

  @Test
  void shouldCreateSessionWithNonEmptyId() {
    // when
    final var session = webSessionRepository.createSession();

    // then
    assertThat(session).isNotNull();
    assertThat(session.getId()).isNotNull().isNotEmpty();
  }

  @Test
  void shouldSaveChangedSession() {
    // given
    final var webSession = new WebSession("session-id");
    webSession.setAttribute("key", "value");
    final var sessionData = mock(SessionData.class);
    when(webSessionMapper.toSessionData(webSession)).thenReturn(sessionData);

    // when
    webSessionRepository.save(webSession);

    // then
    verify(sessionPersistencePort).save(sessionData);
  }

  @Test
  void shouldSkipSaveForUnchangedSession() {
    // given
    final var webSession = new WebSession("session-id");

    // when
    webSessionRepository.save(webSession);

    // then
    verifyNoInteractions(sessionPersistencePort);
    verifyNoInteractions(webSessionMapper);
  }

  @Test
  void shouldDeleteSessionThatShouldBeDeleted() {
    // given
    final var webSession = new WebSession("session-id");
    webSession.setMaxInactiveInterval(java.time.Duration.ZERO);

    // when
    webSessionRepository.save(webSession);

    // then
    verify(sessionPersistencePort).deleteById("session-id");
  }

  @Test
  void shouldFindSessionById() {
    // given
    final var sessionData = mock(SessionData.class);
    when(sessionPersistencePort.findById("session-id")).thenReturn(sessionData);
    final var webSession = new WebSession("session-id");
    when(webSessionMapper.fromSessionData(sessionData)).thenReturn(webSession);

    // when
    final var result = webSessionRepository.findById("session-id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("session-id");
  }

  @Test
  void shouldReturnNullForNullId() {
    // when
    final var result = webSessionRepository.findById(null);

    // then
    assertThat(result).isNull();
    verifyNoInteractions(sessionPersistencePort);
  }

  @Test
  void shouldReturnNullForEmptyId() {
    // when
    final var result = webSessionRepository.findById("");

    // then
    assertThat(result).isNull();
    verifyNoInteractions(sessionPersistencePort);
  }

  @Test
  void shouldDeleteById() {
    // when
    webSessionRepository.deleteById("session-id");

    // then
    verify(sessionPersistencePort).deleteById("session-id");
  }

  @Test
  void shouldDeleteExpiredSessions() {
    // when
    webSessionRepository.deleteExpiredWebSessions();

    // then
    verify(sessionPersistencePort).deleteExpired();
  }

  @Test
  void shouldDetectPollingRequest() {
    // given
    final var sessionData = mock(SessionData.class);
    when(sessionPersistencePort.findById("session-id")).thenReturn(sessionData);
    final var webSession = new WebSession("session-id");
    when(webSessionMapper.fromSessionData(sessionData)).thenReturn(webSession);
    when(request.getHeader("x-is-polling")).thenReturn("true");

    // when
    final var result = webSessionRepository.findById("session-id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.isPolling()).isTrue();
  }
}
