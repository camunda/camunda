/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.schema.indices.OperateWebSessionIndex.ATTRIBUTES;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.CREATION_TIME;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.ID;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.LAST_ACCESSED_TIME;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.MAX_INACTIVE_INTERVAL_IN_SECONDS;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.elasticsearch.action.search.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.stereotype.Component;

@Configuration
@ConditionalOnExpression(
    "${camunda.operate.persistent.sessions.enabled:false}"
        + " or " +
    "${camunda.operate.persistentSessionsEnabled:false}"
)
@Component
@EnableSpringHttpSession
public class ElasticsearchSessionRepository implements SessionRepository<ElasticsearchSessionRepository.ElasticsearchSession> {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSessionRepository.class);

  public static final int DELETE_EXPIRED_SESSIONS_DELAY = 1_000 * 60 * 30; // min

  @Autowired
  private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired
  private GenericConversionService conversionService;

  @Autowired
  private OperateWebSessionIndex operateWebSessionIndex;

  @Autowired
  @Qualifier("sessionThreadPoolScheduler")
  private ThreadPoolTaskScheduler sessionThreadScheduler;

  @PostConstruct
  private void setUp() {
    logger.debug("Persistent sessions in Elasticsearch enabled");
    setupConverter();
    startExpiredSessionCheck();
  }

  @PreDestroy
  private void tearDown() {
    logger.debug("Shutdown ElasticsearchSessionRepository");
  }

  private void setupConverter() {
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
  }

  private void startExpiredSessionCheck() {
    sessionThreadScheduler.scheduleAtFixedRate(this::removedExpiredSessions, DELETE_EXPIRED_SESSIONS_DELAY);
  }

  private void removedExpiredSessions() {
    logger.debug("Check for expired sessions");
    SearchRequest searchRequest = new SearchRequest(operateWebSessionIndex.getFullQualifiedName());
    retryElasticsearchClient.doWithEachSearchResult(searchRequest, sh -> {
      final Map<String, Object> document = sh.getSourceAsMap();
      final Optional<ElasticsearchSession> maybeSession = documentToSession(document);
      if(maybeSession.isPresent()) {
        final ElasticsearchSession session = maybeSession.get();
        logger.debug("Check if session {} is expired: {}", session, session.isExpired());
        if (session.isExpired()) {
          deleteById(session.getId());
        }
      } else {
        // need to delete entry in Elasticsearch in case of failing restore session
        deleteById(getSessionIdFrom(document));
      }
    });
  }

  private boolean shouldDeleteSession(final ElasticsearchSession session) {
    return session.isExpired() || (session.containsAuthentication() && !session.isAuthenticated());
  }

  @Override
  public ElasticsearchSession createSession() {
    // Frontend e2e tests are relying on this pattern
    final String sessionId = UUID.randomUUID().toString().replace("-","");

    ElasticsearchSession session = new ElasticsearchSession(sessionId);
    logger.debug("Create session {} with maxInactiveInterval {} ", session, session.getMaxInactiveInterval());
    return session;
  }

  @Override
  public void save(ElasticsearchSession session) {
    logger.debug("Save session {}", session);
    if (shouldDeleteSession(session)) {
      deleteById(session.getId());
      return;
    }
    if (session.isChanged()) {
      logger.debug("Session {} changed, save in Elasticsearch.", session);
      retryElasticsearchClient.createOrUpdateDocument(
          operateWebSessionIndex.getFullQualifiedName(),
          session.getId(),
          sessionToDocument(session)
      );
      session.clearChangeFlag();
    }
  }

  @Override
  public ElasticsearchSession findById(final String id) {
    logger.debug("Retrieve session {} from Elasticsearch", id);
    Map<String, Object> document;
    try {
      document = retryElasticsearchClient.getDocument(operateWebSessionIndex.getFullQualifiedName(),
          id);
    }catch(Exception e){
      document = null;
    }
    if (document == null) {
      return null;
    }

    final Optional<ElasticsearchSession> maybeSession = documentToSession(document);
    if (maybeSession.isEmpty() ) {
      // need to delete entry in Elasticsearch in case of failing restore session
      deleteById(getSessionIdFrom(document));
      return null;
    }

    final ElasticsearchSession session = maybeSession.get();
    if (shouldDeleteSession(session)) {
      deleteById(session.getId());
      return null;
    } else {
      return session;
    }
  }

  @Override
  public void deleteById(String id) {
    executeAsyncElasticsearchRequest(() -> retryElasticsearchClient.deleteDocument(operateWebSessionIndex.getFullQualifiedName(), id));
  }

  private byte[] serialize(Object object) {
    return (byte[]) conversionService.convert(object, TypeDescriptor.valueOf(Object.class), TypeDescriptor.valueOf(byte[].class));
  }

  private Object deserialize(byte[] bytes) {
    return conversionService.convert(bytes, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
  }

  private Map<String, Object> sessionToDocument(ElasticsearchSession session) {
    Map<String, byte[]> attributes = new HashMap<>();
    session.getAttributeNames().forEach(name -> attributes.put(name, serialize(session.getAttribute(name))));
    return Map.of(
        ID, session.getId(),
        CREATION_TIME, session.getCreationTime().toEpochMilli(),
        LAST_ACCESSED_TIME, session.getLastAccessedTime().toEpochMilli(),
        MAX_INACTIVE_INTERVAL_IN_SECONDS, session.getMaxInactiveInterval().getSeconds(),
        ATTRIBUTES, attributes
    );
  }

  private String getSessionIdFrom(final Map<String, Object> document){
    return (String) document.get(ID);
  }

  private Optional<ElasticsearchSession> documentToSession(Map<String, Object> document) {
    try {
      final String sessionId = getSessionIdFrom(document);
      ElasticsearchSession session = new ElasticsearchSession(sessionId);
      session.setCreationTime(getInstantFor(document.get(CREATION_TIME)));
      session.setLastAccessedTime(getInstantFor(document.get(LAST_ACCESSED_TIME)));
      session.setMaxInactiveInterval(getDurationFor(document.get(MAX_INACTIVE_INTERVAL_IN_SECONDS)));

      Object attributesObject = document.get(ATTRIBUTES);
      if (attributesObject != null && attributesObject.getClass()
          .isInstance(new HashMap<String, String>())) {
        Map<String, String> attributes = (Map<String, String>) document.get(ATTRIBUTES);
        attributes.keySet().forEach(name -> session.setAttribute(name,
            deserialize(Base64.getDecoder().decode(attributes.get(name)))));
      }
      return Optional.of(session);
    } catch (Exception e) {
      logger.error("Could not restore session.", e);
      return Optional.empty();
    }
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

  private void executeAsyncElasticsearchRequest(Runnable requestRunnable) {
    sessionThreadScheduler.execute(requestRunnable);
  }

  static class ElasticsearchSession implements Session {

    private final MapSession delegate;

    private boolean changed;

    public ElasticsearchSession(String id) {
      delegate = new MapSession(id);
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

    public void removeAttribute(String attributeName) {
      delegate.removeAttribute(attributeName);
      changed = true;
    }

    public Instant getCreationTime() {
      return delegate.getCreationTime();
    }

    public void setCreationTime(final Instant creationTime) {
      delegate.setCreationTime(creationTime);
      changed = true;
    }

    public void setLastAccessedTime(final Instant lastAccessedTime) {
      delegate.setLastAccessedTime(lastAccessedTime);
      changed = true;
    }

    public Instant getLastAccessedTime() {
      return delegate.getLastAccessedTime();
    }

    @Override
    public void setMaxInactiveInterval(final Duration interval) {
      delegate.setMaxInactiveInterval(interval);
      changed = true;
    }

    public Duration getMaxInactiveInterval() {
      return delegate.getMaxInactiveInterval();
    }

    public boolean isExpired() {
      return delegate.isExpired();
    }

    public boolean containsAuthentication() {
      return getAuthentication() != null;
    }

    public boolean isAuthenticated() {
      final var authentication = getAuthentication();
      return (authentication != null && authentication.isAuthenticated());
    }

    private  Authentication getAuthentication() {
      final var securityContext = (SecurityContext) delegate.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
      final Authentication authentication;

      if (securityContext != null) {
        authentication = securityContext.getAuthentication();
      } else {
        authentication = null;
      }

      return authentication;
    }

    @Override
    public String changeSessionId() {
      final String newId = delegate.changeSessionId();
      changed = true;
      return newId;
    }

    @Override
    public String toString() {
      return String.format("ElasticsearchSession: %s ", getId());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ElasticsearchSession session = (ElasticsearchSession) o;
      return Objects.equals(getId(), session.getId());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getId());
    }
  }
}
