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
class UpdateGroupTest {

  private static final String UPDATED_GROUP_NAME = "Updated Group Name";

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private long groupKey;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    groupKey =
        client.newCreateGroupCommand().name("Initial Group Name").send().join().getGroupKey();
  }

  @Test
  void shouldUpdateGroupName() {
    // when
    client.newUpdateGroupCommand(groupKey).updateName(UPDATED_GROUP_NAME).send().join();

    // then
    ZeebeAssertHelper.assertGroupUpdated(
        UPDATED_GROUP_NAME,
        group -> assertThat(group).hasGroupKey(groupKey).hasName(UPDATED_GROUP_NAME));
  }

  @Test
  void shouldRejectUpdateIfNameIsNull() {
    // when / then
    assertThatThrownBy(() -> client.newUpdateGroupCommand(groupKey).updateName(null).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("No name provided");
  }

  @Test
  void shouldRejectUpdateIfGroupDoesNotExist() {
    // when / then
    final long notExistingGroupKey = Protocol.encodePartitionId(1, 111L);
    assertThatThrownBy(
            () ->
                client
                    .newUpdateGroupCommand(notExistingGroupKey)
                    .updateName("Non-Existent Group Name")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to update group with key '%d', but a group with this key does not exist."
                .formatted(notExistingGroupKey));
  }
}
