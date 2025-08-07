/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class UniqueEntityIdentifiersIT {

  private static CamundaClient client;

  @Test // regression test for https://github.com/camunda/camunda/issues/35549
  void shouldCreateUniqueIdsForRoleAndGroup() {
    // given
    final var conflictingId = "test";
    final String tenantId = "test-tenant";
    // create group, role, and tenant
    client.newCreateGroupCommand().groupId(conflictingId).name("testGroup").send().join();
    client.newCreateRoleCommand().roleId(conflictingId).name("testRole").send().join();
    client.newCreateTenantCommand().tenantId(tenantId).name("Test Tenant").send().join();
    // assign group to tenant
    client.newAssignGroupToTenantCommand().groupId("test").tenantId(tenantId).send().join();
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var groupsResponse =
                  client.newGroupsByTenantSearchRequest(tenantId).send().join();
              assertThat(groupsResponse.items()).hasSize(1);
              assertThat(groupsResponse.items().get(0).getGroupId()).isEqualTo("test");
            });

    // when
    // assign role to tenant with the same ID
    client.newAssignRoleToTenantCommand().roleId("test").tenantId(tenantId).send().join();
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var rolesResponse =
                  client.newRolesByTenantSearchRequest(tenantId).send().join();
              assertThat(rolesResponse.items()).hasSize(1);
              assertThat(rolesResponse.items().get(0).getRoleId()).isEqualTo("test");
            });

    // then
    final var updatedGroupsResponse = client.newGroupsByTenantSearchRequest(tenantId).send().join();
    assertThat(updatedGroupsResponse.items()).hasSize(1);
    assertThat(updatedGroupsResponse.items().get(0).getGroupId()).isEqualTo("test");
    //    assertThat(updatedGroupsResponse.items()).isEmpty();
    //    assertThatThrownBy(
    //            () ->
    //                client
    //                    .newAssignGroupToTenantCommand()
    //                    .groupId("test")
    //                    .tenantId(tenantId)
    //                    .send()
    //                    .join())
    //        .hasMessageContaining("409")
    //        .hasMessageContaining("Conflict");
  }
}
