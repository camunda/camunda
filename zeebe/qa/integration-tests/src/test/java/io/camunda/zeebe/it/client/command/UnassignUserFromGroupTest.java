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
import io.camunda.client.api.response.CreateUserResponse;
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
public class UnassignUserFromGroupTest {
  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private long groupKey;
  private CreateUserResponse createdUser;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();

    createdUser =
        client
            .newUserCreateCommand()
            .name("User Name")
            .username("user")
            .email("foo@example.com")
            .password("******")
            .send()
            .join();

    groupKey = client.newCreateGroupCommand().name("groupName").send().join().getGroupKey();
    client.newAssignUserToGroupCommand(groupKey).username(createdUser.getUsername()).send().join();
  }

  @Test
  void shouldUnassignUserFromGroup() {
    // when
    client
        .newUnassignUserFromGroupCommand(groupKey)
        .username(createdUser.getUsername())
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertEntityUnassignedFromGroup(
        groupKey,
        createdUser.getUsername(),
        group -> {
          assertThat(group).hasEntityType(EntityType.USER);
        });
  }

  @Test
  void shouldRejectIfUserDoesNotExist() {
    // given
    final var nonExistentUsername = String.valueOf(Protocol.encodePartitionId(1, 111L));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignUserFromGroupCommand(groupKey)
                    .username(nonExistentUsername)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to remove an entity with ID '%s' and type '%s' from group with key '%d', but the entity does not exist."
                .formatted(nonExistentUsername, EntityType.USER, groupKey));
  }

  @Test
  void shouldRejectIfGroupDoesNotExist() {
    // given
    final long nonExistentGroupKey = Protocol.encodePartitionId(1, 111L);

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignUserFromGroupCommand(nonExistentGroupKey)
                    .username("nonExistentUsername")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to update group with key '%s', but a group with this key does not exist."
                .formatted(nonExistentGroupKey));
  }
}
