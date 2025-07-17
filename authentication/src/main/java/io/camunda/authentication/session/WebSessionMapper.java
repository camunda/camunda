/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.entities.PersistentWebSessionEntity;
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
  private final ObjectMapper objectMapper;

  public WebSessionMapper(
      final WebSessionAttributeConverter converter, final ObjectMapper objectMapper) {
    this.converter = converter;
    this.objectMapper = objectMapper;
  }

  public PersistentWebSessionEntity toPersistentWebSession(final WebSession webSession) {
    final var attributes = serializeSessionAttributes(webSession);
    try {
      return new PersistentWebSessionEntity(
          webSession.getId(),
          webSession.getCreationTime().toEpochMilli(),
          webSession.getLastAccessedTime().toEpochMilli(),
          webSession.getMaxInactiveInterval().getSeconds(),
          objectMapper.writeValueAsString(attributes));
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(
          "Could not serialize web session attributes for web session " + webSession.getId());
    }
  }

  public WebSession fromPersistentWebSession(
      final PersistentWebSessionEntity persistentWebSessionEntity) {
    try {
      final var sessionId = persistentWebSessionEntity.id();
      final var creationTime = persistentWebSessionEntity.creationTime();
      final var lastAccessedTime = persistentWebSessionEntity.lastAccessedTime();
      final var maxInactiveIntervalInSeconds =
          persistentWebSessionEntity.maxInactiveIntervalInSeconds();
      final var attributes = deserializeSessionAttributes(persistentWebSessionEntity);

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

  private Map<String, Object> deserializeSessionAttributes(
      final PersistentWebSessionEntity webSessionEntity) {
    try {
      final var attributes =
          parseJsonAttributes(webSessionEntity.attributesAsJson()).orElseGet(HashMap::new);
      return attributes.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> converter.deserialize(e.getValue())));
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(
          "Could not deserialize session attributes for web session " + webSessionEntity.id());
    }
  }

  private Optional<Map<String, byte[]>> parseJsonAttributes(final String attributesAsJson)
      throws JsonProcessingException {
    if (attributesAsJson == null || attributesAsJson.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.readValue(attributesAsJson, new TypeReference<>() {}));
  }

  private Instant toInstant(final Long value) {
    return Optional.ofNullable(value).map(Instant::ofEpochMilli).orElse(null);
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
