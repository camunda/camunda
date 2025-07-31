/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class RolesByMappingRuleIntegrationTest {

  private static CamundaClient camundaClient;

  private static final String EXISTING_ROLE_ID = Strings.newRandomValidIdentityId();

  @BeforeAll
  static void setup() {
    createRole(EXISTING_ROLE_ID, "ARoleName", "description");

    Awaitility.await("Role is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var role = camundaClient.newRoleGetRequest(EXISTING_ROLE_ID).send().join();
              assertThat(role).isNotNull();
              assertThat(role.getRoleId()).isEqualTo(EXISTING_ROLE_ID);
              assertThat(role.getName()).isEqualTo("ARoleName");
              assertThat(role.getDescription()).isEqualTo("description");
            });
  }

  @Test
  void shouldAssignRoleToMappingRule() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleId = Strings.newRandomValidIdentityId();

    createRole(roleId, "ARoleName", "description");
    createMappingRule(mappingRuleId, "mappingRuleName", "testClaimName", "testClaimValue");

    // when
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();
    // then
    verifyRoleIsAssignedToMappingRule(roleId, mappingRuleId);
  }

  @Test
  void shouldUnassignRoleFromMappingRule() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleId = Strings.newRandomValidIdentityId();

    createRole(roleId, "ARoleName", "description");
    createMappingRule(
        mappingRuleId, "someMappingRuleName", "someTestClaimName", "someTestClaimValue");

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();

    verifyRoleIsAssignedToMappingRule(roleId, mappingRuleId);

    // when
    camundaClient
        .newUnassignRoleFromMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();

    // then
    Awaitility.await("Mapping rule is unassigned from the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(() -> assertThat(searchMappingRuleByRole(roleId).items()).isEmpty());
  }

  @Test
  void shouldUnassignRoleFromMappingRuleOnRoleDeletion() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleId = Strings.newRandomValidIdentityId();

    createRole(roleId, "ARoleName", "description");
    createMappingRule(mappingRuleId, "mappingRuleName", "aClaimName", "aClaimValue");

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();

    verifyRoleIsAssignedToMappingRule(roleId, mappingRuleId);

    // when
    camundaClient.newDeleteRoleCommand(roleId).send().join();

    // then
    Awaitility.await("Mapping rule is unassigned from deleted role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(() -> assertThat(searchMappingRuleByRole(roleId).items()).isEmpty());
  }

  @Test
  void shouldRejectAssigningRoleIfRoleAlreadyAssignedToMappingRule() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();

    createMappingRule(mappingRuleId, "mappingRuleName", "claimName", "claimValue");

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(EXISTING_ROLE_ID)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingRuleCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingRuleId(mappingRuleId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to add entity with ID '"
                + mappingRuleId
                + "' to role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is already assigned to this role.");
  }

  @Test
  void shouldRejectUnassigningRoleIfRoleIsNotAssignedToMappingRule() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();

    createMappingRule(mappingRuleId, "mappingRuleName", "someClaimName", "someClaimValue");

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromMappingRuleCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingRuleId(mappingRuleId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to remove entity with ID '"
                + mappingRuleId
                + "' from role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is not assigned to this role.");
  }

  @Test
  void shouldReturnNotFoundOnAssigningRoleToMappingRuleIfRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingRuleCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .mappingRuleId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID does not exist");
  }

  @Test
  void shouldReturnNotFoundOnAssigningRoleToMappingRuleIfMappingRuleDoesNotExist() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingRuleCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingRuleId(mappingRuleId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to add an entity with ID '"
                + mappingRuleId
                + "' and type 'MAPPING_RULE' to role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity doesn't exist.");
  }

  @Test
  void shouldReturnNotFoundOnUnassigningRoleFromMappingRuleIfMappingRuleDoesNotExist() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromMappingRuleCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingRuleId(mappingRuleId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to remove an entity with ID '"
                + mappingRuleId
                + "' and type 'MAPPING_RULE' from role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity doesn't exist.");
  }

  @Test
  void shouldReturnNotFoundOnUnassigningRoleFromMappingRuleIfRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromMappingRuleCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .mappingRuleId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID does not exist");
  }

  @Test
  void shouldSearchMappingRulesByRole() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var mappingRuleName = Strings.newRandomValidIdentityId();
    final var claimName = Strings.newRandomValidIdentityId();
    final var claimValue = Strings.newRandomValidIdentityId();

    createRole(roleId, "SearchRole", "desc");
    createMappingRule(mappingRuleId, mappingRuleName, claimName, claimValue);
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();
    // when / then
    Awaitility.await("Mapping rule should be found by role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient.newMappingRulesByRoleSearchRequest(roleId).send().join();
              assertThat(result.items())
                  .singleElement()
                  .satisfies(
                      mappingRule -> {
                        assertThat(mappingRule.getName()).isEqualTo(mappingRuleName);
                        assertThat(mappingRule.getMappingRuleId()).isEqualTo(mappingRuleId);
                        assertThat(mappingRule.getClaimValue()).isEqualTo(claimValue);
                        assertThat(mappingRule.getClaimName()).isEqualTo(claimName);
                      });
            });
  }

  @Test
  void shouldReturnEmptyListForRoleWithoutMappingRules() {
    final var roleId = Strings.newRandomValidIdentityId();
    createRole(roleId, "EmptyRole", "desc");
    final var result = camundaClient.newMappingRulesByRoleSearchRequest(roleId).send().join();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldSortMappingRulesByName() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleA = Strings.newRandomValidIdentityId();
    final var mappingRuleB = Strings.newRandomValidIdentityId();
    final var mappingRuleC = Strings.newRandomValidIdentityId();

    final var nameA = "AAA-" + Strings.newRandomValidIdentityId();
    final var nameB = "BBB-" + Strings.newRandomValidIdentityId();
    final var nameC = "CCC-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "SortRole", "desc");
    createMappingRule(
        mappingRuleA,
        nameA,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());
    createMappingRule(
        mappingRuleB,
        nameB,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());
    createMappingRule(
        mappingRuleC,
        nameC,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleA)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleB)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleC)
        .send()
        .join();

    Awaitility.await("Mapping rules are sorted by name")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newMappingRulesByRoleSearchRequest(roleId)
                      .sort(s -> s.name().desc())
                      .send()
                      .join();

              assertThat(result.items())
                  .extracting(MappingRule::getName)
                  .containsExactly(nameC, nameB, nameA);
            });
  }

  @Test
  void shouldSortMappingRulesByClaimName() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleA = Strings.newRandomValidIdentityId();
    final var mappingRuleB = Strings.newRandomValidIdentityId();

    final var claimA = "AAA-" + Strings.newRandomValidIdentityId();
    final var claimB = "BBB-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "SortRole", "desc");
    createMappingRule(
        mappingRuleA,
        Strings.newRandomValidIdentityId(),
        claimA,
        Strings.newRandomValidIdentityId());
    createMappingRule(
        mappingRuleB,
        Strings.newRandomValidIdentityId(),
        claimB,
        Strings.newRandomValidIdentityId());

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleA)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleB)
        .send()
        .join();

    Awaitility.await("Mapping rules are sorted by claimName")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newMappingRulesByRoleSearchRequest(roleId)
                      .sort(s -> s.claimName().asc())
                      .send()
                      .join();

              assertThat(result.items())
                  .extracting(MappingRule::getClaimName)
                  .containsExactly(claimA, claimB);
            });
  }

  @Test
  void shouldSortMappingRulesByClaimValueAsc() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleA = Strings.newRandomValidIdentityId();
    final var mappingRuleB = Strings.newRandomValidIdentityId();

    final var valueA = "aaa-" + Strings.newRandomValidIdentityId();
    final var valueB = "bbb-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "SortRole", "desc");
    createMappingRule(
        mappingRuleA,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        valueA);
    createMappingRule(
        mappingRuleB,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        valueB);

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleA)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleB)
        .send()
        .join();

    Awaitility.await("Mapping rules are sorted by claimValue")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newMappingRulesByRoleSearchRequest(roleId)
                      .sort(s -> s.claimValue().desc())
                      .send()
                      .join();

              assertThat(result.items())
                  .extracting(MappingRule::getClaimValue)
                  .containsExactly(valueB, valueA);
            });
  }

  @Test
  void shouldFilterByMappingRuleName() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var nonMatchingMappingRuleId = Strings.newRandomValidIdentityId();
    final var name = "filter-name-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "FilterRole", "desc");
    createMappingRule(
        mappingRuleId,
        name,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());
    createMappingRule(
        nonMatchingMappingRuleId,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(nonMatchingMappingRuleId)
        .send()
        .join();

    Awaitility.await("Mapping rule is filtered by name")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newMappingRulesByRoleSearchRequest(roleId)
                      .filter(f -> f.name(name))
                      .send()
                      .join();
              assertThat(result.items())
                  .singleElement()
                  .extracting(MappingRule::getName)
                  .isEqualTo(name);
            });
  }

  @Test
  void shouldFilterByClaimName() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var nonMatchingMappingRuleId = Strings.newRandomValidIdentityId();
    final var claimName = "filter-claimName-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "FilterRole", "desc");
    createMappingRule(
        mappingRuleId,
        Strings.newRandomValidIdentityId(),
        claimName,
        Strings.newRandomValidIdentityId());
    createMappingRule(
        nonMatchingMappingRuleId,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(nonMatchingMappingRuleId)
        .send()
        .join();

    Awaitility.await("Mapping rule is filtered by claimName")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newMappingRulesByRoleSearchRequest(roleId)
                      .filter(f -> f.claimName(claimName))
                      .send()
                      .join();
              assertThat(result.items())
                  .singleElement()
                  .extracting(MappingRule::getClaimName)
                  .isEqualTo(claimName);
            });
  }

  @Test
  void shouldFilterByClaimValue() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var nonMatchingMappingRuleId = Strings.newRandomValidIdentityId();
    final var claimValue = "filter-claimValue-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "FilterRole", "desc");
    createMappingRule(
        mappingRuleId,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        claimValue);
    createMappingRule(
        nonMatchingMappingRuleId,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(nonMatchingMappingRuleId)
        .send()
        .join();

    Awaitility.await("Mapping rule is filtered by claimValue")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newMappingRulesByRoleSearchRequest(roleId)
                      .filter(f -> f.claimValue(claimValue))
                      .send()
                      .join();
              assertThat(result.items())
                  .singleElement()
                  .extracting(MappingRule::getClaimValue)
                  .isEqualTo(claimValue);
            });
  }

  private static void createMappingRule(
      final String mappingRuleId,
      final String name,
      final String claimName,
      final String claimValue) {
    camundaClient
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .name(name)
        .claimName(claimName)
        .claimValue(claimValue)
        .send()
        .join();
  }

  private static void verifyRoleIsAssignedToMappingRule(
      final String roleId, final String mappingRuleId) {
    Awaitility.await("Mapping rule is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(searchMappingRuleByRole(roleId).items())
                    .hasSize(1)
                    .anyMatch(m -> mappingRuleId.equals(m.getMappingRuleId())));
  }

  private static SearchResponse<MappingRule> searchMappingRuleByRole(final String roleId) {
    return camundaClient.newMappingRulesByRoleSearchRequest(roleId).send().join();
  }

  private static void createRole(
      final String roleId, final String roleName, final String description) {
    camundaClient
        .newCreateRoleCommand()
        .roleId(roleId)
        .name(roleName)
        .description(description)
        .send()
        .join();
  }
}
