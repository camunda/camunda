/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import io.camunda.search.clients.PersistentSessionSearchClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.SessionRepository;

public class WebSessionRepository implements SessionRepository<WebSession> {

  public static final Logger LOGGER = LoggerFactory.getLogger(WebSessionRepository.class);
  private static final String POLLING_HEADER = "x-is-polling";

  private final PersistentSessionSearchClient sessionClient;
  private final WebSessionMapper webSessionMapper;
  private final HttpServletRequest request;

  public WebSessionRepository(
      final PersistentSessionSearchClient sessionClient,
      final WebSessionMapper webSessionMapper,
      final HttpServletRequest request) {
    this.sessionClient = sessionClient;
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
  public void save(final WebSession session) {
    LOGGER.debug("Save session {}", session.getId());
    if (!session.shouldBeDeleted()) {
      saveWebSessionIfChanged(session);
    } else {
      deleteById(session.getId());
    }
  }

  @Override
  public WebSession findById(final String id) {
    LOGGER.debug("Retrieve session {}", id);
    return Optional.ofNullable(sessionClient.getPersistentWebSession(id))
        .map(this::getWebSessionIfNotExpired)
        .orElse(null);
  }

  @Override
  public void deleteById(final String id) {
    LOGGER.debug("Delete session {}", id);
    sessionClient.deletePersistentWebSession(id);
  }

  public void deleteExpiredWebSessions() {
    Optional.ofNullable(sessionClient.getAllPersistentWebSessions())
        .ifPresent(sessions -> sessions.forEach(this::deletePersistentSessionIfExpired));
  }

  private void deletePersistentSessionIfExpired(
      final PersistentWebSessionEntity persistentSession) {
    toWebSession(persistentSession)
        .ifPresentOrElse(
            this::deleteWebSessionIfExpired,
            // otherwise, when the persistent session could
            // not be restored, then delete it.
            () -> deleteById(persistentSession.id()));
  }

  private void deleteWebSessionIfExpired(final WebSession session) {
    if (session.shouldBeDeleted()) {
      deleteById(session.getId());
    }
  }

  private void saveWebSessionIfChanged(final WebSession session) {
    if (session.isChanged()) {
      LOGGER.debug("Web Session {} changed, save in storage.", session);
      Optional.of(session)
          .map(webSessionMapper::toPersistentWebSession)
          .ifPresent(sessionClient::upsertPersistentWebSession);
    }
  }

  private Optional<WebSession> toWebSession(final PersistentWebSessionEntity persistentSession) {
    return Optional.of(persistentSession).map(webSessionMapper::fromPersistentWebSession);
  }

  private WebSession getWebSessionIfNotExpired(final PersistentWebSessionEntity persistentSession) {
    final var webSession = toWebSession(persistentSession).orElse(null);
    if (webSession != null && !webSession.shouldBeDeleted()) {
      webSession.setPolling(isPollingRequest(request));
      return webSession;
    } else {
      // if session is expired (or has no valid authentication),
      // or the web session could not be restored,
      // then immediately delete the persistent session
      deleteById(persistentSession.id());
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
      LOGGER.debug(
          "Expected Exception: is not possible to access request as currently this is not on a request context",
          e);
    }
    return isPollingRequest;
  }
}
