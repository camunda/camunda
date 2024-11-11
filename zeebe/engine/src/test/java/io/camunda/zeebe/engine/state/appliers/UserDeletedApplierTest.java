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
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
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

    authorizationState.insertOwnerTypeByKey(userRecord.getUserKey(), AuthorizationOwnerType.USER);

    authorizationState.createOrAddPermission(
        userRecord.getUserKey(), PROCESS_DEFINITION, CREATE, Set.of("process1", "process2"));

    authorizationState.createOrAddPermission(
        userRecord.getUserKey(), DECISION_DEFINITION, DELETE, Set.of("definition1"));

    final var ownerType = authorizationState.getOwnerType(userRecord.getUserKey());

    assertThat(ownerType).isPresent();
    assertThat(ownerType.get()).isEqualTo(AuthorizationOwnerType.USER);

    assertThat(
            authorizationState.getResourceIdentifiers(
                userRecord.getUserKey(), PROCESS_DEFINITION, CREATE))
        .hasSize(2)
        .containsExactlyInAnyOrder("process1", "process2");

    assertThat(
            authorizationState.getResourceIdentifiers(
                userRecord.getUserKey(), DECISION_DEFINITION, DELETE))
        .hasSize(1)
        .containsExactlyInAnyOrder("definition1");

    // when
    userDeletedApplier.applyState(userRecord.getUserKey(), userRecord);

    // then
    assertThat(authorizationState.getOwnerType(userRecord.getUserKey())).isEmpty();
    assertThat(
            authorizationState.getResourceIdentifiers(
                userRecord.getUserKey(), PROCESS_DEFINITION, CREATE))
        .hasSize(0);
    assertThat(
            authorizationState.getResourceIdentifiers(
                userRecord.getUserKey(), DECISION_DEFINITION, DELETE))
        .hasSize(0);
    assertThat(userState.getUser(userRecord.getUserKey())).isEmpty();
  }
}
