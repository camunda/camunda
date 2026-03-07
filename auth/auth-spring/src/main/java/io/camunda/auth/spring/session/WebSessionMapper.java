/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.session;

import io.camunda.auth.domain.model.SessionData;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

public class WebSessionMapper {

  public static final Logger LOGGER = LoggerFactory.getLogger(WebSessionMapper.class);
  private final WebSessionAttributeConverter converter;

  public WebSessionMapper(final WebSessionAttributeConverter converter) {
    this.converter = converter;
  }

  public SessionData toSessionData(final WebSession webSession) {
    final var attributes = serializeSessionAttributes(webSession);
    return new SessionData(
        webSession.getId(),
        webSession.getCreationTime().toEpochMilli(),
        webSession.getLastAccessedTime().toEpochMilli(),
        webSession.getMaxInactiveInterval().getSeconds(),
        attributes);
  }

  public WebSession fromSessionData(final SessionData sessionData) {
    try {
      final var sessionId = sessionData.id();
      final var creationTime = sessionData.creationTime();
      final var lastAccessedTime = sessionData.lastAccessedTime();
      final var maxInactiveIntervalInSeconds = sessionData.maxInactiveIntervalInSeconds();
      final var attributes = deserializeSessionAttributes(sessionData);

      final var webSession = new WebSession(sessionId);
      webSession.setCreationTime(toInstant(creationTime));
      webSession.setLastAccessedTime(toInstant(lastAccessedTime));
      webSession.setMaxInactiveInterval(toDuration(maxInactiveIntervalInSeconds));
      attributes.forEach(webSession::setAttribute);
      return webSession;
    } catch (final Exception e) {
      LOGGER.error("The persistent session could not be restored.", e);
      return null;
    }
  }

  private Map<String, byte[]> serializeSessionAttributes(final WebSession webSession) {
    final var serializedAttributes = new HashMap<String, byte[]>();
    final var attributeNames = webSession.getAttributeNames();
    attributeNames.forEach(
        a -> serializedAttributes.put(a, converter.serialize(webSession.getAttribute(a))));
    return serializedAttributes;
  }

  private Map<String, Object> deserializeSessionAttributes(final SessionData sessionData) {
    return Optional.ofNullable(sessionData.attributes()).orElse(new HashMap<>()).entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> converter.deserialize(e.getValue())));
  }

  private Instant toInstant(final long value) {
    return Instant.ofEpochMilli(value);
  }

  private Duration toDuration(final long value) {
    return Duration.ofSeconds(value);
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
