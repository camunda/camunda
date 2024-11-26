/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import io.camunda.search.entities.PersistentWebSessionEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

public class WebSessionMapper {

  private final WebSessionAttributeConverter converter;

  public WebSessionMapper(final WebSessionAttributeConverter converter) {
    this.converter = converter;
  }

  public PersistentWebSessionEntity toPersistentWebSession(final WebSession session) {
    final var attributes = serializeSessionAttributes(session);
    return new PersistentWebSessionEntity(
        session.getId(),
        session.getCreationTime().toEpochMilli(),
        session.getLastAccessedTime().toEpochMilli(),
        session.getMaxInactiveInterval().getSeconds(),
        attributes);
  }

  public WebSession fromPersistentWebSession(final PersistentWebSessionEntity entity) {
    final var sessionId = entity.id();
    final var creationTime = entity.creationTime();
    final var lastAccessedTime = entity.lastAccessedTime();
    final var maxInactiveIntervalInSeconds = entity.maxInactiveIntervalInSeconds();
    final var attributes = deserializeSessionAttributes(entity);

    final var webSession = new WebSession(sessionId);
    webSession.setCreationTime(toInstant(creationTime));
    webSession.setLastAccessedTime(toInstant(lastAccessedTime));
    webSession.setMaxInactiveInterval(toDuration(maxInactiveIntervalInSeconds));
    attributes.forEach(webSession::setAttribute);
    return webSession;
  }

  private Map<String, byte[]> serializeSessionAttributes(final WebSession session) {
    final var serializedAttributes = new HashMap<String, byte[]>();
    final var attributeNames = session.getAttributeNames();
    attributeNames.forEach(
        a -> serializedAttributes.put(a, converter.serialize(session.getAttribute(a))));
    return serializedAttributes;
  }

  private Map<String, Object> deserializeSessionAttributes(
      final PersistentWebSessionEntity entity) {
    return Optional.ofNullable(entity.attributes()).orElse(new HashMap<>()).entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> converter.deserialize(e.getValue())));
  }

  private Instant toInstant(final Long object) {
    return Optional.ofNullable(object).map(Instant::ofEpochMilli).orElse(null);
  }

  private Duration toDuration(final Long value) {
    return Optional.ofNullable(value).map(Duration::ofSeconds).orElse(null);
  }

  public static final class SpringBasedWebSessionAttributeConverter
      implements WebSessionAttributeConverter {

    private final GenericConversionService conversionService;

    public SpringBasedWebSessionAttributeConverter(
        final GenericConversionService conversionService) {
      this.conversionService = conversionService;
      conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
      conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
    }

    @Override
    public Object deserialize(final byte[] value) {
      return conversionService.convert(
          value, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
    }

    @Override
    public byte[] serialize(final Object value) {
      return (byte[])
          conversionService.convert(
              value, TypeDescriptor.valueOf(Object.class), TypeDescriptor.valueOf(byte[].class));
    }
  }
}
