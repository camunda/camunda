/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.session;

import io.camunda.gatekeeper.model.session.SessionData;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

/**
 * Maps between {@link WebSession} and the domain {@link SessionData} record, using a {@link
 * WebSessionAttributeConverter} for attribute serialization.
 */
public class WebSessionMapper {

  private static final Logger LOG = LoggerFactory.getLogger(WebSessionMapper.class);
  private final WebSessionAttributeConverter converter;

  public WebSessionMapper(final WebSessionAttributeConverter converter) {
    this.converter = converter;
  }

  /** Converts a {@link WebSession} to a {@link SessionData} domain record. */
  public SessionData toSessionData(final WebSession webSession) {
    final Map<String, Object> attributes = serializeSessionAttributes(webSession);
    return new SessionData(
        webSession.getId(),
        webSession.getCreationTime(),
        webSession.getLastAccessedTime(),
        webSession.getMaxInactiveInterval().getSeconds(),
        attributes);
  }

  /** Restores a {@link WebSession} from a {@link SessionData} domain record. */
  public WebSession fromSessionData(final SessionData sessionData) {
    try {
      final String sessionId = sessionData.id();
      final Instant creationTime = sessionData.creationTime();
      final Instant lastAccessedTime = sessionData.lastAccessedTime();
      final long maxInactiveIntervalInSeconds = sessionData.maxInactiveIntervalInSeconds();

      final WebSession webSession = new WebSession(sessionId);
      webSession.setCreationTime(creationTime);
      if (lastAccessedTime != null) {
        webSession.setLastAccessedTime(lastAccessedTime);
      }
      webSession.setMaxInactiveInterval(Duration.ofSeconds(maxInactiveIntervalInSeconds));

      // Attributes are stored as raw objects in SessionData (the SPI implementation
      // is responsible for serialization). Re-set them on the session.
      if (sessionData.attributes() != null) {
        sessionData.attributes().forEach(webSession::setAttribute);
      }
      return webSession;
    } catch (final Exception e) {
      LOG.error("The persistent session could not be restored.", e);
      return null;
    }
  }

  private Map<String, Object> serializeSessionAttributes(final WebSession webSession) {
    final Map<String, Object> serializedAttributes = new HashMap<>();
    final var attributeNames = webSession.getAttributeNames();
    attributeNames.forEach(name -> serializedAttributes.put(name, webSession.getAttribute(name)));
    return serializedAttributes;
  }

  /**
   * Default converter that uses Spring's {@link GenericConversionService} with standard
   * serializing/deserializing converters for byte-array based persistence.
   */
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
