/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuthorizationRequestTest {

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class BuildAuthorizationRequestValidationTest {

    @Test
    void shouldThrowWhenResourceTypeIsNull() {
      // given
      final var builder =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of())
              .permissionType(PermissionType.READ);

      // when / then
      assertThatThrownBy(builder::build)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("resourceType must be set");
    }

    @Test
    void shouldThrowWhenPermissionTypeIsNull() {
      // given
      final var builder =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of())
              .resourceType(AuthorizationResourceType.PROCESS_DEFINITION);

      // when / then
      assertThatThrownBy(builder::build)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("permissionType must be set");
    }

    @Test
    void shouldThrowWhenNeitherCommandNorClaimsAreProvided() {
      // given
      final var builder =
          AuthorizationRequest.builder()
              .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
              .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE);

      // when / then
      assertThatThrownBy(builder::build)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("command or authorizationClaims must be provided");
    }

    @Test
    void shouldThrowWhenBothCommandAndClaimsAreProvided() {
      // given
      final var command = mock(TypedRecord.class);
      when(command.getAuthorizations())
          .thenReturn(Map.of(Authorization.AUTHORIZED_USERNAME, "demo-user_A"));

      final var builder =
          AuthorizationRequest.builder()
              .command(command)
              .authorizationClaims(Map.of(Authorization.AUTHORIZED_USERNAME, "demo-user_B"))
              .resourceType(AuthorizationResourceType.RESOURCE)
              .permissionType(PermissionType.DELETE_FORM);

      // when / then
      assertThatThrownBy(builder::build)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("command and authorizationClaims are mutually exclusive");
    }

    @ParameterizedTest
    @MethodSource("emptyResourceIdInputs")
    void shouldIgnoreEmptyResourceIds(final String resourceId) {
      // given / when
      final var request =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of())
              .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
              .permissionType(PermissionType.READ_PROCESS_INSTANCE)
              .addResourceId(resourceId)
              .build();

      // then
      assertThat(request.resourceIds()).isEmpty();
    }

    Stream<Arguments> emptyResourceIdInputs() {
      return Stream.of(Arguments.of((String) null), Arguments.of(""));
    }

    @ParameterizedTest
    @MethodSource("emptyResourcePropertyInputs")
    void shouldIgnoreEmptyResourceProperties(
        final String propertyName, final Object propertyValue) {
      // given / when
      final var request =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of())
              .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
              .permissionType(PermissionType.READ_PROCESS_INSTANCE)
              .addResourceProperty(propertyName, propertyValue)
              .build();

      // then
      assertThat(request.resourceProperties()).isEmpty();
    }

    Stream<Arguments> emptyResourcePropertyInputs() {
      return Stream.of(
          Arguments.of(null, "value"), Arguments.of("", "value"), Arguments.of("property", null));
    }

    @Test
    void shouldAddAllResourceIds() {
      // given / when
      final var request =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of())
              .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
              .permissionType(PermissionType.UPDATE_USER_TASK)
              .addAllResourceIds(List.of("id1", "id2", "id3"))
              .build();

      // then
      assertThat(request.resourceIds()).containsExactlyInAnyOrder("id1", "id2", "id3");
    }

    @Test
    void shouldAddAllResourceProperties() {
      // given / when
      final var request =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of())
              .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
              .permissionType(PermissionType.UPDATE_USER_TASK)
              .addResourceProperty("assignee", "user-1")
              .addResourceProperty("candidateUsers", List.of("user-2", "user-3"))
              .addResourceProperty("candidateGroups", List.of("group-1", "group-2"))
              .build();

      // then
      assertThat(request.resourceProperties())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  "assignee", "user-1",
                  "candidateUsers", List.of("user-2", "user-3"),
                  "candidateGroups", List.of("group-1", "group-2")));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class GetErrorMessageTest {

    @ParameterizedTest
    @MethodSource("forbiddenErrorMessageInputs")
    void shouldReturnCorrectForbiddenErrorMessage(
        final Set<String> resourceIds,
        final Map<String, Object> resourceProperties,
        final String expectedMessage) {
      // given
      final var builder =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of())
              .resourceType(AuthorizationResourceType.RESOURCE)
              .permissionType(PermissionType.READ);

      resourceIds.forEach(builder::addResourceId);
      resourceProperties.forEach(builder::addResourceProperty);

      final var request = builder.build();

      // when
      final var message = request.getForbiddenErrorMessage();

      // then
      assertThat(message).isEqualTo(expectedMessage);
    }

    Stream<Arguments> forbiddenErrorMessageInputs() {
      return Stream.of(
          // No resource ids and no resource properties
          Arguments.of(
              Collections.emptySet(),
              Collections.emptyMap(),
              "Insufficient permissions to perform operation 'READ' on resource 'RESOURCE'"),
          // With resource ids
          Arguments.of(
              Set.of("id1"),
              Collections.emptyMap(),
              "Insufficient permissions to perform operation 'READ' on resource 'RESOURCE', required resource identifiers are one of '[*, id1]'"),
          // With multiple resource ids (sorted alphabetically)
          Arguments.of(
              Set.of("id2", "id1", "id3"),
              Collections.emptyMap(),
              "Insufficient permissions to perform operation 'READ' on resource 'RESOURCE', required resource identifiers are one of '[*, id1, id2, id3]'"),
          // With resource properties
          Arguments.of(
              Collections.emptySet(),
              Map.of("prop1", "value1"),
              "Insufficient permissions to perform operation 'READ' on resource 'RESOURCE', resource did not match property constraints '[prop1]'"),
          // With multiple resource properties (sorted alphabetically)
          Arguments.of(
              Collections.emptySet(),
              Map.of("propB", "value2", "propA", "value1"),
              "Insufficient permissions to perform operation 'READ' on resource 'RESOURCE', resource did not match property constraints '[propA, propB]'"),
          // With multiple resource ids and properties (sorted alphabetically)
          Arguments.of(
              Set.of("id5", "id1", "id9"),
              Map.of("propD", "value2", "propY", "value1", "propA", "value1"),
              "Insufficient permissions to perform operation 'READ' on resource 'RESOURCE', required resource identifiers are one of '[*, id1, id5, id9]' "
                  + "or resource must match property constraints '[propA, propD, propY]'"));
    }

    @ParameterizedTest
    @MethodSource("tenantErrorMessageInputs")
    void shouldReturnCorrectTenantErrorMessage(
        final boolean isNewResource, final String tenantId, final String expectedMessage) {
      // given
      final var builder =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of())
              .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
              .permissionType(PermissionType.UPDATE_USER_TASK)
              .tenantId(tenantId)
              .isNewResource(isNewResource);

      final var request = builder.build();

      // when
      final var message = request.getTenantErrorMessage();

      // then
      assertThat(message).isEqualTo(expectedMessage);
    }

    Stream<Arguments> tenantErrorMessageInputs() {
      return Stream.of(
          // New resource - should use forbidden message
          Arguments.of(
              true,
              "tenant-1",
              "Expected to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION' for tenant 'tenant-1', but user is not assigned to this tenant"),
          // Existing resource - should use not found message
          Arguments.of(
              false,
              "tenant-2",
              "Expected to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION', but no resource was found for tenant 'tenant-2'"));
    }
  }
}
