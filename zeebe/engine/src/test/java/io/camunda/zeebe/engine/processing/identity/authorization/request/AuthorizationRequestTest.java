/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Map;
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
  class EqualsAndHashCodeTest {

    @Test
    void shouldBeEqualWhenBuiltWithSameParameters() {
      // given
      final var request1 =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of(Authorization.AUTHORIZED_USERNAME, "user"))
              .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
              .permissionType(PermissionType.READ)
              .addResourceId("resource-1")
              .tenantId("tenant-1")
              .isNewResource(true)
              .build();

      final var request2 =
          AuthorizationRequest.builder()
              .authorizationClaims(Map.of(Authorization.AUTHORIZED_USERNAME, "user"))
              .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
              .permissionType(PermissionType.READ)
              .addResourceId("resource-1")
              .tenantId("tenant-1")
              .isNewResource(true)
              .build();

      // when / then
      assertThat(request1).isEqualTo(request2);
      assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @ParameterizedTest
    @MethodSource("unequalRequestInputs")
    void shouldNotBeEqualWhenBuiltWithDifferentParameters(
        final AuthorizationRequest request1, final AuthorizationRequest request2) {
      // when / then
      assertThat(request1).isNotEqualTo(request2);
      assertThat(request1.hashCode()).isNotEqualTo(request2.hashCode());
    }

    Stream<Arguments> unequalRequestInputs() {
      return Stream.of(
          // Different resource type
          Arguments.of(
              baseRequestBuilder()
                  .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                  .build(),
              baseRequestBuilder()
                  .resourceType(AuthorizationResourceType.DECISION_DEFINITION)
                  .build()),
          // Different permission type
          Arguments.of(
              baseRequestBuilder().permissionType(PermissionType.READ).build(),
              baseRequestBuilder().permissionType(PermissionType.UPDATE).build()),
          // Different resource ids
          Arguments.of(
              baseRequestBuilder().addResourceId("id1").build(),
              baseRequestBuilder().addResourceId("id2").build()),
          // Different tenant id
          Arguments.of(
              baseRequestBuilder().tenantId("tenant-1").build(),
              baseRequestBuilder().tenantId("tenant-2").build()),
          // Different isNewResource
          Arguments.of(
              baseRequestBuilder().isNewResource(true).build(),
              baseRequestBuilder().isNewResource(false).build()),
          // Different claims
          Arguments.of(
              AuthorizationRequest.builder()
                  .authorizationClaims(Map.of(Authorization.AUTHORIZED_USERNAME, "userA"))
                  .resourceType(AuthorizationResourceType.RESOURCE)
                  .permissionType(PermissionType.READ)
                  .build(),
              AuthorizationRequest.builder()
                  .authorizationClaims(Map.of(Authorization.AUTHORIZED_USERNAME, "userB"))
                  .resourceType(AuthorizationResourceType.RESOURCE)
                  .permissionType(PermissionType.READ)
                  .build()));
    }

    private AuthorizationRequest.Builder baseRequestBuilder() {
      return AuthorizationRequest.builder()
          .authorizationClaims(Map.of())
          .resourceType(AuthorizationResourceType.RESOURCE)
          .permissionType(PermissionType.READ);
    }
  }
}
