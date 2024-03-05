/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
  private static final Logger logger = LoggerFactory.getLogger(SessionService.class);
  @Autowired SessionRepository sessionRepository;

  @Autowired
  @Qualifier("sessionThreadPoolScheduler")
  private ThreadPoolTaskScheduler sessionThreadScheduler;

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
    sessionThreadScheduler.scheduleAtFixedRate(
        this::removedExpiredSessions, DELETE_EXPIRED_SESSIONS_DELAY);
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
    final String sessionId = UUID.randomUUID().toString().replace("-", "");

    OperateSession session = new OperateSession(sessionId);
    logger.debug(
        "Create session {} with maxInactiveInterval {} ",
        session,
        session.getMaxInactiveInterval());
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
