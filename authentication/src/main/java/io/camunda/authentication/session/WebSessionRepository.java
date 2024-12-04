/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import io.camunda.search.clients.PersistentWebSessionClient;
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

  private final PersistentWebSessionClient persistentWebSessionClient;
  private final WebSessionMapper webSessionMapper;
  private final HttpServletRequest request;

  public WebSessionRepository(
      final PersistentWebSessionClient persistentWebSessionClient,
      final WebSessionMapper webSessionMapper,
      final HttpServletRequest request) {
    this.persistentWebSessionClient = persistentWebSessionClient;
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
        .map(persistentWebSessionClient::getPersistentWebSession)
        .map(this::getWebSessionIfNotExpired)
        .orElse(null);
  }

  @Override
  public void deleteById(final String id) {
    LOGGER.debug("Delete session {}", id);
    Optional.ofNullable(id)
        .filter(this::isSessionIdNotEmpty)
        .ifPresent(persistentWebSessionClient::deletePersistentWebSession);
  }

  public void deleteExpiredWebSessions() {
    Optional.ofNullable(persistentWebSessionClient.getAllPersistentWebSessions())
        .ifPresent(
            persistentWebSessionEntities ->
                persistentWebSessionEntities.forEach(this::deletePersistentWebSessionIfExpired));
  }

  private void deletePersistentWebSessionIfExpired(
      final PersistentWebSessionEntity persistentWebSessionEntity) {
    toWebSession(persistentWebSessionEntity)
        .ifPresentOrElse(
            this::deleteWebSessionIfExpired,
            // otherwise, when the persistent session could
            // not be restored, then delete it.
            () -> deleteById(persistentWebSessionEntity.id()));
  }

  private void deleteWebSessionIfExpired(final WebSession webSession) {
    if (webSession.shouldBeDeleted()) {
      deleteById(webSession.getId());
    }
  }

  private void saveWebSessionIfChanged(final WebSession webSession) {
    if (webSession.isChanged()) {
      LOGGER.debug("Web Session {} changed, save in storage.", webSession);
      Optional.of(webSession)
          .map(webSessionMapper::toPersistentWebSession)
          .ifPresent(persistentWebSessionClient::upsertPersistentWebSession);
    }
  }

  private Optional<WebSession> toWebSession(
      final PersistentWebSessionEntity persistentWebSessionEntity) {
    return Optional.of(persistentWebSessionEntity).map(webSessionMapper::fromPersistentWebSession);
  }

  private WebSession getWebSessionIfNotExpired(
      final PersistentWebSessionEntity persistentWebSessionEntity) {
    final var webSession = toWebSession(persistentWebSessionEntity).orElse(null);
    if (webSession != null && !webSession.shouldBeDeleted()) {
      webSession.setPolling(isPollingRequest(request));
      return webSession;
    } else {
      // if session is expired (or has no valid authentication),
      // or the web session could not be restored,
      // then immediately delete the persistent session
      deleteById(persistentWebSessionEntity.id());
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

  private boolean isSessionIdNotEmpty(final String sessionId) {
    return sessionId != null && !sessionId.isEmpty();
  }
}
