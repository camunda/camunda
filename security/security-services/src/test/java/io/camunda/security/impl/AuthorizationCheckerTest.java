/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.COMPONENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

public class AuthorizationCheckerTest {

  @Mock private AuthorizationReader authorizationReader;
  private AuthorizationChecker authorizationChecker;

  @BeforeEach
  public void setUp() {
    authorizationChecker = new AuthorizationChecker(authorizationReader);
  }

  @Test
  public void noResourceIdsReturnedWhenOwnerIdsIsEmpty() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    final var authorization = mock(Authorization.class);
    final var securityContext = mock(SecurityContext.class);
    when(securityContext.authentication()).thenReturn(authentication);
    when(securityContext.authorization()).thenReturn(authorization);

    // when
    final var result = authorizationChecker.retrieveAuthorizedAuthorizationScopes(securityContext);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  public void noPermissionTypesReturnedWhenOwnerIdsIsEmpty() {
    // given
    final var authentication = mock(CamundaAuthentication.class);

    // when
    final var result =
        authorizationChecker.collectPermissionTypes("foo", COMPONENT, authentication);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  public void notAuthorizedWhenOwnerIdsIsEmpty() {
    // given
    final var authScope = AuthorizationScope.id("foo");
    final var authentication = mock(CamundaAuthentication.class);
    final var authorization = mock(Authorization.class);
    final var securityContext = mock(SecurityContext.class);
    when(securityContext.authentication()).thenReturn(authentication);
    when(securityContext.authorization()).thenReturn(authorization);

    // when
    final var result = authorizationChecker.isAuthorized(authScope, securityContext);

    // then
    assertThat(result).isFalse();
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class IsAuthorizedTests {

    public final String WILDCARD_RESOURCE_ID = AuthorizationScope.WILDCARD.getResourceId();
    public final String AUTHORIZED_USERNAME =
        io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;

    private static final String RESOURCE_ID = "id";

    private AuthorizationChecker authorizationChecker;
    private FakeAuthorizationReader authorizationReader;

    @BeforeAll
    void beforeAll() {
      authorizationReader = new FakeAuthorizationReader();
      authorizationChecker = new AuthorizationChecker(authorizationReader);
      // user based authorizations
      authorizationReader.create(
          new AuthorizationEntity(
              Protocol.encodePartitionId(1, 1),
              "userWithWildcard",
              AuthorizationOwnerType.USER.name(),
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceMatcher.ANY.value(),
              WILDCARD_RESOURCE_ID,
              Set.of(PermissionType.READ_PROCESS_INSTANCE)));
      authorizationReader.create(
          new AuthorizationEntity(
              Protocol.encodePartitionId(1, 2),
              "userWithResourceId",
              AuthorizationOwnerType.USER.name(),
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceMatcher.ID.value(),
              RESOURCE_ID,
              Set.of(PermissionType.READ_PROCESS_INSTANCE)));
      // client based authorizations
      authorizationReader.create(
          new AuthorizationEntity(
              Protocol.encodePartitionId(1, 3),
              "clientWildcard",
              AuthorizationOwnerType.CLIENT.name(),
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceMatcher.ANY.value(),
              WILDCARD_RESOURCE_ID,
              Set.of(PermissionType.READ_PROCESS_INSTANCE)));
      authorizationReader.create(
          new AuthorizationEntity(
              Protocol.encodePartitionId(1, 4),
              "clientWithResourceId",
              AuthorizationOwnerType.CLIENT.name(),
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceMatcher.ID.value(),
              RESOURCE_ID,
              Set.of(PermissionType.READ_PROCESS_INSTANCE)));
      // mapping rule based authorizations
      authorizationReader.create(
          new AuthorizationEntity(
              Protocol.encodePartitionId(1, 5),
              "mrWildcard",
              AuthorizationOwnerType.MAPPING_RULE.name(),
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceMatcher.ANY.value(),
              WILDCARD_RESOURCE_ID,
              Set.of(PermissionType.READ_PROCESS_INSTANCE)));
      authorizationReader.create(
          new AuthorizationEntity(
              Protocol.encodePartitionId(1, 6),
              "mrWithResourceId",
              AuthorizationOwnerType.MAPPING_RULE.name(),
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceMatcher.ID.value(),
              RESOURCE_ID,
              Set.of(PermissionType.READ_PROCESS_INSTANCE)));
    }

    @AfterAll
    void afterAll() {
      authorizationReader.close();
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("anonymousAuthorizationScenarios")
    void anonymousAuthorizationScenariosTest(
        final String displayName,
        final CamundaAuthentication authentication,
        final Authorization<?> authorization,
        final AuthorizationScope attemptedScope,
        final Expected expected) {
      // given
      final var securityContext =
          SecurityContext.of(
              c -> c.withAuthentication(authentication).withAuthorization(authorization));

      // when
      final boolean isAuthorized =
          authorizationChecker.isAuthorized(attemptedScope, securityContext);

      // then
      Assertions.assertThat(isAuthorized)
          .describedAs(displayName)
          .isEqualTo(expected == Expected.ACCESS_ALLOWED);
    }

    Stream<Arguments> anonymousAuthorizationScenarios() {
      return Stream.of(
          Arguments.of(
              "anonymous ID:id accessing id -> denied",
              CamundaAuthentication.anonymous(),
              Authorization.of(
                  authorization ->
                      authorization
                          .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(RESOURCE_ID)),
              AuthorizationScope.id(RESOURCE_ID),
              Expected.ACCESS_DENIED),
          Arguments.of(
              "anonymous ID:id accessing * -> denied",
              CamundaAuthentication.anonymous(),
              Authorization.of(
                  authorization ->
                      authorization
                          .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(RESOURCE_ID)),
              AuthorizationScope.WILDCARD,
              Expected.ACCESS_DENIED),
          Arguments.of(
              "anonymous ANY:* accessing id -> denied",
              CamundaAuthentication.anonymous(),
              Authorization.of(
                  authorization ->
                      authorization
                          .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(WILDCARD_RESOURCE_ID)),
              AuthorizationScope.id(RESOURCE_ID),
              Expected.ACCESS_DENIED),
          Arguments.of(
              "anonymous ANY:* accessing * -> denied",
              CamundaAuthentication.anonymous(),
              Authorization.of(
                  authorization ->
                      authorization
                          .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(WILDCARD_RESOURCE_ID)),
              AuthorizationScope.WILDCARD,
              Expected.ACCESS_DENIED));
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("authenticatedAuthorizationScenarios")
    void authenticatedAuthorizationScenariosTest(
        final String displayName,
        final CamundaAuthentication authentication,
        final Authorization<?> authorization,
        final AuthorizationScope attemptedScope,
        final Expected expected) {
      // given
      final var securityContext =
          SecurityContext.of(
              c -> c.withAuthentication(authentication).withAuthorization(authorization));

      // when
      final boolean isAuthorized =
          authorizationChecker.isAuthorized(attemptedScope, securityContext);

      // then
      Assertions.assertThat(isAuthorized)
          .describedAs(displayName)
          .isEqualTo(expected == Expected.ACCESS_ALLOWED);
    }

    Stream<Arguments> authenticatedAuthorizationScenarios() {
      return Stream.of(
          // user based
          Arguments.of(
              "auth userWithWildcard ANY:* accessing * -> allowed",
              CamundaAuthentication.of(
                  authorization ->
                      authorization
                          .user("userWithWildcard")
                          .claims(Map.of(AUTHORIZED_USERNAME, "userWithWildcard"))),
              Authorization.of(
                  authorization ->
                      authorization
                          .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(WILDCARD_RESOURCE_ID)),
              AuthorizationScope.WILDCARD,
              Expected.ACCESS_ALLOWED),
          Arguments.of(
              "auth userWithWildcard ANY:* accessing id -> allowed",
              CamundaAuthentication.of(
                  authorization ->
                      authorization
                          .user("userWithWildcard")
                          .claims(Map.of(AUTHORIZED_USERNAME, "userWithWildcard"))),
              Authorization.of(
                  authorization ->
                      authorization
                          .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(WILDCARD_RESOURCE_ID)),
              AuthorizationScope.id(RESOURCE_ID),
              Expected.ACCESS_ALLOWED),
          Arguments.of(
              "auth userWithResourceId ID:id accessing id -> allowed",
              CamundaAuthentication.of(
                  authorization ->
                      authorization
                          .user("userWithResourceId")
                          .claims(Map.of(AUTHORIZED_USERNAME, "userWithResourceId"))),
              Authorization.of(
                  authorization ->
                      authorization
                          .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(RESOURCE_ID)),
              AuthorizationScope.id(RESOURCE_ID),
              Expected.ACCESS_ALLOWED),
          Arguments.of(
              "auth userWithResourceId ID:id accessing different id -> denied",
              CamundaAuthentication.of(
                  authorization ->
                      authorization
                          .user("userWithResourceId")
                          .claims(Map.of(AUTHORIZED_USERNAME, "userWithResourceId"))),
              Authorization.of(
                  authorization ->
                      authorization
                          .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(RESOURCE_ID)),
              AuthorizationScope.id("a_different_id"),
              Expected.ACCESS_DENIED),
          Arguments.of(
              "auth userWithResourceId ID:id accessing * -> denied",
              CamundaAuthentication.of(
                  authorization ->
                      authorization
                          .user("userWithResourceId")
                          .claims(Map.of(AUTHORIZED_USERNAME, "userWithResourceId"))),
              Authorization.of(
                  authorization ->
                      authorization
                          .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(RESOURCE_ID)),
              AuthorizationScope.WILDCARD,
              Expected.ACCESS_DENIED),
          // client based
          Arguments.of(
              "auth clientWildcard ANY:* accessing * -> allowed",
              CamundaAuthentication.of(a -> a.clientId("clientWildcard")),
              Authorization.of(
                  a ->
                      a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(WILDCARD_RESOURCE_ID)),
              AuthorizationScope.WILDCARD,
              Expected.ACCESS_ALLOWED),
          Arguments.of(
              "auth clientWildcard ANY:* accessing id -> allowed",
              CamundaAuthentication.of(a -> a.clientId("clientWildcard")),
              Authorization.of(
                  a ->
                      a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(WILDCARD_RESOURCE_ID)),
              AuthorizationScope.id(RESOURCE_ID),
              Expected.ACCESS_ALLOWED),
          Arguments.of(
              "auth clientWithResourceId ID:id accessing id -> allowed",
              CamundaAuthentication.of(a -> a.clientId("clientWithResourceId")),
              Authorization.of(
                  a ->
                      a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(RESOURCE_ID)),
              AuthorizationScope.id(RESOURCE_ID),
              Expected.ACCESS_ALLOWED),
          Arguments.of(
              "auth clientWithResourceId ID:id accessing other id -> denied",
              CamundaAuthentication.of(a -> a.clientId("clientWithResourceId")),
              Authorization.of(
                  a ->
                      a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(RESOURCE_ID)),
              AuthorizationScope.id("different"),
              Expected.ACCESS_DENIED),
          // mapping rule based
          Arguments.of(
              "auth mapping rule mrWildcard ANY:* accessing * -> allowed",
              CamundaAuthentication.of(a -> a.mappingRule("mrWildcard")),
              Authorization.of(
                  a ->
                      a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(WILDCARD_RESOURCE_ID)),
              AuthorizationScope.WILDCARD,
              Expected.ACCESS_ALLOWED),
          Arguments.of(
              "auth mapping rule mrWildcard ANY:* accessing id -> allowed",
              CamundaAuthentication.of(a -> a.mappingRule("mrWildcard")),
              Authorization.of(
                  a ->
                      a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(WILDCARD_RESOURCE_ID)),
              AuthorizationScope.id(RESOURCE_ID),
              Expected.ACCESS_ALLOWED),
          Arguments.of(
              "auth mapping rule mrWithResourceId ID:id accessing id -> allowed",
              CamundaAuthentication.of(a -> a.mappingRule("mrWithResourceId")),
              Authorization.of(
                  a ->
                      a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(RESOURCE_ID)),
              AuthorizationScope.id(RESOURCE_ID),
              Expected.ACCESS_ALLOWED),
          Arguments.of(
              "auth mapping rule mrWithResourceId ID:id accessing other id -> denied",
              CamundaAuthentication.of(a -> a.mappingRule("mrWithResourceId")),
              Authorization.of(
                  a ->
                      a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                          .permissionType(PermissionType.READ_PROCESS_INSTANCE)
                          .resourceId(RESOURCE_ID)),
              AuthorizationScope.id("different"),
              Expected.ACCESS_DENIED));
    }

    private enum Expected {
      ACCESS_ALLOWED,
      ACCESS_DENIED
    }
  }
}
