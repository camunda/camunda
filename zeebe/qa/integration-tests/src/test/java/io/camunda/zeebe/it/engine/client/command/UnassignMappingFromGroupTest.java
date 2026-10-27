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
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private String groupId;
  private String mappingId;

  @BeforeEach
  void initClientAndInstances() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    mappingId =
        client
            .newCreateMappingRuleCommand()
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
    client
        .newAssignMappingRuleToGroupCommand()
        .mappingRuleId(mappingId)
        .groupId(groupId)
        .send()
        .join();
  }

  @Test
  void shouldUnassignMappingFromGroup() {
    // when
    client
        .newUnassignMappingRuleFromGroupCommand()
        .mappingRuleId(mappingId)
        .groupId(groupId)
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertEntityUnassignedFromGroup(
        groupId,
        mappingId,
        group -> {
          assertThat(group).hasEntityType(EntityType.MAPPING_RULE);
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
                    .newUnassignMappingRuleFromGroupCommand()
                    .mappingRuleId(mappingId)
                    .groupId(nonExistentGroupId)
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
                client
                    .newUnassignMappingRuleFromGroupCommand()
                    .mappingRuleId(mappingId)
                    .groupId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingRuleFromGroupCommand()
                    .mappingRuleId(mappingId)
                    .groupId("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  @Test
  void shouldRejectIfMissingMappingId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingRuleFromGroupCommand()
                    .mappingRuleId(null)
                    .groupId(groupId)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be null");
  }

  @Test
  void shouldRejectIfEmptyMappingId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingRuleFromGroupCommand()
                    .mappingRuleId("")
                    .groupId(groupId)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be empty");
  }
}
