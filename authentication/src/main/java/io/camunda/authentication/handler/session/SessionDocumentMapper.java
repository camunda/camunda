/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler.session;

import static io.camunda.authentication.handler.session.WebSession.ATTRIBUTES;
import static io.camunda.authentication.handler.session.WebSession.CREATION_TIME;
import static io.camunda.authentication.handler.session.WebSession.ID;
import static io.camunda.authentication.handler.session.WebSession.LAST_ACCESSED_TIME;
import static io.camunda.authentication.handler.session.WebSession.MAX_INACTIVE_INTERVAL_IN_SECONDS;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.stereotype.Component;

@Component
public class SessionDocumentMapper {

  public static final String POLLING_HEADER = "x-is-polling";
  private static final Logger LOGGER = LoggerFactory.getLogger(SessionDocumentMapper.class);
  private final GenericConversionService conversionService;

  public SessionDocumentMapper(final GenericConversionService conversionService) {
    this.conversionService = conversionService;
  }

  @PostConstruct
  private void setUp() {
    setupConverter();
  }

  private void setupConverter() {
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
  }

  public Map<String, Object> sessionToDocument(final WebSession session) {
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
        session.getMaxInactiveInterval() == null
            ? Long.MAX_VALUE
            : session.getMaxInactiveInterval().getSeconds(),
        ATTRIBUTES,
        attributes);
  }

  public WebSession documentToSession(
      final HttpServletRequest request, final Map<String, Object> document) {
    try {
      if (document == null || document.isEmpty()) {
        LOGGER.warn("Document is empty");
        return null;
      }

      final String sessionId = getSessionIdFrom(document);
      final WebSession session = new WebSession(sessionId);
      session.setLastAccessedTime(getInstantFor(document.get(LAST_ACCESSED_TIME)));
      try {
        if (request != null && request.getHeader(POLLING_HEADER) != null) {
          request.getHeader(POLLING_HEADER);
          session.setPolling(true);
        }
      } catch (final Exception e) {

      }
      session.setCreationTime(getInstantFor(document.get(CREATION_TIME)));
      session.setMaxInactiveInterval(
          getDurationFor(document.get(MAX_INACTIVE_INTERVAL_IN_SECONDS)));

      final Object attributesObject = document.get(ATTRIBUTES);
      if (attributesObject != null
          && attributesObject.getClass().isInstance(new HashMap<String, String>())) {
        final Map<String, Object> attributes = (Map<String, Object>) document.get(ATTRIBUTES);
        attributes
            .keySet()
            .forEach(
                name -> {
                  if (attributes.get(name) instanceof String) {
                    session.setAttribute(
                        name,
                        deserialize(Base64.getDecoder().decode((String) attributes.get(name))));
                  } else if (attributes.get(name) instanceof byte[]) {
                    session.setAttribute(name, deserialize((byte[]) attributes.get(name)));
                  }
                });
      }
      return session;

    } catch (final Exception e) {
      LOGGER.error(e.getMessage(), e);
      return null;
    }
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
}
