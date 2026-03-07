/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.session;

import io.camunda.auth.domain.model.SessionData;
import io.camunda.auth.domain.spi.SessionPersistencePort;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.SessionRepository;

public class WebSessionRepository implements SessionRepository<WebSession> {

  public static final Logger LOGGER = LoggerFactory.getLogger(WebSessionRepository.class);
  private static final String POLLING_HEADER = "x-is-polling";

  private final SessionPersistencePort sessionPersistencePort;
  private final WebSessionMapper webSessionMapper;
  private final HttpServletRequest request;

  public WebSessionRepository(
      final SessionPersistencePort sessionPersistencePort,
      final WebSessionMapper webSessionMapper,
      final HttpServletRequest request) {
    this.sessionPersistencePort = sessionPersistencePort;
    this.webSessionMapper = webSessionMapper;
    this.request = request;
  }

  @Override
  public WebSession createSession() {
    final var sessionId = UUID.randomUUID().toString().replace("-", "");
    final var session = new WebSession(sessionId);
    LOGGER.debug(
        "Create session {} with maxInactiveInterval {} s",
        session,
        session.getMaxInactiveInterval());
    return session;
  }

  @Override
  public void save(final WebSession webSession) {
    LOGGER.debug("Save session {}", webSession.getId());
    if (!webSession.shouldBeDeleted()) {
      saveWebSessionIfChanged(webSession);
    } else {
      deleteById(webSession.getId());
    }
  }

  @Override
  public WebSession findById(final String id) {
    LOGGER.debug("Retrieve session {}", id);
    return Optional.ofNullable(id)
        .filter(this::isSessionIdNotEmpty)
        .map(sessionPersistencePort::findById)
        .map(this::getWebSessionIfNotExpired)
        .orElse(null);
  }

  @Override
  public void deleteById(final String id) {
    LOGGER.debug("Delete session {}", id);
    Optional.ofNullable(id)
        .filter(this::isSessionIdNotEmpty)
        .ifPresent(sessionPersistencePort::deleteById);
  }

  public void deleteExpiredWebSessions() {
    sessionPersistencePort.deleteExpired();
  }

  private void saveWebSessionIfChanged(final WebSession webSession) {
    if (webSession.isChanged()) {
      LOGGER.debug("Web Session {} changed, save in storage.", webSession);
      Optional.of(webSession)
          .map(webSessionMapper::toSessionData)
          .ifPresent(sessionPersistencePort::save);
    }
  }

  private Optional<WebSession> toWebSession(final SessionData sessionData) {
    return Optional.of(sessionData).map(webSessionMapper::fromSessionData);
  }

  private WebSession getWebSessionIfNotExpired(final SessionData sessionData) {
    final var webSession = toWebSession(sessionData).orElse(null);
    if (webSession != null && !webSession.shouldBeDeleted()) {
      webSession.setPolling(isPollingRequest(request));
      return webSession;
    } else {
      deleteById(sessionData.id());
      return null;
    }
  }

  private boolean isPollingRequest(final HttpServletRequest request) {
    boolean isPollingRequest = false;
    try {
      isPollingRequest =
          request != null
              && request.getHeader(POLLING_HEADER) != null
              && Boolean.parseBoolean(request.getHeader(POLLING_HEADER));
    } catch (final Exception e) {
      LOGGER.debug(
          "Expected Exception: is not possible to access request as currently this is not on a request context",
          e);
    }
    return isPollingRequest;
  }

  private boolean isSessionIdNotEmpty(final String sessionId) {
    return sessionId != null && !sessionId.isEmpty();
  }
}
