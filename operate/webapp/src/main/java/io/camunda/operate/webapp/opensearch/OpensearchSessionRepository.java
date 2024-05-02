/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static java.util.function.UnaryOperator.identity;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.security.OperateSession;
import io.camunda.operate.webapp.security.SessionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchSessionRepository implements SessionRepository {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchSessionRepository.class);

  private final RichOpenSearchClient richOpenSearchClient;

  private final GenericConversionService conversionService;

  private final OperateWebSessionIndex operateWebSessionIndex;

  private final HttpServletRequest request;

  public OpensearchSessionRepository(
      final RichOpenSearchClient richOpenSearchClient,
      final GenericConversionService conversionService,
      final OperateWebSessionIndex operateWebSessionIndex,
      final HttpServletRequest request) {
    this.richOpenSearchClient = richOpenSearchClient;
    this.conversionService = conversionService;
    this.operateWebSessionIndex = operateWebSessionIndex;
    this.request = request;
  }

  @PostConstruct
  private void setUp() {
    setupConverter();
  }

  private void setupConverter() {
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
  }

  private SessionEntity toSessionEntity(final OperateSession session) {
    final Map<String, String> attributes =
        session.getAttributeNames().stream()
            .collect(Collectors.toMap(identity(), name -> serialize(session.getAttribute(name))));

    return new SessionEntity(
        session.getId(),
        session.getCreationTime().toEpochMilli(),
        session.getLastAccessedTime().toEpochMilli(),
        session.getMaxInactiveInterval().getSeconds(),
        attributes,
        session.isPolling());
  }

  private String serialize(final Object object) {
    return new String(
        Base64.getEncoder()
            .encode(
                (byte[])
                    conversionService.convert(
                        object,
                        TypeDescriptor.valueOf(Object.class),
                        TypeDescriptor.valueOf(byte[].class))));
  }

  private OperateSession toOperateSession(final SessionEntity sessionEntity) {
    final OperateSession session = new OperateSession(sessionEntity.id());
    session.setCreationTime(nullable(sessionEntity.creationTime, Instant::ofEpochMilli));
    session.setLastAccessedTime(nullable(sessionEntity.lastAccessedTime, Instant::ofEpochMilli));
    session.setMaxInactiveInterval(
        nullable(sessionEntity.maxInactiveIntervalInSeconds, Duration::ofSeconds));

    if (sessionEntity.attributes() != null) {
      sessionEntity
          .attributes()
          .forEach((key, value) -> session.setAttribute(key, deserialize(value)));
    }

    try {
      if (request != null && request.getHeader(POLLING_HEADER) != null) {
        session.setPolling(true);
      }
    } catch (final Exception e) {
      LOGGER.debug(
          "Expected Exception: is not possible to access request as currently this is not on a request context");
    }
    return session;
  }

  private Object deserialize(final String s) {
    final byte[] bytes = Base64.getDecoder().decode(s.getBytes());
    return conversionService.convert(
        bytes, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
  }

  private <A, R> R nullable(final A a, final Function<A, R> f) {
    return a == null ? null : f.apply(a);
  }

  @Override
  public List<String> getExpiredSessionIds() {
    return richOpenSearchClient
        .doc()
        .scrollValues(
            searchRequestBuilder(operateWebSessionIndex.getFullQualifiedName()),
            SessionEntity.class)
        .stream()
        .map(this::toOperateSession)
        .filter(OperateSession::isExpired)
        .map(OperateSession::getId)
        .toList();
  }

  @Override
  public void save(final OperateSession session) {
    final var requestBuilder =
        indexRequestBuilder(operateWebSessionIndex.getFullQualifiedName())
            .id(session.getId())
            .document(toSessionEntity(session));

    richOpenSearchClient.doc().indexWithRetries(requestBuilder);
  }

  @Override
  public Optional<OperateSession> findById(final String id) {
    try {
      return richOpenSearchClient
          .doc()
          .getWithRetries(operateWebSessionIndex.getFullQualifiedName(), id, SessionEntity.class)
          .map(this::toOperateSession);
    } catch (final Exception e) {
      return Optional.empty();
    }
  }

  @Override
  public void deleteById(final String id) {
    richOpenSearchClient.doc().deleteWithRetries(operateWebSessionIndex.getFullQualifiedName(), id);
  }

  private record SessionEntity(
      String id,
      Long creationTime,
      Long lastAccessedTime,
      Long maxInactiveIntervalInSeconds,
      Map<String, String> attributes,
      Boolean polling) {}
}
