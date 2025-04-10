/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Enable with https://github.com/camunda/camunda/issues/29925")
@ZeebeIntegration
class AssignUserToGroupTest {

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private long groupKey;
  private long userKey;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    userKey =
        client
            .newUserCreateCommand()
            .name("User Name")
            .username("user")
            .email("foo@example.com")
            .password("******")
            .send()
            .join()
            .getUserKey();

    groupKey = client.newCreateGroupCommand().name("groupName").send().join().getGroupKey();
  }

  @Test
  void shouldAssignUserToGroup() {
    // when
    client.newAssignUserToGroupCommand(groupKey).userKey(userKey).send().join();

    // then
    ZeebeAssertHelper.assertEntityAssignedToGroup(
        groupKey,
        userKey,
        group -> {
          assertThat(group).hasEntityType(EntityType.USER);
        });
  }

  @Test
  void shouldRejectIfUserDoesNotExist() {
    // given
    final long nonExistentUserKey = Protocol.encodePartitionId(1, 111L);

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignUserToGroupCommand(groupKey)
                    .userKey(nonExistentUserKey)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to add an entity with key '%d' and type '%s' to group with key '%d', but the entity does not exist."
                .formatted(nonExistentUserKey, EntityType.USER, groupKey));
  }

  @Test
  void shouldRejectIfGroupDoesNotExist() {
    // given
    final long nonExistentGroupKey = Protocol.encodePartitionId(1, 111L);

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignUserToGroupCommand(nonExistentGroupKey)
                    .userKey(userKey)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to update group with key '%s', but a group with this key does not exist."
                .formatted(nonExistentGroupKey));
  }

  @Test
  void shouldRejectIfAlreadyAssigned() {
    // given
    client.newAssignUserToGroupCommand(groupKey).userKey(userKey).send().join();

    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToGroupCommand(groupKey).userKey(userKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining(
            "Expected to add entity with key '%d' to group with key '%d', but the entity is already assigned to this group."
                .formatted(userKey, groupKey));
  }
}
