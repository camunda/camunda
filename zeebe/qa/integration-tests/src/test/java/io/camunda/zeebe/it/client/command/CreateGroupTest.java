/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Enable with https://github.com/camunda/camunda/issues/29925")
@ZeebeIntegration
class CreateGroupTest {

  private static final String GROUP_NAME = "groupName";

  @AutoClose CamundaClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldCreateGroup() {
    // when
    final var response = client.newCreateGroupCommand().name(GROUP_NAME).send().join();

    // then
    assertThat(response.getGroupKey()).isGreaterThan(0);
    ZeebeAssertHelper.assertGroupCreated(
        GROUP_NAME,
        (group) -> {
          assertThat(group).hasName(GROUP_NAME);
        });
  }

  @Test
  void shouldRejectIfGroupNameAlreadyExists() {
    // given
    client.newCreateGroupCommand().name(GROUP_NAME).send().join();

    // when / then
    assertThatThrownBy(() -> client.newCreateGroupCommand().name(GROUP_NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a group with this name already exists");
  }

  @Test
  void shouldRejectIfMissingGroupName() {
    // when / then
    assertThatThrownBy(() -> client.newCreateGroupCommand().send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }
}
