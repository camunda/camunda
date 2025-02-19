/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder;
import io.camunda.authentication.session.WebSessionMapper.SpringBasedWebSessionAttributeConverter;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.entities.RoleEntity;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;

public class WebSessionMapperTest {

  private WebSessionMapper webSessionMapper;
  private WebSessionAttributeConverter webSessionAttributeConverter;

  @BeforeEach
  void setUp() {
    final var conversionService = new GenericConversionService();
    webSessionAttributeConverter = new SpringBasedWebSessionAttributeConverter(conversionService);
    webSessionMapper = new WebSessionMapper(webSessionAttributeConverter);
  }

  @Test
  void toPersistentSessionWithCamundaPrinciple() {
    // given
    final var securityContext = new SecurityContextImpl();
    final UsernamePasswordAuthenticationToken authenticationToken =
        new UsernamePasswordAuthenticationToken(
            CamundaUserBuilder.aCamundaUser()
                .withUsername("test")
                .withPassword("admin")
                .withUserKey(1L)
                .withRoles(List.of(new RoleEntity(1L, "testRole")))
                .build(),
            null);
    securityContext.setAuthentication(authenticationToken);

    final WebSession webSession = new WebSession("sessionId");
    webSession.setAttribute("securityContext", securityContext);

    // when
    final var persistentWebSession = webSessionMapper.toPersistentWebSession(webSession);

    assertThat(persistentWebSession).isNotNull();
    assertThat(persistentWebSession.id()).isEqualTo("sessionId");
    assertThat(persistentWebSession.attributes()).hasSize(1);
    assertThat(persistentWebSession.attributes().get("securityContext"))
        .isEqualTo(webSessionAttributeConverter.serialize(securityContext));
  }

  @Test
  void toPersistentSession() {
    // given
    final var now = Instant.now();
    final var maxInactiveInterval = Duration.ofSeconds(1800);
    final var testAttribute = new TestAttribute("foo", "bar");

    final WebSession webSession = new WebSession("sessionId");
    webSession.setCreationTime(now);
    webSession.setLastAccessedTime(now);
    webSession.setMaxInactiveInterval(maxInactiveInterval);
    webSession.setAttribute("attribute1", "value1");
    webSession.setAttribute("attribute2", testAttribute);

    // when
    final var persistentWebSession = webSessionMapper.toPersistentWebSession(webSession);

    assertThat(persistentWebSession).isNotNull();
    assertThat(persistentWebSession.id()).isEqualTo("sessionId");
    assertThat(persistentWebSession.creationTime()).isEqualTo(now.toEpochMilli());
    assertThat(persistentWebSession.lastAccessedTime()).isEqualTo(now.toEpochMilli());
    assertThat(persistentWebSession.maxInactiveIntervalInSeconds())
        .isEqualTo(maxInactiveInterval.getSeconds());
    assertThat(persistentWebSession.attributes()).hasSize(2);
    assertThat(persistentWebSession.attributes().get("attribute1"))
        .isEqualTo(webSessionAttributeConverter.serialize("value1"));
    assertThat(persistentWebSession.attributes().get("attribute2"))
        .isEqualTo(webSessionAttributeConverter.serialize(testAttribute));
  }

  @Test
  void fromPersistentSession() {
    // given
    final var now = Instant.now();
    final var maxInactiveInterval = Duration.ofSeconds(1800);
    final var testAttribute = new TestAttribute("foo", "bar");

    final var persistentSession =
        new PersistentWebSessionEntity(
            "sessionId",
            now.toEpochMilli(),
            now.toEpochMilli(),
            maxInactiveInterval.toSeconds(),
            Map.of(
                "attribute1",
                webSessionAttributeConverter.serialize("value1"),
                "attribute2",
                webSessionAttributeConverter.serialize(testAttribute)));

    // when
    final var webSession = webSessionMapper.fromPersistentWebSession(persistentSession);

    assertThat(webSession).isNotNull();
    assertThat(webSession.getId()).isEqualTo("sessionId");
    assertThat(webSession.getCreationTime().toEpochMilli()).isEqualTo(now.toEpochMilli());
    assertThat(webSession.getLastAccessedTime().toEpochMilli()).isEqualTo(now.toEpochMilli());
    assertThat(webSession.getMaxInactiveInterval()).isEqualTo(maxInactiveInterval);
    assertThat(webSession.getAttributeNames()).hasSize(2);
    assertThat((String) webSession.getAttribute("attribute1")).isEqualTo("value1");
    assertThat((TestAttribute) webSession.getAttribute("attribute2")).isEqualTo(testAttribute);
  }

  @Test
  void fromPersistentSessionWithCamundaPrinciple() {
    // given
    final var now = Instant.now();
    final var maxInactiveInterval = Duration.ofSeconds(1800);
    final var securityContext = new SecurityContextImpl();
    final UsernamePasswordAuthenticationToken authenticationToken =
        new UsernamePasswordAuthenticationToken(
            CamundaUserBuilder.aCamundaUser()
                .withUsername("test")
                .withPassword("admin")
                .withUserKey(1L)
                .withRoles(List.of(new RoleEntity(1L, "testRole")))
                .build(),
            null);
    securityContext.setAuthentication(authenticationToken);

    final var persistentSession =
        new PersistentWebSessionEntity(
            "sessionId",
            now.toEpochMilli(),
            now.toEpochMilli(),
            maxInactiveInterval.toSeconds(),
            Map.of("securityContext", webSessionAttributeConverter.serialize(securityContext)));

    // when
    final var webSession = webSessionMapper.fromPersistentWebSession(persistentSession);

    assertThat(webSession).isNotNull();
    assertThat(webSession.getId()).isEqualTo("sessionId");
    assertThat(webSession.getCreationTime().toEpochMilli()).isEqualTo(now.toEpochMilli());
    assertThat(webSession.getLastAccessedTime().toEpochMilli()).isEqualTo(now.toEpochMilli());
    assertThat(webSession.getMaxInactiveInterval()).isEqualTo(maxInactiveInterval);
    assertThat(webSession.getAttributeNames()).hasSize(1);
    assertThat((SecurityContextImpl) webSession.getAttribute("securityContext"))
        .isEqualTo(securityContext);
  }

  private record TestAttribute(String attribute, String value) implements Serializable {}
}
