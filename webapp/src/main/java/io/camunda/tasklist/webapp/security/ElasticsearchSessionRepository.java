/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.schema.indices.TasklistWebSessionIndex.ATTRIBUTES;
import static io.camunda.tasklist.schema.indices.TasklistWebSessionIndex.CREATION_TIME;
import static io.camunda.tasklist.schema.indices.TasklistWebSessionIndex.ID;
import static io.camunda.tasklist.schema.indices.TasklistWebSessionIndex.LAST_ACCESSED_TIME;
import static io.camunda.tasklist.schema.indices.TasklistWebSessionIndex.MAX_INACTIVE_INTERVAL_IN_SECONDS;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.schema.indices.TasklistWebSessionIndex;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.stereotype.Component;

@ConditionalOnExpression(
    "${camunda.tasklist.persistent.sessions.enabled:false}"
        + " or "
        + "${camunda.tasklist.persistentSessionsEnabled:false}")
@Component
@EnableSpringHttpSession
public class ElasticsearchSessionRepository
    implements SessionRepository<ElasticsearchSessionRepository.ElasticsearchSession> {

  public static final int DELETE_EXPIRED_SESSIONS_DELAY = 1_000 * 60 * 30; // min
  public static final String POLLING_HEADER = "x-is-polling";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchSessionRepository.class);

  @Autowired
  @Qualifier("sessionThreadPoolScheduler")
  public ThreadPoolTaskScheduler taskScheduler;

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired private GenericConversionService conversionService;

  @Autowired private TasklistWebSessionIndex tasklistWebSessionIndex;

  @Autowired private HttpServletRequest request;

  @PostConstruct
  private void setUp() {
    LOGGER.debug("Persistent sessions in Elasticsearch enabled");
    setupConverter();
    startExpiredSessionCheck();
  }

  @PreDestroy
  private void tearDown() {
    LOGGER.debug("Shutdown ElasticsearchSessionRepository");
  }

  private void setupConverter() {
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
  }

  private void startExpiredSessionCheck() {
    taskScheduler.scheduleAtFixedRate(this::removedExpiredSessions, DELETE_EXPIRED_SESSIONS_DELAY);
  }

  private void removedExpiredSessions() {
    LOGGER.debug("Check for expired sessions");
    final SearchRequest searchRequest =
        new SearchRequest(tasklistWebSessionIndex.getFullQualifiedName());
    retryElasticsearchClient.doWithEachSearchResult(
        searchRequest,
        sh -> {
          final Map<String, Object> document = sh.getSourceAsMap();
          final Optional<ElasticsearchSession> maybeSession = documentToSession(document);
          if (maybeSession.isPresent()) {
            final Session session = maybeSession.get();
            LOGGER.debug("Check if session {} is expired: {}", session, session.isExpired());
            if (session.isExpired()) {
              deleteById(session.getId());
            }
          } else {
            // need to delete entry in Elasticsearch in case of failing restore session
            deleteById(getSessionIdFrom(document));
          }
        });
  }

  @Override
  public ElasticsearchSession createSession() {
    // Frontend e2e tests are relying on this pattern
    final String sessionId = UUID.randomUUID().toString().replace("-", "");

    final ElasticsearchSession session = new ElasticsearchSession(sessionId);
    LOGGER.debug(
        "Create session {} with maxInactiveInterval {} s",
        session,
        session.getMaxInactiveInterval());
    return session;
  }

  @Override
  public void save(ElasticsearchSession session) {
    LOGGER.debug("Save session {}", session);
    if (session.isExpired()) {
      deleteById(session.getId());
      return;
    }
    if (session.isChanged()) {
      LOGGER.debug("Session {} changed, save in Elasticsearch.", session);
      retryElasticsearchClient.createOrUpdateDocument(
          tasklistWebSessionIndex.getFullQualifiedName(),
          session.getId(),
          sessionToDocument(session));
      session.clearChangeFlag();
    }
  }

  @Override
  public ElasticsearchSession findById(final String id) {
    LOGGER.debug("Retrieve session {} from Elasticsearch", id);
    retryElasticsearchClient.refresh(tasklistWebSessionIndex.getFullQualifiedName());
    Map<String, Object> document;
    try {
      document =
          retryElasticsearchClient.getDocument(tasklistWebSessionIndex.getFullQualifiedName(), id);
    } catch (Exception e) {
      document = null;
    }
    if (document == null) {
      return null;
    }

    final Optional<ElasticsearchSession> maybeSession = documentToSession(document);
    if (maybeSession.isEmpty()) {
      // need to delete entry in Elasticsearch in case of failing restore session
      deleteById(getSessionIdFrom(document));
      return null;
    }

    final ElasticsearchSession session = maybeSession.get();
    if (session.isExpired()) {
      deleteById(session.getId());
      return null;
    } else {
      return session;
    }
  }

  @Override
  public void deleteById(String id) {
    LOGGER.debug("Delete session {}", id);
    executeAsyncElasticsearchRequest(
        () ->
            retryElasticsearchClient.deleteDocument(
                tasklistWebSessionIndex.getFullQualifiedName(), id));
  }

  private byte[] serialize(Object object) {
    return (byte[])
        conversionService.convert(
            object, TypeDescriptor.valueOf(Object.class), TypeDescriptor.valueOf(byte[].class));
  }

  private Object deserialize(byte[] bytes) {
    return conversionService.convert(
        bytes, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
  }

  private Map<String, Object> sessionToDocument(ElasticsearchSession session) {
    final Map<String, byte[]> attributes = new HashMap<>();
    session
        .getAttributeNames()
        .forEach(name -> attributes.put(name, serialize(session.getAttribute(name))));
    return Map.of(
        ID,
        session.getId(),
        CREATION_TIME,
        session.getCreationTime().toEpochMilli(),
        LAST_ACCESSED_TIME,
        session.getLastAccessedTime().toEpochMilli(),
        MAX_INACTIVE_INTERVAL_IN_SECONDS,
        session.getMaxInactiveInterval().getSeconds(),
        ATTRIBUTES,
        attributes);
  }

  private String getSessionIdFrom(final Map<String, Object> document) {
    return (String) document.get(ID);
  }

  private Instant getInstantFor(final Object object) {
    if (object == null) {
      return null;
    }
    if (object instanceof Long) {
      return Instant.ofEpochMilli((Long) object);
    }
    return null;
  }

  private Duration getDurationFor(final Object object) {
    if (object == null) {
      return null;
    }
    if (object instanceof Integer) {
      return Duration.ofSeconds((Integer) object);
    }
    return null;
  }

  private Optional<ElasticsearchSession> documentToSession(Map<String, Object> document) {
    try {
      final String sessionId = getSessionIdFrom(document);
      final ElasticsearchSession session = new ElasticsearchSession(sessionId);
      session.setLastAccessedTime(getInstantFor(document.get(LAST_ACCESSED_TIME)));
      try {
        if (request != null
            && request.getHeader(POLLING_HEADER) != null
            && (!request.getHeader(POLLING_HEADER).equals(true))) {
          session.setPolling(true);
        }
      } catch (Exception e) {
        LOGGER.debug(
            "Expected Exception: is not possible to access request as currently this is not on a request context");
      }
      session.setCreationTime(getInstantFor(document.get(CREATION_TIME)));
      session.setMaxInactiveInterval(
          getDurationFor(document.get(MAX_INACTIVE_INTERVAL_IN_SECONDS)));

      final Object attributesObject = document.get(ATTRIBUTES);
      if (attributesObject != null
          && attributesObject.getClass().isInstance(new HashMap<String, String>())) {
        final Map<String, String> attributes = (Map<String, String>) document.get(ATTRIBUTES);
        attributes
            .keySet()
            .forEach(
                name ->
                    session.setAttribute(
                        name, deserialize(Base64.getDecoder().decode(attributes.get(name)))));
      }
      return Optional.of(session);
    } catch (Exception e) {
      LOGGER.error("Could not restore session.", e);
      return Optional.empty();
    }
  }

  private void executeAsyncElasticsearchRequest(Runnable requestRunnable) {
    taskScheduler.execute(requestRunnable);
  }

  static class ElasticsearchSession implements Session {

    private final MapSession delegate;

    private boolean changed;

    private boolean polling;

    public ElasticsearchSession(String id) {
      delegate = new MapSession(id);
      polling = false;
    }

    boolean isChanged() {
      return changed;
    }

    void clearChangeFlag() {
      changed = false;
    }

    public String getId() {
      return delegate.getId();
    }

    @Override
    public String changeSessionId() {
      final String newId = delegate.changeSessionId();
      changed = true;
      return newId;
    }

    public ElasticsearchSession setId(String id) {
      delegate.setId(id);
      return this;
    }

    public <T> T getAttribute(String attributeName) {
      return delegate.getAttribute(attributeName);
    }

    public Set<String> getAttributeNames() {
      return delegate.getAttributeNames();
    }

    public void setAttribute(String attributeName, Object attributeValue) {
      delegate.setAttribute(attributeName, attributeValue);
      changed = true;
    }

    @Override
    public void removeAttribute(String attributeName) {
      delegate.removeAttribute(attributeName);
      changed = true;
    }

    @Override
    public Instant getCreationTime() {
      return delegate.getCreationTime();
    }

    public void setCreationTime(Instant creationTime) {
      delegate.setCreationTime(creationTime);
      changed = true;
    }

    @Override
    public int hashCode() {
      return Objects.hash(getId());
    }

    @Override
    public void setLastAccessedTime(Instant lastAccessedTime) {
      if (!polling) {
        delegate.setLastAccessedTime(lastAccessedTime);
        changed = true;
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ElasticsearchSession session = (ElasticsearchSession) o;
      return Objects.equals(getId(), session.getId());
    }

    @Override
    public Instant getLastAccessedTime() {
      return delegate.getLastAccessedTime();
    }

    @Override
    public void setMaxInactiveInterval(final Duration interval) {
      delegate.setMaxInactiveInterval(interval);
      changed = true;
    }

    @Override
    public Duration getMaxInactiveInterval() {
      return delegate.getMaxInactiveInterval();
    }

    @Override
    public String toString() {
      return String.format("ElasticsearchSession: %s ", getId());
    }

    @Override
    public boolean isExpired() {
      final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      return delegate.isExpired() || (authentication != null && !authentication.isAuthenticated());
    }

    public boolean isPolling() {
      return polling;
    }

    public ElasticsearchSession setPolling(boolean polling) {
      this.polling = polling;
      return this;
    }
  }
}
