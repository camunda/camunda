/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.session;

import io.camunda.gatekeeper.spi.SessionPersistencePort;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.SessionRepository;

/**
 * {@link SessionRepository} backed by a {@link SessionPersistencePort} SPI implementation. All
 * persistence is delegated to the port so the library remains storage-agnostic.
 */
public class WebSessionRepository implements SessionRepository<WebSession> {

  private static final Logger LOG = LoggerFactory.getLogger(WebSessionRepository.class);
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
    final String sessionId = UUID.randomUUID().toString().replace("-", "");
    final WebSession session = new WebSession(sessionId);
    LOG.debug(
        "Create session {} with maxInactiveInterval {} s",
        session,
        session.getMaxInactiveInterval());
    return session;
  }

  @Override
  public void save(final WebSession webSession) {
    LOG.debug("Save session {}", webSession.getId());
    if (!webSession.shouldBeDeleted()) {
      saveWebSessionIfChanged(webSession);
    } else {
      deleteById(webSession.getId());
    }
  }

  @Override
  public WebSession findById(final String id) {
    LOG.debug("Retrieve session {}", id);
    return Optional.ofNullable(id)
        .filter(this::isSessionIdNotEmpty)
        .flatMap(sessionPersistencePort::findById)
        .map(webSessionMapper::fromSessionData)
        .map(this::getWebSessionIfNotExpired)
        .orElse(null);
  }

  @Override
  public void deleteById(final String id) {
    LOG.debug("Delete session {}", id);
    Optional.ofNullable(id)
        .filter(this::isSessionIdNotEmpty)
        .ifPresent(sessionPersistencePort::deleteById);
  }

  /** Deletes all expired web sessions via the persistence port. */
  public void deleteExpiredWebSessions() {
    sessionPersistencePort.deleteExpired();
  }

  private void saveWebSessionIfChanged(final WebSession webSession) {
    if (webSession.isChanged()) {
      LOG.debug("Web Session {} changed, save in storage.", webSession);
      final var sessionData = webSessionMapper.toSessionData(webSession);
      sessionPersistencePort.save(sessionData);
    }
  }

  private WebSession getWebSessionIfNotExpired(final WebSession webSession) {
    if (webSession != null && !webSession.shouldBeDeleted()) {
      webSession.setPolling(isPollingRequest(request));
      return webSession;
    } else {
      // if session is expired (or has no valid authentication),
      // or the web session could not be restored,
      // then immediately delete the persistent session
      if (webSession != null) {
        deleteById(webSession.getId());
      }
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
      // This can happen, if it is called outside a dispatcher servlet.
      LOG.debug(
          "Expected Exception: is not possible to access request"
              + " as currently this is not on a request context",
          e);
    }
    return isPollingRequest;
  }

  private boolean isSessionIdNotEmpty(final String sessionId) {
    return sessionId != null && !sessionId.isEmpty();
  }
}
