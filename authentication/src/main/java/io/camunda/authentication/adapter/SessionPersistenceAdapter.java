/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import io.camunda.gatekeeper.model.session.SessionData;
import io.camunda.gatekeeper.spi.SessionPersistencePort;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(PersistentWebSessionClient.class)
public final class SessionPersistenceAdapter implements SessionPersistencePort {

  private static final Logger LOG = LoggerFactory.getLogger(SessionPersistenceAdapter.class);

  private final PersistentWebSessionClient client;
  private final GenericConversionService conversionService;

  public SessionPersistenceAdapter(
      final PersistentWebSessionClient client, final GenericConversionService conversionService) {
    this.client = client;
    this.conversionService = conversionService;
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
  }

  @Override
  public Optional<SessionData> findById(final String id) {
    try {
      final var entity = client.getPersistentWebSession(id);
      if (entity == null) {
        return Optional.empty();
      }
      return Optional.of(toSessionData(entity));
    } catch (final Exception e) {
      LOG.error("Failed to find session {}", id, e);
      return Optional.empty();
    }
  }

  @Override
  public void save(final SessionData sessionData) {
    final var entity = toEntity(sessionData);
    client.upsertPersistentWebSession(entity);
  }

  @Override
  public void deleteById(final String id) {
    client.deletePersistentWebSession(id);
  }

  @Override
  public void deleteExpired() {
    final var allSessions = client.getAllPersistentWebSessions();
    if (allSessions != null && allSessions.items() != null) {
      final var now = Instant.now();
      allSessions.items().stream()
          .filter(entity -> isExpired(entity, now))
          .forEach(entity -> client.deletePersistentWebSession(entity.id()));
    }
  }

  private boolean isExpired(final PersistentWebSessionEntity entity, final Instant now) {
    if (entity.lastAccessedTime() == null || entity.maxInactiveIntervalInSeconds() == null) {
      return false;
    }
    final var lastAccess = Instant.ofEpochMilli(entity.lastAccessedTime());
    final var expiryTime = lastAccess.plusSeconds(entity.maxInactiveIntervalInSeconds());
    return now.isAfter(expiryTime);
  }

  private SessionData toSessionData(final PersistentWebSessionEntity entity) {
    final var attributes = deserializeAttributes(entity.attributes());
    return new SessionData(
        entity.id(),
        entity.creationTime() != null ? Instant.ofEpochMilli(entity.creationTime()) : null,
        entity.lastAccessedTime() != null ? Instant.ofEpochMilli(entity.lastAccessedTime()) : null,
        entity.maxInactiveIntervalInSeconds() != null ? entity.maxInactiveIntervalInSeconds() : 0,
        attributes);
  }

  private PersistentWebSessionEntity toEntity(final SessionData sessionData) {
    final var attributes = serializeAttributes(sessionData.attributes());
    return new PersistentWebSessionEntity(
        sessionData.id(),
        sessionData.creationTime() != null ? sessionData.creationTime().toEpochMilli() : null,
        sessionData.lastAccessedTime() != null
            ? sessionData.lastAccessedTime().toEpochMilli()
            : null,
        sessionData.maxInactiveIntervalInSeconds(),
        attributes);
  }

  private Map<String, byte[]> serializeAttributes(final Map<String, Object> attributes) {
    if (attributes == null) {
      return new HashMap<>();
    }
    return attributes.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e ->
                    (byte[])
                        conversionService.convert(
                            e.getValue(),
                            TypeDescriptor.valueOf(Object.class),
                            TypeDescriptor.valueOf(byte[].class))));
  }

  private Map<String, Object> deserializeAttributes(final Map<String, byte[]> attributes) {
    if (attributes == null) {
      return new HashMap<>();
    }
    return attributes.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e ->
                    conversionService.convert(
                        e.getValue(),
                        TypeDescriptor.valueOf(byte[].class),
                        TypeDescriptor.valueOf(Object.class))));
  }
}
