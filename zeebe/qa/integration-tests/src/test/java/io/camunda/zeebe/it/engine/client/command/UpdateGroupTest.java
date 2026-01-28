/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class UpdateGroupTest {

  private static final String UPDATED_GROUP_NAME = "Updated Group Name";
  private static final String UPDATED_GROUP_DESCRIPTION = "Updated Group Description";

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private String groupId;

  @BeforeEach
  void initClientAndInstances() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    groupId =
        client
            .newCreateGroupCommand()
            .groupId(Strings.newRandomValidIdentityId())
            .name("Initial Group Name")
            .description("Initial Group Description")
            .send()
            .join()
            .getGroupId();
  }

  @Test
  void shouldUpdateGroup() {
    // when
    client
        .newUpdateGroupCommand(groupId)
        .name(UPDATED_GROUP_NAME)
        .description(UPDATED_GROUP_DESCRIPTION)
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertGroupUpdated(
        UPDATED_GROUP_NAME,
        group ->
            assertThat(group)
                .hasGroupId(groupId)
                .hasName(UPDATED_GROUP_NAME)
                .hasDescription(UPDATED_GROUP_DESCRIPTION));
  }

  @Test
  void shouldUpdateGroupWithEmptyDescription() {
    // when
    client.newUpdateGroupCommand(groupId).name(UPDATED_GROUP_NAME).description("").send().join();

    // then
    ZeebeAssertHelper.assertGroupUpdated(
        UPDATED_GROUP_NAME,
        group ->
            assertThat(group).hasGroupId(groupId).hasName(UPDATED_GROUP_NAME).hasDescription(""));
  }

  @Test
  void shouldRejectUpdateIfNameIsNull() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateGroupCommand(groupId)
                    .name(null)
                    .description(UPDATED_GROUP_DESCRIPTION)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRejectUpdateIfDescriptionIsNull() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateGroupCommand(groupId)
                    .name(UPDATED_GROUP_NAME)
                    .description(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("description must not be null");
  }

  @Test
  void shouldRejectUpdateIfGroupDoesNotExist() {
    // when / then
    final String notExistingGroupId = "notExistingGroupId";
    assertThatThrownBy(
            () ->
                client
                    .newUpdateGroupCommand(notExistingGroupId)
                    .name("Non-Existent Group Name")
                    .description("Non-Existent Group Description")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to update group with ID '%s', but a group with this ID does not exist."
                .formatted(notExistingGroupId));
  }
}
