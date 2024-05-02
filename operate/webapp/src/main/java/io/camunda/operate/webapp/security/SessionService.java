/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.stereotype.Component;

@Configuration
@ConditionalOnExpression(
    "${camunda.operate.persistent.sessions.enabled:false}"
        + " or "
        + "${camunda.operate.persistentSessionsEnabled:false}")
@Component
@EnableSpringHttpSession
public class SessionService
    implements org.springframework.session.SessionRepository<OperateSession> {
  public static final int DELETE_EXPIRED_SESSIONS_DELAY = 1_000 * 60 * 30; // min
  private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);
  @Autowired SessionRepository sessionRepository;

  @Autowired
  @Qualifier("sessionThreadPoolScheduler")
  private ThreadPoolTaskScheduler sessionThreadScheduler;

  @PostConstruct
  private void setUp() {
    LOGGER.debug("Persistent sessions in enabled");
    startExpiredSessionCheck();
  }

  @PreDestroy
  private void tearDown() {
    LOGGER.debug("Shutdown SessionService");
  }

  private void startExpiredSessionCheck() {
    sessionThreadScheduler.scheduleAtFixedRate(
        this::removedExpiredSessions, DELETE_EXPIRED_SESSIONS_DELAY);
  }

  private void removedExpiredSessions() {
    LOGGER.debug("Check for expired sessions");
    sessionRepository.getExpiredSessionIds().forEach(this::deleteById);
  }

  private boolean shouldDeleteSession(final OperateSession session) {
    return session.isExpired() || (session.containsAuthentication() && !session.isAuthenticated());
  }

  @Override
  public OperateSession createSession() {
    // Frontend e2e tests are relying on this pattern
    final String sessionId = UUID.randomUUID().toString().replace("-", "");

    final OperateSession session = new OperateSession(sessionId);
    LOGGER.debug(
        "Create session {} with maxInactiveInterval {} ",
        session,
        session.getMaxInactiveInterval());
    return session;
  }

  @Override
  public void save(OperateSession session) {
    LOGGER.debug("Save session {}", session);
    if (shouldDeleteSession(session)) {
      deleteById(session.getId());
      return;
    }
    if (session.isChanged()) {
      LOGGER.debug("Session {} changed, save in Elasticsearch.", session);
      sessionRepository.save(session);
      session.clearChangeFlag();
    }
  }

  @Override
  public OperateSession findById(final String id) {
    LOGGER.debug("Retrieve session {} from Elasticsearch", id);
    final Optional<OperateSession> maybeSession = sessionRepository.findById(id);
    if (maybeSession.isEmpty()) {
      // need to delete entry in Elasticsearch in case of failing restore session
      deleteById(id);
      return null;
    }

    final OperateSession session = maybeSession.get();
    if (shouldDeleteSession(session)) {
      deleteById(session.getId());
      return null;
    } else {
      return session;
    }
  }

  @Override
  public void deleteById(String id) {
    executeAsyncElasticsearchRequest(() -> sessionRepository.deleteById(id));
  }

  private void executeAsyncElasticsearchRequest(Runnable requestRunnable) {
    sessionThreadScheduler.execute(requestRunnable);
  }
}
