/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Mapping;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class MappingsByGroupSearchTest {

  private static CamundaClient camundaClient;

  private static final String MAPPING_ID_1 = "a" + Strings.newRandomValidUsername();
  private static final String MAPPING_ID_2 = "b" + Strings.newRandomValidUsername();
  private static final String MAPPING_ID_3 = "c" + Strings.newRandomValidUsername();
  private static final String GROUP_ID = Strings.newRandomValidIdentityId();

  @BeforeAll
  static void setup() {
    createMapping(MAPPING_ID_1);
    createMapping(MAPPING_ID_2);
    createMapping(MAPPING_ID_3);

    createGroup(GROUP_ID);

    assignMappingToGroup(MAPPING_ID_1, GROUP_ID);
    assignMappingToGroup(MAPPING_ID_2, GROUP_ID);

    waitForGroupsToBeUpdated();
  }

  @Test
  void shouldReturnMappingsByGroup() {
    final var mappings = camundaClient.newMappingsByGroupSearchRequest(GROUP_ID).send().join();

    assertThat(mappings.items().size()).isEqualTo(2);
    assertThat(mappings.items())
        .extracting(
            Mapping::getMappingRuleId,
            Mapping::getClaimName,
            Mapping::getClaimValue,
            Mapping::getName)
        .contains(
            tuple(MAPPING_ID_1, MAPPING_ID_1 + "claimName", MAPPING_ID_1 + "claimValue", "name"),
            tuple(MAPPING_ID_2, MAPPING_ID_2 + "claimName", MAPPING_ID_2 + "claimValue", "name"));
  }

  @Test
  void shouldReturnMappingsByGroupFiltered() {
    final var mappings =
        camundaClient
            .newMappingsByGroupSearchRequest(GROUP_ID)
            .filter(fn -> fn.mappingRuleId(MAPPING_ID_1))
            .send()
            .join();

    assertThat(mappings.items().size()).isEqualTo(1);
    assertThat(mappings.items())
        .extracting(Mapping::getMappingRuleId)
        .containsExactly(MAPPING_ID_1);
  }

  @Test
  void shouldReturnMappingByGroupSorted() {
    final var mappings =
        camundaClient
            .newMappingsByGroupSearchRequest(GROUP_ID)
            .sort(fn -> fn.mappingRuleId().desc())
            .send()
            .join();

    assertThat(mappings.items().size()).isEqualTo(2);
    assertThat(mappings.items())
        .extracting(Mapping::getMappingRuleId)
        .containsExactly(MAPPING_ID_2, MAPPING_ID_1);
  }

  @Test
  void shouldRejectIfMissingGroupId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newMappingsByGroupSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newMappingsByGroupSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  private static void createMapping(final String mappingId) {
    camundaClient
        .newCreateMappingCommand()
        .mappingRuleId(mappingId)
        .name("name")
        .claimName(mappingId + "claimName")
        .claimValue(mappingId + "claimValue")
        .send()
        .join();
  }

  private static void createGroup(final String groupId) {
    camundaClient.newCreateGroupCommand().groupId(groupId).name("name").send().join();
  }

  private static void assignMappingToGroup(final String mappingId, final String groupId) {
    camundaClient.newAssignMappingToGroupCommand(groupId).mappingRuleId(mappingId).send().join();
  }

  private static void waitForGroupsToBeUpdated() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var mappings =
                  camundaClient.newMappingsByGroupSearchRequest(GROUP_ID).send().join();
              assertThat(mappings.items().size()).isEqualTo(2);
            });
  }
}
