/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Configuration
@ConditionalOnExpression(
    "${camunda.operate.persistent.sessions.enabled:false}"
        + " or " +
    "${camunda.operate.persistentSessionsEnabled:false}"
)
@Component
@EnableSpringHttpSession
public class SessionService implements org.springframework.session.SessionRepository<OperateSession> {
  private static final Logger logger = LoggerFactory.getLogger(SessionService.class);
  public static final int DELETE_EXPIRED_SESSIONS_DELAY = 1_000 * 60 * 30; // min

  @Autowired
  @Qualifier("sessionThreadPoolScheduler")
  private ThreadPoolTaskScheduler sessionThreadScheduler;

  @Autowired
  SessionRepository sessionRepository;

  @PostConstruct
  private void setUp() {
    logger.debug("Persistent sessions in enabled");
    startExpiredSessionCheck();
  }

  @PreDestroy
  private void tearDown() {
    logger.debug("Shutdown SessionService");
  }

  private void startExpiredSessionCheck() {
    sessionThreadScheduler.scheduleAtFixedRate(this::removedExpiredSessions, DELETE_EXPIRED_SESSIONS_DELAY);
  }

  private void removedExpiredSessions() {
    logger.debug("Check for expired sessions");
    sessionRepository.getExpiredSessionIds().forEach(this::deleteById);
  }

  private boolean shouldDeleteSession(final OperateSession session) {
    return session.isExpired() || (session.containsAuthentication() && !session.isAuthenticated());
  }

  @Override
  public OperateSession createSession() {
    // Frontend e2e tests are relying on this pattern
    final String sessionId = UUID.randomUUID().toString().replace("-","");

    OperateSession session = new OperateSession(sessionId);
    logger.debug("Create session {} with maxInactiveInterval {} ", session, session.getMaxInactiveInterval());
    return session;
  }

  @Override
  public void save(OperateSession session) {
    logger.debug("Save session {}", session);
    if (shouldDeleteSession(session)) {
      deleteById(session.getId());
      return;
    }
    if (session.isChanged()) {
      logger.debug("Session {} changed, save in Elasticsearch.", session);
      sessionRepository.save(session);
      session.clearChangeFlag();
    }
  }

  @Override
  public OperateSession findById(final String id) {
    logger.debug("Retrieve session {} from Elasticsearch", id);
    final Optional<OperateSession> maybeSession = sessionRepository.findById(id);
    if (maybeSession.isEmpty() ) {
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
