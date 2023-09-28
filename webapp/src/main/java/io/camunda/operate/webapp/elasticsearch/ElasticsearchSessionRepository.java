/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.webapp.security.OperateSession;
import io.camunda.operate.webapp.security.SessionRepository;
import jakarta.annotation.PostConstruct;
import org.elasticsearch.action.search.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.camunda.operate.schema.indices.OperateWebSessionIndex.ATTRIBUTES;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.CREATION_TIME;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.ID;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.LAST_ACCESSED_TIME;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.MAX_INACTIVE_INTERVAL_IN_SECONDS;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchSessionRepository implements SessionRepository {
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSessionRepository.class);

  @Autowired
  private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired
  private GenericConversionService conversionService;

  @Autowired
  private OperateWebSessionIndex operateWebSessionIndex;

  @PostConstruct
  private void setUp() {
    setupConverter();
  }

  private void setupConverter() {
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
  }

  @Override
  public List<String> getExpiredSessionIds() {
    SearchRequest searchRequest = new SearchRequest(operateWebSessionIndex.getFullQualifiedName());
    List<String> result = new ArrayList<>();

    retryElasticsearchClient.doWithEachSearchResult(searchRequest, sh -> {
      final Map<String, Object> document = sh.getSourceAsMap();
      final Optional<OperateSession> maybeSession = documentToSession(document);
      if(maybeSession.isPresent()) {
        final OperateSession session = maybeSession.get();
        logger.debug("Check if session {} is expired: {}", session, session.isExpired());
        if (session.isExpired()) {
          result.add(session.getId());
        }
      } else {
        // need to delete entry in Elasticsearch in case of failing restore session
        result.add(getSessionIdFrom(document));
      }
    });

    return result;
  }

  @Override
  public void save(OperateSession session) {
    retryElasticsearchClient.createOrUpdateDocument(
      operateWebSessionIndex.getFullQualifiedName(),
      session.getId(),
      sessionToDocument(session)
    );
  }

  @Override
  public Optional<OperateSession> findById(final String id) {
    try {
      Optional<Map<String, Object>> maybeDocument = Optional.ofNullable(retryElasticsearchClient.getDocument(operateWebSessionIndex.getFullQualifiedName(), id));
      return maybeDocument.flatMap(this::documentToSession);
    } catch(Exception e){
      return Optional.empty();
    }
  }

  @Override
  public void deleteById(String id) {
    retryElasticsearchClient.deleteDocument(operateWebSessionIndex.getFullQualifiedName(), id);
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
}
