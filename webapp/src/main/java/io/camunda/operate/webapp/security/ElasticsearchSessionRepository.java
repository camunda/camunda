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

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.elasticsearch.action.search.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.stereotype.Component;

@Configuration
@ConditionalOnExpression(
    "${camunda.operate.persistent.sessions.enabled:false}"
        + " or " +
    "${camunda.operate.persistentSessionsEnabled:false}"
)
@Conditional(ElasticsearchCondition.class)
@Component
@EnableSpringHttpSession
public class ElasticsearchSessionRepository implements SessionRepository<OperateSession> {

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
      final Optional<OperateSession> maybeSession = documentToSession(document);
      if(maybeSession.isPresent()) {
        final OperateSession session = maybeSession.get();
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
      retryElasticsearchClient.createOrUpdateDocument(
          operateWebSessionIndex.getFullQualifiedName(),
          session.getId(),
          sessionToDocument(session)
      );
      session.clearChangeFlag();
    }
  }

  @Override
  public OperateSession findById(final String id) {
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

    final Optional<OperateSession> maybeSession = documentToSession(document);
    if (maybeSession.isEmpty() ) {
      // need to delete entry in Elasticsearch in case of failing restore session
      deleteById(getSessionIdFrom(document));
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
    executeAsyncElasticsearchRequest(() -> retryElasticsearchClient.deleteDocument(operateWebSessionIndex.getFullQualifiedName(), id));
  }

  private byte[] serialize(Object object) {
    return (byte[]) conversionService.convert(object, TypeDescriptor.valueOf(Object.class), TypeDescriptor.valueOf(byte[].class));
  }

  private Object deserialize(byte[] bytes) {
    return conversionService.convert(bytes, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
  }

  private Map<String, Object> sessionToDocument(OperateSession session) {
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

  private Optional<OperateSession> documentToSession(Map<String, Object> document) {
    try {
      final String sessionId = getSessionIdFrom(document);
      OperateSession session = new OperateSession(sessionId);
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

}
