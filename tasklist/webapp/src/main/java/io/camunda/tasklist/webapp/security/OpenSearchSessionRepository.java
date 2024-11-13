/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.schema.v86.indices.TasklistWebSessionIndex.ATTRIBUTES;
import static io.camunda.tasklist.schema.v86.indices.TasklistWebSessionIndex.CREATION_TIME;
import static io.camunda.tasklist.schema.v86.indices.TasklistWebSessionIndex.ID;
import static io.camunda.tasklist.schema.v86.indices.TasklistWebSessionIndex.LAST_ACCESSED_TIME;
import static io.camunda.tasklist.schema.v86.indices.TasklistWebSessionIndex.MAX_INACTIVE_INTERVAL_IN_SECONDS;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.schema.v86.indices.TasklistWebSessionIndex;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
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
@Conditional(OpenSearchCondition.class)
public class OpenSearchSessionRepository
    implements SessionRepository<OpenSearchSessionRepository.OpenSearchSession> {

  public static final int DELETE_EXPIRED_SESSIONS_DELAY = 1_000 * 60 * 30; // min
  public static final String POLLING_HEADER = "x-is-polling";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchSessionRepository.class);

  @Autowired
  @Qualifier("sessionThreadPoolScheduler")
  public ThreadPoolTaskScheduler taskScheduler;

  @Autowired private RetryOpenSearchClient retryOpenSearchClient;

  @Autowired private GenericConversionService conversionService;

  @Autowired private TasklistWebSessionIndex tasklistWebSessionIndex;

  @Autowired private HttpServletRequest request;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @PostConstruct
  private void setUp() {
    LOGGER.debug("Persistent sessions in OpenSearch enabled");
    setupConverter();
    startExpiredSessionCheck();
  }

  @PreDestroy
  private void tearDown() {
    LOGGER.debug("Shutdown OpenSearchSessionRepository");
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
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest.index(tasklistWebSessionIndex.getFullQualifiedName());
    retryOpenSearchClient.doWithEachSearchResult(
        searchRequest,
        sh -> {
          final Map<String, Object> document =
              objectMapper.convertValue(sh.source(), new TypeReference<Map<String, Object>>() {});
          final Optional<OpenSearchSession> maybeSession = documentToSession(document);
          if (maybeSession.isPresent()) {
            final Session session = maybeSession.get();
            LOGGER.debug("Check if session {} is expired: {}", session, session.isExpired());
            if (session.isExpired()) {
              deleteById(session.getId());
            }
          } else {
            // need to delete entry in OpenSearch in case of failing restore session
            deleteById(getSessionIdFrom(document));
          }
        });
  }

  private boolean shouldDeleteSession(final OpenSearchSession session) {
    return session.isExpired() || (session.containsAuthentication() && !session.isAuthenticated());
  }

  @Override
  public OpenSearchSession createSession() {
    // Frontend e2e tests are relying on this pattern
    final String sessionId = UUID.randomUUID().toString().replace("-", "");

    final OpenSearchSession session = new OpenSearchSession(sessionId);
    LOGGER.debug(
        "Create session {} with maxInactiveInterval {} s",
        session,
        session.getMaxInactiveInterval());
    return session;
  }

  @Override
  public void save(final OpenSearchSession session) {
    LOGGER.debug("Save session {}", session);
    if (shouldDeleteSession(session)) {
      deleteById(session.getId());
      return;
    }
    if (session.isChanged()) {
      LOGGER.debug("Session {} changed, save in OpenSearch.", session);
      retryOpenSearchClient.createOrUpdateDocument(
          tasklistWebSessionIndex.getFullQualifiedName(),
          session.getId(),
          sessionToDocument(session));
      session.clearChangeFlag();
    }
  }

  @Override
  public OpenSearchSession findById(final String id) {
    LOGGER.debug("Retrieve session {} from OpenSearch", id);
    Map<String, Object> document;
    try {
      document =
          retryOpenSearchClient.getDocument(tasklistWebSessionIndex.getFullQualifiedName(), id);
    } catch (final Exception e) {
      document = null;
    }
    if (document == null) {
      return null;
    }

    final Optional<OpenSearchSession> maybeSession = documentToSession(document);
    if (maybeSession.isEmpty()) {
      // need to delete entry in OpenSearch in case of failing restore session
      deleteById(getSessionIdFrom(document));
      return null;
    }

    final OpenSearchSession session = maybeSession.get();
    if (shouldDeleteSession(session)) {
      deleteById(session.getId());
      return null;
    } else {
      return session;
    }
  }

  @Override
  public void deleteById(final String id) {
    LOGGER.debug("Delete session {}", id);
    executeAsyncOpenSearchRequest(
        () ->
            retryOpenSearchClient.deleteDocument(
                tasklistWebSessionIndex.getFullQualifiedName(), id));
  }

  private byte[] serialize(final Object object) {
    return (byte[])
        conversionService.convert(
            object, TypeDescriptor.valueOf(Object.class), TypeDescriptor.valueOf(byte[].class));
  }

  private Object deserialize(final byte[] bytes) {
    return conversionService.convert(
        bytes, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
  }

  private Map<String, Object> sessionToDocument(final OpenSearchSession session) {
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

  private Optional<OpenSearchSession> documentToSession(final Map<String, Object> document) {
    try {
      final String sessionId = getSessionIdFrom(document);
      final OpenSearchSession session = new OpenSearchSession(sessionId);
      session.setLastAccessedTime(getInstantFor(document.get(LAST_ACCESSED_TIME)));
      try {
        if (request != null
            && request.getHeader(POLLING_HEADER) != null
            && (!request.getHeader(POLLING_HEADER).equals(true))) {
          session.setPolling(true);
        }
      } catch (final Exception e) {
        LOGGER.debug(
            "Expected Exception: is not possible to access request as currently this is not on a request context");
      }
      session.setCreationTime(getInstantFor(document.get(CREATION_TIME)));
      session.setMaxInactiveInterval(
          getDurationFor(document.get(MAX_INACTIVE_INTERVAL_IN_SECONDS)));

      final Object attributesObject = document.get(ATTRIBUTES);
      if (attributesObject != null
          && attributesObject.getClass().isInstance(new LinkedHashMap<>())) {
        final Map<String, String> attributes = (Map<String, String>) document.get(ATTRIBUTES);
        attributes
            .keySet()
            .forEach(
                name ->
                    session.setAttribute(
                        name, deserialize(Base64.getDecoder().decode(attributes.get(name)))));
      }
      return Optional.of(session);
    } catch (final Exception e) {
      LOGGER.error("Could not restore session.", e);
      return Optional.empty();
    }
  }

  private void executeAsyncOpenSearchRequest(final Runnable requestRunnable) {
    taskScheduler.execute(requestRunnable);
  }

  static class OpenSearchSession implements Session {

    private final MapSession delegate;

    private boolean changed;

    private boolean polling;

    public OpenSearchSession(final String id) {
      delegate = new MapSession(id);
      polling = false;
    }

    boolean isChanged() {
      return changed;
    }

    void clearChangeFlag() {
      changed = false;
    }

    @Override
    public String getId() {
      return delegate.getId();
    }

    @Override
    public String changeSessionId() {
      final String newId = delegate.changeSessionId();
      changed = true;
      return newId;
    }

    @Override
    public <T> T getAttribute(final String attributeName) {
      return delegate.getAttribute(attributeName);
    }

    @Override
    public Set<String> getAttributeNames() {
      return delegate.getAttributeNames();
    }

    @Override
    public void setAttribute(final String attributeName, final Object attributeValue) {
      delegate.setAttribute(attributeName, attributeValue);
      changed = true;
    }

    @Override
    public void removeAttribute(final String attributeName) {
      delegate.removeAttribute(attributeName);
      changed = true;
    }

    @Override
    public Instant getCreationTime() {
      return delegate.getCreationTime();
    }

    public void setCreationTime(final Instant creationTime) {
      delegate.setCreationTime(creationTime);
      changed = true;
    }

    public OpenSearchSession setId(final String id) {
      delegate.setId(id);
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(getId());
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final OpenSearchSession session = (OpenSearchSession) o;
      return Objects.equals(getId(), session.getId());
    }

    @Override
    public String toString() {
      return String.format("OpenSearchSession: %s ", getId());
    }

    @Override
    public void setLastAccessedTime(final Instant lastAccessedTime) {
      if (!polling) {
        delegate.setLastAccessedTime(lastAccessedTime);
        changed = true;
      }
    }

    public boolean containsAuthentication() {
      return getAuthentication() != null;
    }

    public boolean isAuthenticated() {
      final var authentication = getAuthentication();
      try {
        return authentication != null && authentication.isAuthenticated();
      } catch (final InsufficientAuthenticationException ex) {
        // TODO consider not throwing exceptions in authentication.isAuthenticated()
        return false;
      }
    }

    private Authentication getAuthentication() {
      final var securityContext =
          (SecurityContext) delegate.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
      final Authentication authentication;

      if (securityContext != null) {
        authentication = securityContext.getAuthentication();
      } else {
        authentication = null;
      }

      return authentication;
    }

    @Override
    public Instant getLastAccessedTime() {
      return delegate.getLastAccessedTime();
    }

    public boolean isPolling() {
      return polling;
    }

    public OpenSearchSession setPolling(final boolean polling) {
      this.polling = polling;
      return this;
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
    public boolean isExpired() {
      return delegate.isExpired();
    }
  }
}
