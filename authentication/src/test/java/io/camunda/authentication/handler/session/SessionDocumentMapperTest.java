/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;

class SessionDocumentMapperTest {

  private GenericConversionService conversionService;
  private SessionDocumentMapper sessionDocumentMapper;

  @BeforeEach
  void setUp() {
    conversionService = new GenericConversionService();
    sessionDocumentMapper = new SessionDocumentMapper(conversionService);
    sessionDocumentMapper.setupConverter();
  }

  @AfterEach
  void tearDown() {}

  @Test
  void sessionToDocumentReturnMap() {
    final WebSession webSession = new WebSession(UUID.randomUUID().toString());
    webSession.setAttribute("attribute1", "value1");
    webSession.setAttribute("attribute2", "value2".getBytes(StandardCharsets.UTF_8));

    final Map<String, Object> document = sessionDocumentMapper.sessionToDocument(webSession);

    assertNotNull(document);
    assertThat(document).hasFieldOrProperty(WebSession.ID);
    assertThat(document.get(WebSession.ID)).isEqualTo(webSession.getId());
    assertThat(document).hasFieldOrProperty(WebSession.CREATION_TIME);
    assertThat(document.get(WebSession.CREATION_TIME))
        .isEqualTo(webSession.getCreationTime().toEpochMilli());
    assertThat(document).hasFieldOrProperty(WebSession.LAST_ACCESSED_TIME);
    assertThat(document.get(WebSession.LAST_ACCESSED_TIME))
        .isEqualTo(webSession.getLastAccessedTime().toEpochMilli());
    assertThat(document).hasFieldOrProperty(WebSession.MAX_INACTIVE_INTERVAL_IN_SECONDS);
    assertThat(document.get(WebSession.MAX_INACTIVE_INTERVAL_IN_SECONDS))
        .isEqualTo(webSession.getMaxInactiveInterval().getSeconds());
    assertThat(document).hasFieldOrProperty(WebSession.ATTRIBUTES);
    assertThat(document.get(WebSession.ATTRIBUTES)).hasFieldOrProperty("attribute1");
    assertThat(document.get(WebSession.ATTRIBUTES)).hasFieldOrProperty("attribute2");
  }

  @Test
  void documentToSessionReturnsSession() {
    final Map<String, Object> document = new HashMap<>();
    document.put(WebSession.ID, UUID.randomUUID().toString());
    document.put(WebSession.LAST_ACCESSED_TIME, 1000L);
    document.put(WebSession.CREATION_TIME, 1000L);
    document.put(
        WebSession.ATTRIBUTES,
        Map.of(
            "attribute1",
            Objects.requireNonNull(
                conversionService.convert(
                    "value".getBytes(),
                    TypeDescriptor.valueOf(Object.class),
                    TypeDescriptor.valueOf(byte[].class)))));

    final WebSession webSession = sessionDocumentMapper.documentToSession(null, document);

    assertNotNull(webSession);
    assertThat(webSession.getId()).isEqualTo(document.get(WebSession.ID));
    assertThat(webSession.getCreationTime().toEpochMilli())
        .isEqualTo(document.get(WebSession.CREATION_TIME));
    assertThat(webSession.getLastAccessedTime().toEpochMilli())
        .isEqualTo(document.get(WebSession.LAST_ACCESSED_TIME));
    assertThat(webSession.getMaxInactiveInterval()).isNull();
    assertThat(webSession.getAttributeNames()).contains("attribute1");
  }

  @Test
  void sessionToDocumentToSessionReturnsSame() {
    final WebSession webSession = new WebSession(UUID.randomUUID().toString());
    webSession.setAttribute("attribute1", "value1");
    webSession.setAttribute("attribute2", "value2".getBytes(StandardCharsets.UTF_8));

    final WebSession mappedSession =
        sessionDocumentMapper.documentToSession(
            null, sessionDocumentMapper.sessionToDocument(webSession));

    assertNotNull(mappedSession);
    assertThat(mappedSession.getId()).isEqualTo(webSession.getId());
    assertThat(mappedSession.getCreationTime().toEpochMilli())
        .isEqualTo(webSession.getCreationTime().toEpochMilli());
    assertThat(mappedSession.getLastAccessedTime().toEpochMilli())
        .isEqualTo(webSession.getLastAccessedTime().toEpochMilli());
    assertThat(mappedSession.getMaxInactiveInterval())
        .isEqualTo(webSession.getMaxInactiveInterval());
    assertThat(mappedSession.getAttributeNames()).isEqualTo(webSession.getAttributeNames());
    assertThat((Object) mappedSession.getAttribute("attribute1"))
        .isEqualTo(webSession.getAttribute("attribute1"));
    assertThat((Object) mappedSession.getAttribute("attribute2"))
        .isEqualTo(webSession.getAttribute("attribute2"));
  }

  @Test
  void getSessionIdFrom() {
    final WebSession webSession = new WebSession(UUID.randomUUID().toString());
    final var id =
        sessionDocumentMapper.getSessionIdFrom(sessionDocumentMapper.sessionToDocument(webSession));
    assertThat(id).isEqualTo(webSession.getId());
  }
}
