/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class CreateGroupTest {

  private static final String GROUP_ID = "groupId";
  private static final String GROUP_NAME = "groupName";
  private static final String GROUP_DESCRIPTION = "groupDescription";

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;

  @BeforeEach
  void initClientAndInstances() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldCreateGroup() {
    // when
    final var response =
        client
            .newCreateGroupCommand()
            .groupId(GROUP_ID)
            .name(GROUP_NAME)
            .description(GROUP_DESCRIPTION)
            .send()
            .join();

    // then
    assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
    assertThat(response.getName()).isEqualTo(GROUP_NAME);
    assertThat(response.getDescription()).isEqualTo(GROUP_DESCRIPTION);
    ZeebeAssertHelper.assertGroupCreated(
        GROUP_NAME,
        (group) -> {
          assertThat(group).hasGroupId(GROUP_ID);
          assertThat(group).hasName(GROUP_NAME);
          assertThat(group).hasDescription(GROUP_DESCRIPTION);
        });
  }

  @Test
  void shouldCreateGroupWithNoDescription() {
    // when
    final var response =
        client.newCreateGroupCommand().groupId(GROUP_ID).name(GROUP_NAME).send().join();

    // then
    assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
    assertThat(response.getName()).isEqualTo(GROUP_NAME);
    assertThat(response.getDescription()).isEqualTo("");
    ZeebeAssertHelper.assertGroupCreated(
        GROUP_NAME,
        (group) -> {
          assertThat(group).hasGroupId(GROUP_ID);
          assertThat(group).hasName(GROUP_NAME);
          assertThat(group).hasDescription("");
        });
  }

  @Test
  void shouldRejectIfGroupIdAlreadyExists() {
    // given
    client.newCreateGroupCommand().groupId(GROUP_ID).name(GROUP_NAME).send().join();

    // when / then
    assertThatThrownBy(
            () -> client.newCreateGroupCommand().groupId(GROUP_ID).name(GROUP_NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a group with this ID already exists");
  }

  @Test
  void shouldRejectIfMissingGroupId() {
    // when / then
    assertThatThrownBy(() -> client.newCreateGroupCommand().groupId(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectIfMissingGroupName() {
    // when / then
    assertThatThrownBy(() -> client.newCreateGroupCommand().groupId(GROUP_ID).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }
}
