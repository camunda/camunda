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
        .ifPresent(
            sessions ->
                sessions.stream()
                    .map(webSessionMapper::fromPersistentWebSession)
                    .filter(WebSession::shouldBeDeleted)
                    .forEach(this::deleteWebSession));
  }

  private void deleteWebSession(final WebSession session) {
    deleteById(session.getId());
  }

  private void saveWebSessionIfChanged(final WebSession session) {
    if (session.isChanged()) {
      LOGGER.debug("Web Session {} changed, save in storage.", session);
      Optional.of(session)
          .map(webSessionMapper::toPersistentWebSession)
          .ifPresent(sessionClient::upsertPersistentWebSession);
    }
  }

  private WebSession getWebSessionIfNotExpired(final PersistentWebSessionEntity persistentSession) {
    final var webSession =
        Optional.ofNullable(persistentSession)
            .map(webSessionMapper::fromPersistentWebSession)
            .orElse(null);

    if (webSession != null) {
      if (!webSession.shouldBeDeleted()) {
        webSession.setPolling(isPollingRequest(request));
      } else {
        // if session is expired (or has no valid authentication),
        // then immediately delete the persistent session
        deleteById(webSession.getId());
        return null;
      }
    }
    return webSession;
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
          "Expected Exception: is not possible to access request as currently this is not on a request context");
    }
    return isPollingRequest;
  }
}
