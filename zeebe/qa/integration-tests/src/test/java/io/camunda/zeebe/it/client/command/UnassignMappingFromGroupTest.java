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
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class UnassignMappingFromGroupTest {

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private String groupId;
  private String mappingId;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    mappingId =
        client
            .newCreateMappingCommand()
            .mappingRuleId(Strings.newRandomValidIdentityId())
            .name("mappingName")
            .claimName("name")
            .claimValue("value")
            .send()
            .join()
            .getMappingRuleId();

    groupId =
        client
            .newCreateGroupCommand()
            .groupId(Strings.newRandomValidIdentityId())
            .name("groupName")
            .send()
            .join()
            .getGroupId();
    client.newAssignMappingToGroupCommand(groupId).mappingId(mappingId).send().join();
  }

  @Test
  void shouldUnassignMappingFromGroup() {
    // when
    client.newUnassignMappingFromGroupCommand(groupId).mappingId(mappingId).send().join();

    // then
    ZeebeAssertHelper.assertEntityUnassignedFromGroup(
        groupId,
        mappingId,
        group -> {
          assertThat(group).hasEntityType(EntityType.MAPPING);
        });
  }

  @Test
  void shouldRejectIfGroupDoesNotExist() {
    // given
    final String nonExistentGroupId = Strings.newRandomValidIdentityId();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingFromGroupCommand(nonExistentGroupId)
                    .mappingId(mappingId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to update group with ID '%s', but a group with this ID does not exist."
                .formatted(nonExistentGroupId));
  }

  @Test
  void shouldRejectIfMissingGroupId() {
    // when / then
    assertThatThrownBy(
            () ->
                client.newUnassignMappingFromGroupCommand(null).mappingId(mappingId).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(
            () -> client.newUnassignMappingFromGroupCommand("").mappingId(mappingId).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  @Test
  void shouldRejectIfMissingMappingId() {
    // when / then
    assertThatThrownBy(
            () ->
                client.newUnassignMappingFromGroupCommand("groupId").mappingId(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingId must not be null");
  }

  @Test
  void shouldRejectIfEmptyMappingId() {
    // when / then
    assertThatThrownBy(
            () -> client.newUnassignMappingFromGroupCommand("groupId").mappingId("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingId must not be empty");
  }
}
