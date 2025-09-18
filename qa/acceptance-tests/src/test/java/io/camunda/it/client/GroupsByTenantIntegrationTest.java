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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ConsistencyPolicy;
import io.camunda.client.api.search.response.TenantGroup;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class GroupsByTenantIntegrationTest {
  private static CamundaClient camundaClient;

  private static final String TENANT_ID = Strings.newRandomValidIdentityId();
  private static final String A_GROUP_ID = "aGroupId";
  private static final String B_GROUP_ID = "bGroupId";
  private static final String UNASSIGNED_GROUP_ID = Strings.newRandomValidIdentityId();

  @BeforeAll
  static void setup() {
    camundaClient.newCreateTenantCommand().tenantId(TENANT_ID).name("tenantName").send().join();

    camundaClient.newCreateGroupCommand().groupId(A_GROUP_ID).name("firstGroupName").send().join();
    camundaClient.newCreateGroupCommand().groupId(B_GROUP_ID).name("secondGroupName").send().join();
    camundaClient
        .newCreateGroupCommand()
        .groupId(UNASSIGNED_GROUP_ID)
        .name("groupName")
        .send()
        .join();

    camundaClient
        .newAssignGroupToTenantCommand()
        .groupId(A_GROUP_ID)
        .tenantId(TENANT_ID)
        .send()
        .join();
    camundaClient
        .newAssignGroupToTenantCommand()
        .groupId(B_GROUP_ID)
        .tenantId(TENANT_ID)
        .send()
        .join();

    Awaitility.await("Groups should appear in tenant group search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final List<TenantGroup> groups =
                  camundaClient
                      .newGroupsByTenantSearchRequest(TENANT_ID)
                      .consistencyPolicy(ConsistencyPolicy.noWait())
                      .send()
                      .join()
                      .items();
              assertThat(groups)
                  .extracting(TenantGroup::getGroupId)
                  .contains(A_GROUP_ID, B_GROUP_ID);
            });
  }

  @Test
  void shouldReturnGroupsByTenant() {
    final List<TenantGroup> groups =
        camundaClient
            .newGroupsByTenantSearchRequest(TENANT_ID)
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join()
            .items();
    assertThat(groups).hasSize(2);
    assertThat(groups).extracting(TenantGroup::getGroupId).containsExactly(A_GROUP_ID, B_GROUP_ID);
  }

  @Test
  void shouldReturnGroupsByTenantSortedByTenantIdDesc() {
    final List<TenantGroup> groups =
        camundaClient
            .newGroupsByTenantSearchRequest(TENANT_ID)
            .sort(s -> s.groupId().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join()
            .items();
    assertThat(groups).hasSize(2);
    assertThat(groups).extracting(TenantGroup::getGroupId).containsExactly(B_GROUP_ID, A_GROUP_ID);
  }

  @Test
  void shouldRejectGroupsByTenantSearchIfMissingTenantId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGroupsByTenantSearchRequest(null)
                    .consistencyPolicy(ConsistencyPolicy.noWait())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRejectGroupsByTenantSearchIfEmptyTenantId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGroupsByTenantSearchRequest("")
                    .consistencyPolicy(ConsistencyPolicy.noWait())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void searchGroupsShouldReturnEmptyListWhenSearchingForNonExistingTenantId() {
    final var clientsSearchResponse =
        camundaClient
            .newGroupsByTenantSearchRequest("someTenantId")
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    assertThat(clientsSearchResponse.items()).isEmpty();
  }
}
