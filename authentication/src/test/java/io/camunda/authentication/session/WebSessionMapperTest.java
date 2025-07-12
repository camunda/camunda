/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    final var conversionService = new GenericConversionService();
    webSessionAttributeConverter = new SpringBasedWebSessionAttributeConverter(conversionService);
    objectMapper = new ObjectMapper();
    webSessionMapper = new WebSessionMapper(webSessionAttributeConverter, objectMapper);
  }

  @Test
  void toPersistentSessionWithCamundaPrincipal() throws JsonProcessingException {
    // given
    final var securityContext = new SecurityContextImpl();
    final UsernamePasswordAuthenticationToken authenticationToken =
        new UsernamePasswordAuthenticationToken(
            CamundaUserBuilder.aCamundaUser()
                .withUsername("test")
                .withPassword("admin")
                .withUserKey(1L)
                .withRoles(List.of(new RoleEntity(1L, "testRole", "testRole", "description")))
                .build(),
            null);
    securityContext.setAuthentication(authenticationToken);

    final WebSession webSession = new WebSession("sessionId");
    webSession.setAttribute("securityContext", securityContext);

    // when
    final var persistentWebSession = webSessionMapper.toPersistentWebSession(webSession);

    assertThat(persistentWebSession).isNotNull();
    assertThat(persistentWebSession.id()).isEqualTo("sessionId");
    assertThat(persistentWebSession.attributesAsJson()).isNotNull();
    assertThat(persistentWebSession.attributesAsJson())
        .isEqualTo(
            "{\"securityContext\":\"rO0ABXNyAD1vcmcuc3ByaW5nZnJhbWV3b3JrLnNlY3VyaXR5LmNvcmUuY29udGV4dC5TZWN1cml0eUNvbnRleHRJbXBsAAAAAAAAAmwCAAFMAA5hdXRoZW50aWNhdGlvbnQAMkxvcmcvc3ByaW5nZnJhbWV3b3JrL3NlY3VyaXR5L2NvcmUvQXV0aGVudGljYXRpb247eHBzcgBPb3JnLnNwcmluZ2ZyYW1ld29yay5zZWN1cml0eS5hdXRoZW50aWNhdGlvbi5Vc2VybmFtZVBhc3N3b3JkQXV0aGVudGljYXRpb25Ub2tlbgAAAAAAAAJsAgACTAALY3JlZGVudGlhbHN0ABJMamF2YS9sYW5nL09iamVjdDtMAAlwcmluY2lwYWxxAH4ABHhyAEdvcmcuc3ByaW5nZnJhbWV3b3JrLnNlY3VyaXR5LmF1dGhlbnRpY2F0aW9uLkFic3RyYWN0QXV0aGVudGljYXRpb25Ub2tlbtOqKH5uR2QOAgADWgANYXV0aGVudGljYXRlZEwAC2F1dGhvcml0aWVzdAAWTGphdmEvdXRpbC9Db2xsZWN0aW9uO0wAB2RldGFpbHNxAH4ABHhwAHNyAB9qYXZhLnV0aWwuQ29sbGVjdGlvbnMkRW1wdHlMaXN0ergXtDynnt4CAAB4cHBwc3IALGlvLmNhbXVuZGEuYXV0aGVudGljYXRpb24uZW50aXR5LkNhbXVuZGFVc2Vy0Mj+PYgzt8gCAAdaAAljYW5Mb2dvdXRMAA5hdXRoZW50aWNhdGlvbnQAOExpby9jYW11bmRhL2F1dGhlbnRpY2F0aW9uL2VudGl0eS9BdXRoZW50aWNhdGlvbkNvbnRleHQ7TAAHYzhMaW5rc3QAD0xqYXZhL3V0aWwvTWFwO0wAC2Rpc3BsYXlOYW1ldAASTGphdmEvbGFuZy9TdHJpbmc7TAAFZW1haWxxAH4ADUwADXNhbGVzUGxhblR5cGVxAH4ADUwAB3VzZXJLZXl0ABBMamF2YS9sYW5nL0xvbmc7eHIAMm9yZy5zcHJpbmdmcmFtZXdvcmsuc2VjdXJpdHkuY29yZS51c2VyZGV0YWlscy5Vc2VyAAAAAAAAAmwCAAdaABFhY2NvdW50Tm9uRXhwaXJlZFoAEGFjY291bnROb25Mb2NrZWRaABVjcmVkZW50aWFsc05vbkV4cGlyZWRaAAdlbmFibGVkTAALYXV0aG9yaXRpZXN0AA9MamF2YS91dGlsL1NldDtMAAhwYXNzd29yZHEAfgANTAAIdXNlcm5hbWVxAH4ADXhwAQEBAXNyACVqYXZhLnV0aWwuQ29sbGVjdGlvbnMkVW5tb2RpZmlhYmxlU2V0gB2S0Y+bgFUCAAB4cgAsamF2YS51dGlsLkNvbGxlY3Rpb25zJFVubW9kaWZpYWJsZUNvbGxlY3Rpb24ZQgCAy173HgIAAUwAAWNxAH4ABnhwc3IAEWphdmEudXRpbC5UcmVlU2V03ZhQk5Xth1sDAAB4cHNyAEZvcmcuc3ByaW5nZnJhbWV3b3JrLnNlY3VyaXR5LmNvcmUudXNlcmRldGFpbHMuVXNlciRBdXRob3JpdHlDb21wYXJhdG9yAAAAAAAAAmwCAAB4cHcEAAAAAHh0AAVhZG1pbnQABHRlc3QAc3IANmlvLmNhbXVuZGEuYXV0aGVudGljYXRpb24uZW50aXR5LkF1dGhlbnRpY2F0aW9uQ29udGV4dAAAAAAAAAAAAgAHWgASZ3JvdXBzQ2xhaW1FbmFibGVkTAAWYXV0aG9yaXplZEFwcGxpY2F0aW9uc3QAEExqYXZhL3V0aWwvTGlzdDtMAAhjbGllbnRJZHEAfgANTAAGZ3JvdXBzcQB+ABxMAAVyb2xlc3EAfgAcTAAHdGVuYW50c3EAfgAcTAAIdXNlcm5hbWVxAH4ADXhwAHNyABFqYXZhLnV0aWwuQ29sbFNlcleOq7Y6G6gRAwABSQADdGFneHAAAAABdwQAAAAAeHBxAH4AH3NxAH4AHgAAAAF3BAAAAAFzcgAlaW8uY2FtdW5kYS5zZWFyY2guZW50aXRpZXMuUm9sZUVudGl0eQAAAAAAAAAAAgAETAALZGVzY3JpcHRpb25xAH4ADUwABG5hbWVxAH4ADUwABnJvbGVJZHEAfgANTAAHcm9sZUtleXEAfgAOeHB0AAtkZXNjcmlwdGlvbnQACHRlc3RSb2xlcQB+ACRzcgAOamF2YS5sYW5nLkxvbmc7i+SQzI8j3wIAAUoABXZhbHVleHIAEGphdmEubGFuZy5OdW1iZXKGrJUdC5TgiwIAAHhwAAAAAAAAAAF4cQB+AB9xAH4AGnNxAH4AHgAAAAN3BAAAAAB4cHBwcQB+ACc=\"}");
    final Map<String, byte[]> attributes =
        objectMapper.readValue(persistentWebSession.attributesAsJson(), new TypeReference<>() {});
    assertThat(attributes).containsKey("securityContext");
    assertThat(attributes.get("securityContext"))
        .isEqualTo(webSessionAttributeConverter.serialize(securityContext));
  }

  @Test
  void toPersistentSession() throws JsonProcessingException {
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
    webSession.setAttribute("attribute3", null);

    // when
    final var persistentWebSession = webSessionMapper.toPersistentWebSession(webSession);

    assertThat(persistentWebSession).isNotNull();
    assertThat(persistentWebSession.id()).isEqualTo("sessionId");
    assertThat(persistentWebSession.creationTime()).isEqualTo(now.toEpochMilli());
    assertThat(persistentWebSession.lastAccessedTime()).isEqualTo(now.toEpochMilli());
    assertThat(persistentWebSession.maxInactiveIntervalInSeconds())
        .isEqualTo(maxInactiveInterval.getSeconds());
    assertThat(persistentWebSession.attributesAsJson()).isNotNull();
    assertThat(persistentWebSession.attributesAsJson())
        .isEqualTo(
            "{\"attribute1\":\"rO0ABXQABnZhbHVlMQ==\",\"attribute2\":\"rO0ABXNyAERpby5jYW11bmRhLmF1dGhlbnRpY2F0aW9uLnNlc3Npb24uV2ViU2Vzc2lvbk1hcHBlclRlc3QkVGVzdEF0dHJpYnV0ZQAAAAAAAAAAAgACTAAJYXR0cmlidXRldAASTGphdmEvbGFuZy9TdHJpbmc7TAAFdmFsdWVxAH4AAXhwdAADZm9vdAADYmFy\"}");
    final Map<String, byte[]> attributes =
        objectMapper.readValue(persistentWebSession.attributesAsJson(), new TypeReference<>() {});
    assertThat(attributes.size()).isEqualTo(2);
    assertThat(attributes).containsKey("attribute1");
    assertThat(attributes.get("attribute1"))
        .isEqualTo(webSessionAttributeConverter.serialize("value1"));
    assertThat(attributes).containsKey("attribute2");
    assertThat(attributes.get("attribute2"))
        .isEqualTo(webSessionAttributeConverter.serialize(testAttribute));
  }

  @Test
  void fromPersistentSession() throws JsonProcessingException {
    // given
    final var now = Instant.now();
    final var maxInactiveInterval = Duration.ofSeconds(1800);
    final var testAttribute = new TestAttribute("foo", "bar");

    final var attributeMap =
        Map.of(
            "attribute1",
            webSessionAttributeConverter.serialize("value1"),
            "attribute2",
            webSessionAttributeConverter.serialize(testAttribute));

    final var persistentSession =
        new PersistentWebSessionEntity(
            "sessionId",
            now.toEpochMilli(),
            now.toEpochMilli(),
            maxInactiveInterval.toSeconds(),
            objectMapper.writeValueAsString(attributeMap));

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
  void fromPersistentSessionReturnsNullDueToInvalidAttributes() {
    // given
    final var persistentSession =
        new PersistentWebSessionEntity(
            "sessionId",
            Instant.now().toEpochMilli(),
            Instant.now().toEpochMilli(),
            500L,
            "invalidAttributes");

    // when
    final var webSession = webSessionMapper.fromPersistentWebSession(persistentSession);

    // then
    assertThat(webSession).isNull();
  }

  @Test
  void fromPersistentSessionWithCamundaPrincipal() throws JsonProcessingException {
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
                .withRoles(List.of(new RoleEntity(1L, "testRole", "testRole", "description")))
                .build(),
            null);
    securityContext.setAuthentication(authenticationToken);

    final var persistentSession =
        new PersistentWebSessionEntity(
            "sessionId",
            now.toEpochMilli(),
            now.toEpochMilli(),
            maxInactiveInterval.toSeconds(),
            objectMapper.writeValueAsString(
                Map.of(
                    "securityContext", webSessionAttributeConverter.serialize(securityContext))));

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
