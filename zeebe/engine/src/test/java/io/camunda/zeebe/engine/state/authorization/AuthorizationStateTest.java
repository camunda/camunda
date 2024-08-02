/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class AuthorizationStateTest {
  private MutableProcessingState processingState;
  private MutableAuthorizationState authorizationState;

  @BeforeEach
  public void setup() {
    authorizationState = processingState.getAuthorizationState();
  }

  @DisplayName("should return null if no authorization for owner and resource is not exist")
  @Test
  void shouldReturnNullIfNoAuthorizationForOwnerAndResourceExists() {
    // when
    final var persistedAuth =
        authorizationState.getAuthorization(
            "owner" + UUID.randomUUID(),
            AuthorizationOwnerType.USER.name(),
            "resource",
            "resource-type");
    // then
    assertThat(persistedAuth).isNull();
  }

  @DisplayName(
      "should create authorization if no authorization for owner and resource does not exist")
  @Test
  void shouldCreateIfUsernameDoesNotExist() {
    // when
    final AuthorizationRecord authorizationRecord =
        new AuthorizationRecord()
            .setOwnerKey("owner" + UUID.randomUUID())
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceKey("resource")
            .setResourceType("resourceType")
            .setPermissions(List.of("write:*"));
    authorizationState.createAuthorization(authorizationRecord);

    // then
    final var persistedAuthorization =
        authorizationState.getAuthorization(
            authorizationRecord.getOwnerKey(),
            authorizationRecord.getOwnerType(),
            authorizationRecord.getResourceKey(),
            authorizationRecord.getResourceType());
    assertThat(persistedAuthorization.getPermissions())
        .isEqualTo(authorizationRecord.getPermissions());
  }

  @DisplayName(
      "should create authorization throws exception when authorization for owner and resource already exist")
  @Test
  void shouldThrowExceptionInCreateIfUsernameDoesNotExist() {
    // given
    final AuthorizationRecord authorizationRecord =
        new AuthorizationRecord()
            .setOwnerKey("owner" + UUID.randomUUID())
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceKey("resource")
            .setResourceType("resourceType")
            .setPermissions(List.of("write:*"));
    authorizationState.createAuthorization(authorizationRecord);

    // when/then
    assertThatThrownBy(() -> authorizationState.createAuthorization(authorizationRecord))
        .isInstanceOf(ZeebeDbInconsistentException.class);
  }
}
