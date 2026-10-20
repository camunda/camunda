/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbAuthorizationStateVersionTest {

  private MutableProcessingState processingState;
  private MutableAuthorizationState authorizationState;
  private MutablePolicyUpdateState policyUpdateState;

  @BeforeEach
  void setup() {
    authorizationState = processingState.getAuthorizationState();
    policyUpdateState = processingState.getPolicyUpdateState();
  }

  @Test
  void shouldReadAuthorizationWrittenWithoutPolicyUpdate() {
    // when
    authorizationState.create(1L, authorization("r", 0, PermissionType.READ));

    // then
    assertThat(authorizationState.get(1L)).isPresent();
    assertThat(scopes(PermissionType.READ, 0)).hasSize(1);
    assertThat(authorizationState.getAuthorizationKeysForOwner(AuthorizationOwnerType.ROLE, "r"))
        .containsExactly(1L);
  }

  @Test
  void shouldHidePendingAuthorizationUntilTheSwitch() {
    // when
    authorizationState.create(1L, authorization("r", 2, PermissionType.READ));

    // then: a user command does not see it, and a command of version 2 does
    assertThat(authorizationState.get(1L)).isEmpty();
    assertThat(scopes(PermissionType.READ, 0)).isEmpty();
    assertThat(authorizationState.getAuthorizationKeysForOwner(AuthorizationOwnerType.ROLE, "r"))
        .isEmpty();
    assertThat(authorizationState.get(1L, 2)).isPresent();
    assertThat(scopes(PermissionType.READ, 2)).hasSize(1);

    // when
    policyUpdateState.setCurrentVersion(2);

    // then
    assertThat(authorizationState.get(1L)).isPresent();
    assertThat(scopes(PermissionType.READ, 0)).hasSize(1);
  }

  @Test
  void shouldKeepOldPermissionsVisibleUntilAPendingUpdateIsSwitched() {
    // given
    authorizationState.create(1L, authorization("r", 0, PermissionType.READ));

    // when: a pending update replaces READ with UPDATE
    authorizationState.update(1L, authorization("r", 3, PermissionType.UPDATE));

    // then
    assertThat(scopes(PermissionType.READ, 0)).hasSize(1);
    assertThat(scopes(PermissionType.UPDATE, 0)).isEmpty();

    // when
    policyUpdateState.setCurrentVersion(3);

    // then
    assertThat(scopes(PermissionType.READ, 0)).isEmpty();
    assertThat(scopes(PermissionType.UPDATE, 0)).hasSize(1);
  }

  @Test
  void shouldHideAuthorizationDeletedAtCurrentVersion() {
    // given
    authorizationState.create(1L, authorization("r", 0, PermissionType.READ));

    // when
    authorizationState.delete(1L, 1);
    policyUpdateState.setCurrentVersion(1);

    // then
    assertThat(authorizationState.get(1L)).isEmpty();
    assertThat(scopes(PermissionType.READ, 0)).isEmpty();
    assertThat(authorizationState.getAuthorizationKeysForOwner(AuthorizationOwnerType.ROLE, "r"))
        .isEmpty();
  }

  private Set<?> scopes(final PermissionType permission, final long commandVersion) {
    return authorizationState.getAuthorizationScopes(
        AuthorizationOwnerType.ROLE,
        "r",
        AuthorizationResourceType.AUTHORIZATION,
        permission,
        commandVersion);
  }

  private static AuthorizationRecord authorization(
      final String ownerId, final long version, final PermissionType permission) {
    return new AuthorizationRecord()
        .setOwnerType(AuthorizationOwnerType.ROLE)
        .setOwnerId(ownerId)
        .setResourceType(AuthorizationResourceType.AUTHORIZATION)
        .setResourceMatcher(AuthorizationResourceMatcher.ANY)
        .setResourceId("*")
        .setPermissionTypes(Set.of(permission))
        .setPolicyVersion(version);
  }
}
