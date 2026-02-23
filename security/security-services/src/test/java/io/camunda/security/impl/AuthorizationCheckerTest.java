/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AuthorizationCheckerTest {

  private static final String EMPTY_STRING = "";

  private AuthorizationChecker authorizationChecker;

  @BeforeEach
  public void setUp() {
    authorizationChecker = new AuthorizationChecker(new FakeAuthorizationReader());
  }

  @Test
  public void noResourceIdsReturnedWhenOwnerIdsIsEmpty() {
    // given
    final var result =
        authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            CamundaAuthentication.of(a -> a), Authorization.of(a -> a));

    // then
    Assertions.assertThat(result).isEmpty();
  }

  @Test
  public void noPermissionTypesReturnedWhenOwnerIdsIsEmpty() {
    // when
    final var result =
        authorizationChecker.collectPermissionTypes(
            "foo", AuthorizationResourceType.COMPONENT, CamundaAuthentication.none());

    // then
    Assertions.assertThat(result).isEmpty();
  }

  @Test
  public void notAuthorizedWhenOwnerIdsIsEmpty() {
    // given
    final var authScope = AuthorizationScope.id("foo");

    // when
    final var result =
        authorizationChecker.isAuthorized(
            authScope, CamundaAuthentication.of(a -> a), Authorization.of(a -> a));

    // then
    Assertions.assertThat(result).isFalse();
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class IsAuthorizedTests {

    public static final String WILDCARD_RESOURCE_ID = AuthorizationScope.WILDCARD.getResourceId();
    public static final String AUTHORIZED_USERNAME =
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
              EMPTY_STRING,
              Set.of(PermissionType.READ_PROCESS_INSTANCE)));
      authorizationReader.create(
          new AuthorizationEntity(
              Protocol.encodePartitionId(1, 2),
              "userWithResourceId",
              AuthorizationOwnerType.USER.name(),
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceMatcher.ID.value(),
              RESOURCE_ID,
              EMPTY_STRING,
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
              EMPTY_STRING,
              Set.of(PermissionType.READ_PROCESS_INSTANCE)));
      authorizationReader.create(
          new AuthorizationEntity(
              Protocol.encodePartitionId(1, 4),
              "clientWithResourceId",
              AuthorizationOwnerType.CLIENT.name(),
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceMatcher.ID.value(),
              RESOURCE_ID,
              EMPTY_STRING,
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
              EMPTY_STRING,
              Set.of(PermissionType.READ_PROCESS_INSTANCE)));
      authorizationReader.create(
          new AuthorizationEntity(
              Protocol.encodePartitionId(1, 6),
              "mrWithResourceId",
              AuthorizationOwnerType.MAPPING_RULE.name(),
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceMatcher.ID.value(),
              RESOURCE_ID,
              EMPTY_STRING,
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
      // when
      final boolean isAuthorized =
          authorizationChecker.isAuthorized(attemptedScope, authentication, authorization);

      // then
      Assertions.assertThat(isAuthorized)
          .describedAs(displayName)
          .isEqualTo(expected.isAuthorized());
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
      // when
      final boolean isAuthorized =
          authorizationChecker.isAuthorized(attemptedScope, authentication, authorization);

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
      ACCESS_DENIED;

      boolean isAuthorized() {
        return switch (this) {
          case ACCESS_ALLOWED -> true;
          case ACCESS_DENIED -> false;
        };
      }
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class CollectPermissionTypesTests {

    private static final String WILDCARD_RESOURCE_ID = AuthorizationScope.WILDCARD.getResourceId();
    private static final String RESOURCE_ID = "id";
    private static final String OTHER_RESOURCE_ID = "other-id";

    private final AtomicLong keyCounter = new AtomicLong(Protocol.encodePartitionId(1, 50));

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("collectPermissionTypesScenarios")
    void collectPermissionTypesScenariosTest(final CollectPermissionScenario scenario) {
      try (final var reader = new FakeAuthorizationReader()) {
        // given
        for (final var authorizationEntity : scenario.given()) {
          reader.create(authorizationEntity);
        }
        final var checker = new AuthorizationChecker(reader);

        // when
        final var actual =
            checker.collectPermissionTypes(
                scenario.resourceId(), scenario.resourceType(), scenario.authentication());

        // then
        Assertions.assertThat(actual)
            .describedAs(scenario.displayName())
            .containsExactlyInAnyOrderElementsOf(scenario.expected());
      }
    }

    Stream<CollectPermissionScenario> collectPermissionTypesScenarios() {
      return Stream.of(
          CollectPermissionScenario.displayName(
                  "anonymous PROCESS_DEFINITION no authorizations -> empty")
              .whenAnonymous()
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.PROCESS_DEFINITION)
              .thenResultContains()
              .build(),
          CollectPermissionScenario.displayName(
                  "anonymous PROCESS_DEFINITION unspecified authorization -> empty")
              .given(
                  authEntity(
                      AuthorizationOwnerType.UNSPECIFIED,
                      "unspecified",
                      AuthorizationResourceType.UNSPECIFIED,
                      AuthorizationResourceMatcher.UNSPECIFIED,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)))
              .whenAnonymous()
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.PROCESS_DEFINITION)
              .thenResultContains()
              .build(),
          CollectPermissionScenario.displayName("user alice wildcard AUTHORIZATION READ -> {READ}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)))
              .whenUser("alice")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.AUTHORIZATION)
              .thenResultContains(PermissionType.READ)
              .build(),
          CollectPermissionScenario.displayName("user bob DOCUMENT specific READ -> {READ}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "bob",
                      AuthorizationResourceType.DOCUMENT,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(PermissionType.READ)))
              .whenUser("bob")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.DOCUMENT)
              .thenResultContains(PermissionType.READ)
              .build(),
          CollectPermissionScenario.displayName(
                  "user alice PROCESS_DEFINITION wildcard READ_PROCESS_DEFINITION + specific CANCEL -> union")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.PROCESS_DEFINITION,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ_PROCESS_DEFINITION)),
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.PROCESS_DEFINITION,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(PermissionType.CANCEL_PROCESS_INSTANCE)))
              .whenUser("alice")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.PROCESS_DEFINITION)
              .thenResultContains(
                  PermissionType.READ_PROCESS_DEFINITION, PermissionType.CANCEL_PROCESS_INSTANCE)
              .build(),
          CollectPermissionScenario.displayName("user alice SYSTEM duplicate READ -> {READ}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.SYSTEM,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.SYSTEM,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(PermissionType.READ)))
              .whenUser("alice")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.SYSTEM)
              .thenResultContains(PermissionType.READ)
              .build(),
          CollectPermissionScenario.displayName("user carol AUTHORIZATION mismatch -> empty")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "carol",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(PermissionType.READ)))
              .whenUser("carol")
              .accessesResource(OTHER_RESOURCE_ID, AuthorizationResourceType.AUTHORIZATION)
              .build(),
          CollectPermissionScenario.displayName(
                  "user dave DOCUMENT wildcard applies to other id -> {CREATE,READ,DELETE}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "dave",
                      AuthorizationResourceType.DOCUMENT,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE)))
              .whenUser("dave")
              .accessesResource(OTHER_RESOURCE_ID, AuthorizationResourceType.DOCUMENT)
              .thenResultContains(PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE)
              .build(),
          CollectPermissionScenario.displayName(
                  "alice + groupA PROCESS_DEFINITION wildcard -> {READ_PROCESS_DEFINITION}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.GROUP,
                      "groupA",
                      AuthorizationResourceType.PROCESS_DEFINITION,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ_PROCESS_DEFINITION)))
              .whenUser("alice")
              .withGroups("groupA")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.PROCESS_DEFINITION)
              .thenResultContains(PermissionType.READ_PROCESS_DEFINITION)
              .build(),
          CollectPermissionScenario.displayName(
                  "alice + groupA AUTHORIZATION union wildcard READ + specific UPDATE -> {READ,UPDATE}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.GROUP,
                      "groupA",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(PermissionType.UPDATE)))
              .whenUser("alice")
              .withGroups("groupA")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.AUTHORIZATION)
              .thenResultContains(PermissionType.READ, PermissionType.UPDATE)
              .build(),
          CollectPermissionScenario.displayName(
                  "alice AUTHORIZATION union READ+UPDATE + wildcard DELETE -> {READ,UPDATE,DELETE}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(PermissionType.READ, PermissionType.UPDATE)),
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.DELETE)))
              .whenUser("alice")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.AUTHORIZATION)
              .thenResultContains(PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE)
              .build(),
          CollectPermissionScenario.displayName("erin COMPONENT wildcard ACCESS -> {ACCESS}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "erin",
                      AuthorizationResourceType.COMPONENT,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.ACCESS)))
              .whenUser("erin")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.COMPONENT)
              .thenResultContains(PermissionType.ACCESS)
              .build(),
          CollectPermissionScenario.displayName(
                  "frank SYSTEM wildcard READ + specific READ_JOB_METRIC+READ_USAGE_METRIC+UPDATE -> union")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "frank",
                      AuthorizationResourceType.SYSTEM,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "frank",
                      AuthorizationResourceType.SYSTEM,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(
                          PermissionType.READ_USAGE_METRIC,
                          PermissionType.READ_JOB_METRIC,
                          PermissionType.UPDATE)))
              .whenUser("frank")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.SYSTEM)
              .thenResultContains(
                  PermissionType.READ,
                  PermissionType.READ_USAGE_METRIC,
                  PermissionType.READ_JOB_METRIC,
                  PermissionType.UPDATE)
              .build(),
          CollectPermissionScenario.displayName("hank AUDIT_LOG wildcard -> {READ}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "hank",
                      AuthorizationResourceType.AUDIT_LOG,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)))
              .whenUser("hank")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.AUDIT_LOG)
              .thenResultContains(PermissionType.READ)
              .build(),
          CollectPermissionScenario.displayName("gina PROCESS_DEFINITION mismatch -> empty")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "gina",
                      AuthorizationResourceType.PROCESS_DEFINITION,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(PermissionType.UPDATE_PROCESS_INSTANCE)))
              .whenUser("gina")
              .accessesResource(OTHER_RESOURCE_ID, AuthorizationResourceType.PROCESS_DEFINITION)
              .build(),
          CollectPermissionScenario.displayName("hank DOCUMENT wildcard -> {CREATE,READ,DELETE}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "hank",
                      AuthorizationResourceType.DOCUMENT,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE)))
              .whenUser("hank")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.DOCUMENT)
              .thenResultContains(PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE)
              .build(),
          CollectPermissionScenario.displayName(
                  "ivy BATCH wildcard CREATE + specific DELETE_PROCESS_DEFINITION -> union")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "ivy",
                      AuthorizationResourceType.BATCH,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.CREATE)),
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "ivy",
                      AuthorizationResourceType.BATCH,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION)))
              .whenUser("ivy")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.BATCH)
              .thenResultContains(
                  PermissionType.CREATE,
                  PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION)
              .build(),
          CollectPermissionScenario.displayName("client clientA DOCUMENT wildcard READ -> {READ}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.CLIENT,
                      "clientA",
                      AuthorizationResourceType.DOCUMENT,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)))
              .whenClient("clientA")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.DOCUMENT)
              .thenResultContains(PermissionType.READ)
              .build(),
          CollectPermissionScenario.displayName(
                  "combo client+role+mappingRule DOCUMENT union READ+CREATE+DELETE -> {READ,CREATE,DELETE}")
              .given(
                  authEntity(
                      AuthorizationOwnerType.CLIENT,
                      "comboClient",
                      AuthorizationResourceType.DOCUMENT,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.ROLE,
                      "roleX",
                      AuthorizationResourceType.DOCUMENT,
                      AuthorizationResourceMatcher.ID,
                      RESOURCE_ID,
                      Set.of(PermissionType.CREATE)),
                  authEntity(
                      AuthorizationOwnerType.MAPPING_RULE,
                      "mrCombo",
                      AuthorizationResourceType.DOCUMENT,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.DELETE)))
              .whenClient("comboClient")
              .withRoles("roleX")
              .withMappingRules("mrCombo")
              .accessesResource(RESOURCE_ID, AuthorizationResourceType.DOCUMENT)
              .thenResultContains(PermissionType.READ, PermissionType.CREATE, PermissionType.DELETE)
              .build());
    }

    private AuthorizationEntity authEntity(
        final AuthorizationOwnerType ownerType,
        final String ownerId,
        final AuthorizationResourceType resourceType,
        final AuthorizationResourceMatcher matcher,
        final String resourceId,
        final Set<PermissionType> permissionTypes) {
      return new AuthorizationEntity(
          keyCounter.incrementAndGet(),
          ownerId,
          ownerType.name(),
          resourceType.name(),
          matcher.value(),
          resourceId,
          EMPTY_STRING,
          permissionTypes);
    }

    record CollectPermissionScenario(
        String displayName,
        CamundaAuthentication authentication,
        String resourceId,
        AuthorizationResourceType resourceType,
        List<AuthorizationEntity> given,
        Set<PermissionType> expected) {

      @Override
      public String toString() {
        return displayName;
      }

      static Builder displayName(final String displayName) {
        return new Builder(displayName);
      }

      static final class Builder {
        private final String displayName;
        private String user;
        private String clientId;
        private final List<String> groupIds = new ArrayList<>();
        private final List<String> mappingRuleIds = new ArrayList<>();
        private final List<String> roleIds = new ArrayList<>();
        private String requestedResourceId;
        private AuthorizationResourceType resourceType;
        private final List<AuthorizationEntity> givenAuthorizationEntities = new ArrayList<>();
        private final Set<PermissionType> expected = new LinkedHashSet<>();

        Builder(final String displayName) {
          this.displayName = displayName;
        }

        Builder given(final AuthorizationEntity... authorizationEntities) {
          if (authorizationEntities != null) {
            givenAuthorizationEntities.addAll(Arrays.asList(authorizationEntities));
          }
          return this;
        }

        public Builder whenAnonymous() {
          user = null;
          clientId = null;
          groupIds.clear();
          mappingRuleIds.clear();
          roleIds.clear();
          return this;
        }

        Builder whenUser(final String user) {
          this.user = user;
          return this;
        }

        Builder whenClient(final String clientId) {
          this.clientId = clientId;
          return this;
        }

        Builder withGroups(final String... groupIds) {
          if (groupIds != null) {
            this.groupIds.addAll(Arrays.asList(groupIds));
          }
          return this;
        }

        public Builder withMappingRules(final String... mappingRuleIds) {
          if (mappingRuleIds != null) {
            this.mappingRuleIds.addAll(Arrays.asList(mappingRuleIds));
          }
          return this;
        }

        Builder withRoles(final String... roleIds) {
          if (roleIds != null) {
            this.roleIds.addAll(Arrays.asList(roleIds));
          }
          return this;
        }

        Builder accessesResource(
            final String resourceId, final AuthorizationResourceType resourceType) {
          requestedResourceId = resourceId;
          this.resourceType = resourceType;
          return this;
        }

        Builder thenResultContains(final PermissionType... permissionTypes) {
          if (permissionTypes != null) {
            expected.addAll(Arrays.asList(permissionTypes));
          }
          return this;
        }

        CollectPermissionScenario build() {
          final CamundaAuthentication authentication;
          if (user == null
              && clientId == null
              && groupIds.isEmpty()
              && mappingRuleIds.isEmpty()
              && roleIds.isEmpty()) {
            authentication = CamundaAuthentication.anonymous();
          } else {
            authentication =
                CamundaAuthentication.of(
                    a -> {
                      if (user != null) {
                        a.user(user);
                      }
                      if (clientId != null) {
                        a.clientId(clientId);
                      }
                      if (!groupIds.isEmpty()) {
                        a.groupIds(groupIds);
                      }
                      if (!mappingRuleIds.isEmpty()) {
                        a.mappingRule(mappingRuleIds);
                      }
                      if (!roleIds.isEmpty()) {
                        a.roleIds(roleIds);
                      }
                      return a;
                    });
          }
          return new CollectPermissionScenario(
              displayName,
              authentication,
              requestedResourceId,
              resourceType,
              List.copyOf(givenAuthorizationEntities),
              Set.copyOf(expected));
        }
      }
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class RetrieveAuthorizedAuthorizationScopesTests {

    private static final String WILDCARD_RESOURCE_ID = AuthorizationScope.WILDCARD.getResourceId();

    private final AtomicLong keyCounter = new AtomicLong(Protocol.encodePartitionId(1, 300));

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("retrieveAuthorizationScopesScenarios")
    void retrieveAuthorizationScopesScenariosTest(final RetrieveScopesScenario scenario) {
      try (final var reader = new FakeAuthorizationReader()) {
        // given
        for (final var entity : scenario.given()) {
          reader.create(entity);
        }
        final var checker = new AuthorizationChecker(reader);

        // when
        final var actual =
            checker.retrieveAuthorizedAuthorizationScopes(
                scenario.authentication(),
                Authorization.of(
                    a ->
                        a.resourceType(scenario.resourceType())
                            .permissionType(scenario.permissionType())
                            .resourceId(WILDCARD_RESOURCE_ID)));

        // then
        Assertions.assertThat(actual)
            .describedAs(scenario.displayName())
            .containsExactlyElementsOf(scenario.expected());
      }
    }

    Stream<RetrieveScopesScenario> retrieveAuthorizationScopesScenarios() {
      return Stream.of(
          RetrieveScopesScenario.displayName("anonymous DOCUMENT no authorizations -> empty")
              .whenAnonymous()
              .accessesResource(AuthorizationResourceType.DOCUMENT, PermissionType.READ)
              .thenResultContains()
              .build(),
          RetrieveScopesScenario.displayName("user alice wildcard + specific -> ['*','res-1']")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.DOCUMENT,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.DOCUMENT,
                      AuthorizationResourceMatcher.ID,
                      "res-1",
                      Set.of(PermissionType.READ)))
              .whenUser("alice")
              .accessesResource(AuthorizationResourceType.DOCUMENT, PermissionType.READ)
              .thenResultContains(WILDCARD_RESOURCE_ID, "res-1")
              .build(),
          RetrieveScopesScenario.displayName("user + group union -> ['user-res','group-res']")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.MESSAGE,
                      AuthorizationResourceMatcher.ID,
                      "user-res",
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.GROUP,
                      "groupA",
                      AuthorizationResourceType.MESSAGE,
                      AuthorizationResourceMatcher.ID,
                      "group-res",
                      Set.of(PermissionType.READ)))
              .whenUser("alice")
              .withGroups("groupA")
              .accessesResource(AuthorizationResourceType.MESSAGE, PermissionType.READ)
              .thenResultContains("user-res", "group-res")
              .build(),
          RetrieveScopesScenario.displayName(
                  "duplicate resource ids from user + group retained -> ['dup-res','dup-res']")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      "dup-res",
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.GROUP,
                      "groupA",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      "dup-res",
                      Set.of(PermissionType.READ)))
              .whenUser("alice")
              .withGroups("groupA")
              .accessesResource(AuthorizationResourceType.AUTHORIZATION, PermissionType.READ)
              .thenResultContains("dup-res", "dup-res")
              .build(),
          RetrieveScopesScenario.displayName("different permission excluded -> ['res-read']")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      "res-read",
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      "res-update",
                      Set.of(PermissionType.UPDATE)))
              .whenUser("alice")
              .accessesResource(AuthorizationResourceType.AUTHORIZATION, PermissionType.READ)
              .thenResultContains("res-read")
              .build(),
          RetrieveScopesScenario.displayName(
                  "multiple owner types aggregated -> ['client-res','role-res','mr-res']")
              .given(
                  authEntity(
                      AuthorizationOwnerType.CLIENT,
                      "clientX",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      "client-res",
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.ROLE,
                      "roleY",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      "role-res",
                      Set.of(PermissionType.READ)),
                  authEntity(
                      AuthorizationOwnerType.MAPPING_RULE,
                      "mrZ",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ID,
                      "mr-res",
                      Set.of(PermissionType.READ)))
              .whenClient("clientX")
              .withRoles("roleY")
              .withMappingRules("mrZ")
              .accessesResource(AuthorizationResourceType.AUTHORIZATION, PermissionType.READ)
              .thenResultContains("client-res", "role-res", "mr-res")
              .build(),
          RetrieveScopesScenario.displayName(
                  "user alice legacy authz (resourcePropertyName = null) wildcard -> ['*']")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      // Simulate "8.8 persisted" entity, the field didn't exist, returned as `null`
                      null,
                      Set.of(PermissionType.READ)))
              .whenUser("alice")
              .accessesResource(AuthorizationResourceType.AUTHORIZATION, PermissionType.READ)
              .thenResultContains(WILDCARD_RESOURCE_ID)
              .build(),
          RetrieveScopesScenario.displayName("resource type mismatch -> empty")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.PROCESS_DEFINITION,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ_PROCESS_DEFINITION)))
              .whenUser("alice")
              .accessesResource(AuthorizationResourceType.AUTHORIZATION, PermissionType.READ)
              .thenResultContains()
              .build(),
          RetrieveScopesScenario.displayName("permission type mismatch -> empty")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)))
              .whenUser("alice")
              .accessesResource(AuthorizationResourceType.AUTHORIZATION, PermissionType.UPDATE)
              .thenResultContains()
              .build(),
          RetrieveScopesScenario.displayName("only wildcard -> ['*']")
              .given(
                  authEntity(
                      AuthorizationOwnerType.USER,
                      "alice",
                      AuthorizationResourceType.AUTHORIZATION,
                      AuthorizationResourceMatcher.ANY,
                      WILDCARD_RESOURCE_ID,
                      Set.of(PermissionType.READ)))
              .whenUser("alice")
              .accessesResource(AuthorizationResourceType.AUTHORIZATION, PermissionType.READ)
              .thenResultContains(WILDCARD_RESOURCE_ID)
              .build());
    }

    private AuthorizationEntity authEntity(
        final AuthorizationOwnerType ownerType,
        final String ownerId,
        final AuthorizationResourceType resourceType,
        final AuthorizationResourceMatcher matcher,
        final String resourceId,
        final Set<PermissionType> permissionTypes) {
      return authEntity(
          ownerType, ownerId, resourceType, matcher, resourceId, EMPTY_STRING, permissionTypes);
    }

    private AuthorizationEntity authEntity(
        final AuthorizationOwnerType ownerType,
        final String ownerId,
        final AuthorizationResourceType resourceType,
        final AuthorizationResourceMatcher matcher,
        final String resourceId,
        final String resourcePropertyName,
        final Set<PermissionType> permissionTypes) {
      return new AuthorizationEntity(
          keyCounter.incrementAndGet(),
          ownerId,
          ownerType.name(),
          resourceType.name(),
          matcher.value(),
          resourceId,
          resourcePropertyName,
          permissionTypes);
    }

    record RetrieveScopesScenario(
        String displayName,
        CamundaAuthentication authentication,
        AuthorizationResourceType resourceType,
        PermissionType permissionType,
        List<AuthorizationEntity> given,
        List<AuthorizationScope> expected) {

      @Override
      public String toString() {
        return displayName;
      }

      static Builder displayName(final String displayName) {
        return new Builder(displayName);
      }

      static final class Builder {
        private final String displayName;
        private String user;
        private String clientId;
        private final List<String> groupIds = new ArrayList<>();
        private final List<String> mappingRuleIds = new ArrayList<>();
        private final List<String> roleIds = new ArrayList<>();
        private AuthorizationResourceType resourceType;
        private PermissionType permissionType;
        private final List<AuthorizationEntity> givenAuthorizationEntities = new ArrayList<>();
        private final List<AuthorizationScope> expectedScopes = new ArrayList<>();

        Builder(final String displayName) {
          this.displayName = displayName;
        }

        Builder given(final AuthorizationEntity... authorizationEntities) {
          if (authorizationEntities != null) {
            givenAuthorizationEntities.addAll(Arrays.asList(authorizationEntities));
          }
          return this;
        }

        Builder whenAnonymous() {
          user = null;
          clientId = null;
          groupIds.clear();
          mappingRuleIds.clear();
          roleIds.clear();
          return this;
        }

        Builder whenUser(final String user) {
          this.user = user;
          return this;
        }

        Builder whenClient(final String clientId) {
          this.clientId = clientId;
          return this;
        }

        Builder withGroups(final String... groupIds) {
          if (groupIds != null) {
            this.groupIds.addAll(Arrays.asList(groupIds));
          }
          return this;
        }

        Builder withMappingRules(final String... mappingRuleIds) {
          if (mappingRuleIds != null) {
            this.mappingRuleIds.addAll(Arrays.asList(mappingRuleIds));
          }
          return this;
        }

        Builder withRoles(final String... roleIds) {
          if (roleIds != null) {
            this.roleIds.addAll(Arrays.asList(roleIds));
          }
          return this;
        }

        Builder accessesResource(
            final AuthorizationResourceType resourceType, final PermissionType permissionType) {
          this.resourceType = resourceType;
          this.permissionType = permissionType;
          return this;
        }

        Builder thenResultContains(final String... resourceIds) {
          if (resourceIds != null) {
            Arrays.stream(resourceIds).forEach(id -> expectedScopes.add(AuthorizationScope.of(id)));
          }
          return this;
        }

        RetrieveScopesScenario build() {
          final CamundaAuthentication authentication;
          if (user == null
              && clientId == null
              && groupIds.isEmpty()
              && mappingRuleIds.isEmpty()
              && roleIds.isEmpty()) {
            authentication = CamundaAuthentication.anonymous();
          } else {
            authentication =
                CamundaAuthentication.of(
                    a -> {
                      if (user != null) {
                        a.user(user);
                      }
                      if (clientId != null) {
                        a.clientId(clientId);
                      }
                      if (!groupIds.isEmpty()) {
                        a.groupIds(groupIds);
                      }
                      if (!mappingRuleIds.isEmpty()) {
                        a.mappingRule(mappingRuleIds);
                      }
                      if (!roleIds.isEmpty()) {
                        a.roleIds(roleIds);
                      }
                      return a;
                    });
          }
          return new RetrieveScopesScenario(
              displayName,
              authentication,
              resourceType,
              permissionType,
              List.copyOf(givenAuthorizationEntities),
              List.copyOf(expectedScopes));
        }
      }
    }
  }
}
