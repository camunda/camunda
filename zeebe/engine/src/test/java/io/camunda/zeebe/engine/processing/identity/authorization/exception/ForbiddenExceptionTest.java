/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for ForbiddenException */
final class ForbiddenExceptionTest {

  @Test
  void shouldConstructMessageFromAuthorizationRequest() {
    // Given: Authorization request for CREATE on RESOURCE
    final var command = mockCommand();
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.RESOURCE)
            .permissionType(PermissionType.CREATE)
            .addResourceId("resource-1")
            .build();

    // When: Creating exception
    final var exception = new ForbiddenException(request);

    // Then: Message should contain permission and resource type
    assertThat(exception.getMessage())
        .contains("Insufficient permissions")
        .contains("CREATE")
        .contains("RESOURCE");
  }

  @Test
  void shouldHaveForbiddenRejectionType() {
    // Given: Any authorization request
    final var command = mockCommand();
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.USER_TASK)
            .permissionType(PermissionType.UPDATE)
            .build();

    // When: Creating exception
    final var exception = new ForbiddenException(request);

    // Then: Should always have FORBIDDEN rejection type
    assertThat(exception.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
  }

  @Test
  void shouldIncludeResourceTypeInMessage() {
    // Given: Request with specific resource type
    final var command = mockCommand();
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.READ)
            .addResourceId("process-123")
            .build();

    // When: Creating exception
    final var exception = new ForbiddenException(request);

    // Then: Message should contain resource type
    assertThat(exception.getMessage()).contains("PROCESS_DEFINITION");
  }

  @Test
  void shouldHandleMultipleResourceIds() {
    // Given: Request with multiple resource IDs
    final var command = mockCommand();
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.RESOURCE)
            .permissionType(PermissionType.DELETE)
            .addResourceId("resource-1")
            .addResourceId("resource-2")
            .build();

    // When: Creating exception
    final var exception = new ForbiddenException(request);

    // Then: Message should be constructed successfully
    assertThat(exception.getMessage())
        .contains("Insufficient permissions")
        .contains("DELETE")
        .contains("RESOURCE");
  }

  private TypedRecord<?> mockCommand() {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations()).thenReturn(Map.of("username", "testUser"));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }
}
