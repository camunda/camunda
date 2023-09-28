/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.security.OperateSession;
import io.camunda.operate.webapp.security.SessionRepository;
import jakarta.annotation.PostConstruct;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static java.util.function.UnaryOperator.identity;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchSessionRepository implements SessionRepository {
  private static final Logger logger = LoggerFactory.getLogger(OpensearchSessionRepository.class);

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private GenericConversionService conversionService;

  @Autowired
  private OperateWebSessionIndex operateWebSessionIndex;

  private record SessionEntity(
    String id,
    Long creationTime,
    Long lastAccessedTime,
    Long maxInactiveIntervalInSeconds,
    Map<String, String> attributes
  ){}

  @PostConstruct
  private void setUp() {
    setupConverter();
  }

  private void setupConverter() {
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
  }

  private SessionEntity toSessionEntity(OperateSession session) {
    Map<String, String> attributes = session.getAttributeNames().stream().collect(Collectors.toMap(
      identity(),
      name -> serialize(session.getAttribute(name))
    ));

    return new SessionEntity(
      session.getId(),
      session.getCreationTime().toEpochMilli(),
      session.getLastAccessedTime().toEpochMilli(),
      session.getMaxInactiveInterval().getSeconds(),
      attributes
    );
  }

  private String serialize(Object object) {
    return new String(
      Base64.getEncoder().encode(
        (byte[]) conversionService.convert(object, TypeDescriptor.valueOf(Object.class), TypeDescriptor.valueOf(byte[].class))
      )
    );
  }

  private OperateSession toOperateSession(SessionEntity sessionEntity) {
    OperateSession session = new OperateSession(sessionEntity.id());
    session.setCreationTime(nullable(sessionEntity.creationTime, Instant::ofEpochMilli));
    session.setLastAccessedTime(nullable(sessionEntity.lastAccessedTime, Instant::ofEpochMilli));
    session.setMaxInactiveInterval(nullable(sessionEntity.maxInactiveIntervalInSeconds, Duration::ofSeconds));

    if (sessionEntity.attributes() != null) {
      sessionEntity.attributes().forEach((key, value) -> session.setAttribute(key, deserialize(value)));
    }

    return session;
  }

  private Object deserialize(String s) {
    byte[] bytes = Base64.getDecoder().decode(s.getBytes());
    return conversionService.convert(bytes, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
  }

  private <A, R> R nullable(final A a, Function<A, R> f) {
    return a == null ? null : f.apply(a);
  }

  @Override
  public List<String> getExpiredSessionIds() {
    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder(operateWebSessionIndex.getFullQualifiedName()), SessionEntity.class)
      .stream()
      .map(this::toOperateSession)
      .filter(OperateSession::isExpired)
      .map(OperateSession::getId)
      .toList();
  }

  @Override
  public void save(OperateSession session) {
    var requestBuilder = indexRequestBuilder(operateWebSessionIndex.getFullQualifiedName())
      .id(session.getId())
      .document(toSessionEntity(session));

    richOpenSearchClient.doc().indexWithRetries(requestBuilder);
  }

  @Override
  public Optional<OperateSession> findById(final String id) {
    try {
      return richOpenSearchClient.doc().getWithRetries(operateWebSessionIndex.getFullQualifiedName(), id, SessionEntity.class)
        .map(this::toOperateSession);
    } catch(Exception e){
      return Optional.empty();
    }
  }

  @Override
  public void deleteById(String id) {
    richOpenSearchClient.doc().deleteWithRetries(operateWebSessionIndex.getFullQualifiedName(), id);
  }
}
