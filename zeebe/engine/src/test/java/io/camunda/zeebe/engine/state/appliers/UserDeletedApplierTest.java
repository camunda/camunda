/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.CREATE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.DELETE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class UserDeletedApplierTest {
  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableUserState userState;
  private MutableAuthorizationState authorizationState;
  private UserDeletedApplier userDeletedApplier;

  @BeforeEach
  public void setup() {
    userState = processingState.getUserState();
    authorizationState = processingState.getAuthorizationState();
    userDeletedApplier = new UserDeletedApplier(processingState);
  }

  @Test
  void shouldDeleteAuthorizationsForAUser() {
    // given
    final var userRecord =
        new UserRecord()
            .setUserKey(1L)
            .setName("foo")
            .setUsername("foobar")
            .setEmail("foo@bar")
            .setPassword("password");
    userState.create(userRecord);

    authorizationState.create(
        1L,
        new AuthorizationRecord()
            .setAuthorizationKey(1L)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("process1")
            .setResourceType(PROCESS_DEFINITION)
            .setOwnerId(userRecord.getUsername())
            .setOwnerType(AuthorizationOwnerType.USER)
            .setPermissionTypes(Set.of(CREATE)));
    authorizationState.create(
        2L,
        new AuthorizationRecord()
            .setAuthorizationKey(2L)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("process2")
            .setResourceType(PROCESS_DEFINITION)
            .setOwnerId(userRecord.getUsername())
            .setOwnerType(AuthorizationOwnerType.USER)
            .setPermissionTypes(Set.of(CREATE)));
    authorizationState.create(
        3L,
        new AuthorizationRecord()
            .setAuthorizationKey(3L)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("definition1")
            .setResourceType(DECISION_DEFINITION)
            .setOwnerId(userRecord.getUsername())
            .setOwnerType(AuthorizationOwnerType.USER)
            .setPermissionTypes(Set.of(DELETE)));

    assertThat(
            authorizationState.getResourceIdentifiers(
                AuthorizationOwnerType.USER, userRecord.getUsername(), PROCESS_DEFINITION, CREATE))
        .hasSize(2)
        .containsExactlyInAnyOrder("process1", "process2");

    assertThat(
            authorizationState.getResourceIdentifiers(
                AuthorizationOwnerType.USER, userRecord.getUsername(), DECISION_DEFINITION, DELETE))
        .hasSize(1)
        .containsExactlyInAnyOrder("definition1");

    // when
    userDeletedApplier.applyState(userRecord.getUserKey(), userRecord);

    // then
    assertThat(userState.getUser(userRecord.getUserKey())).isEmpty();
  }
}
