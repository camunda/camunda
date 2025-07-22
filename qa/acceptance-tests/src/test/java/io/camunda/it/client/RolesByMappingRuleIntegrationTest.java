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
  void shouldAssignRoleToMapping() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();

    createRole(roleId, "ARoleName", "description");
    createMapping(mappingId, "mappingName", "testClaimName", "testClaimValue");

    // when
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingId)
        .send()
        .join();
    // then
    verifyRoleIsAssignedToMapping(roleId, mappingId);
  }

  @Test
  void shouldUnassignRoleFromMapping() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();

    createRole(roleId, "ARoleName", "description");
    createMapping(mappingId, "someMappingName", "someTestClaimName", "someTestClaimValue");

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingId)
        .send()
        .join();

    verifyRoleIsAssignedToMapping(roleId, mappingId);

    // when
    camundaClient
        .newUnassignRoleFromMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingId)
        .send()
        .join();

    // then
    Awaitility.await("Mapping is unassigned from the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(() -> assertThat(searchMappingRuleByRole(roleId).items()).isEmpty());
  }

  @Test
  void shouldUnassignRoleFromMappingOnRoleDeletion() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();

    createRole(roleId, "ARoleName", "description");
    createMapping(mappingId, "mappingName", "aClaimName", "aClaimValue");

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingId)
        .send()
        .join();

    verifyRoleIsAssignedToMapping(roleId, mappingId);

    // when
    camundaClient.newDeleteRoleCommand(roleId).send().join();

    // then
    Awaitility.await("Mapping is unassigned from deleted role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(() -> assertThat(searchMappingRuleByRole(roleId).items()).isEmpty());
  }

  @Test
  void shouldRejectAssigningRoleIfRoleAlreadyAssignedToMapping() {
    // given
    final var mappingId = Strings.newRandomValidIdentityId();

    createMapping(mappingId, "mappingName", "claimName", "claimValue");

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(EXISTING_ROLE_ID)
        .mappingRuleId(mappingId)
        .send()
        .join();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingRuleCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingRuleId(mappingId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to add entity with ID '"
                + mappingId
                + "' to role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is already assigned to this role.");
  }

  @Test
  void shouldRejectUnassigningRoleIfRoleIsNotAssignedToMapping() {
    // given
    final var mappingId = Strings.newRandomValidIdentityId();

    createMapping(mappingId, "mappingName", "someClaimName", "someClaimValue");

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromMappingRuleCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingRuleId(mappingId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to remove entity with ID '"
                + mappingId
                + "' from role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is not assigned to this role.");
  }

  @Test
  void shouldReturnNotFoundOnAssigningRoleToMappingIfRoleDoesNotExist() {
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
  void shouldReturnNotFoundOnAssigningRoleToMappingIfMappingDoesNotExist() {
    // given
    final var mappingId = Strings.newRandomValidIdentityId();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingRuleCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingRuleId(mappingId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to add an entity with ID '"
                + mappingId
                + "' and type 'MAPPING_RULE' to role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity doesn't exist.");
  }

  @Test
  void shouldReturnNotFoundOnUnassigningRoleFromMappingIfMappingDoesNotExist() {
    // given
    final var mappingId = Strings.newRandomValidIdentityId();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromMappingRuleCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingRuleId(mappingId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to remove an entity with ID '"
                + mappingId
                + "' and type 'MAPPING_RULE' from role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity doesn't exist.");
  }

  @Test
  void shouldReturnNotFoundOnUnassigningRoleFromMappingIfRoleDoesNotExist() {
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
  void shouldSearchMappingsByRole() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();
    final var mappingName = Strings.newRandomValidIdentityId();
    final var claimName = Strings.newRandomValidIdentityId();
    final var claimValue = Strings.newRandomValidIdentityId();

    createRole(roleId, "SearchRole", "desc");
    createMapping(mappingId, mappingName, claimName, claimValue);
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingId)
        .send()
        .join();
    // when / then
    Awaitility.await("Mapping should be found by role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient.newMappingRulesByRoleSearchRequest(roleId).send().join();
              assertThat(result.items())
                  .singleElement()
                  .satisfies(
                      mapping -> {
                        assertThat(mapping.getName()).isEqualTo(mappingName);
                        assertThat(mapping.getMappingRuleId()).isEqualTo(mappingId);
                        assertThat(mapping.getClaimValue()).isEqualTo(claimValue);
                        assertThat(mapping.getClaimName()).isEqualTo(claimName);
                      });
            });
  }

  @Test
  void shouldReturnEmptyListForRoleWithoutMappings() {
    final var roleId = Strings.newRandomValidIdentityId();
    createRole(roleId, "EmptyRole", "desc");
    final var result = camundaClient.newMappingRulesByRoleSearchRequest(roleId).send().join();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldSortMappingsByName() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingA = Strings.newRandomValidIdentityId();
    final var mappingB = Strings.newRandomValidIdentityId();
    final var mappingC = Strings.newRandomValidIdentityId();

    final var nameA = "AAA-" + Strings.newRandomValidIdentityId();
    final var nameB = "BBB-" + Strings.newRandomValidIdentityId();
    final var nameC = "CCC-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "SortRole", "desc");
    createMapping(
        mappingA, nameA, Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId());
    createMapping(
        mappingB, nameB, Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId());
    createMapping(
        mappingC, nameC, Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId());

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingA)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingB)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingC)
        .send()
        .join();

    Awaitility.await("Mappings are sorted by name")
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
  void shouldSortMappingsByClaimName() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingA = Strings.newRandomValidIdentityId();
    final var mappingB = Strings.newRandomValidIdentityId();

    final var claimA = "AAA-" + Strings.newRandomValidIdentityId();
    final var claimB = "BBB-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "SortRole", "desc");
    createMapping(
        mappingA, Strings.newRandomValidIdentityId(), claimA, Strings.newRandomValidIdentityId());
    createMapping(
        mappingB, Strings.newRandomValidIdentityId(), claimB, Strings.newRandomValidIdentityId());

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingA)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingB)
        .send()
        .join();

    Awaitility.await("Mappings are sorted by claimName")
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
  void shouldSortMappingsByClaimValueAsc() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingA = Strings.newRandomValidIdentityId();
    final var mappingB = Strings.newRandomValidIdentityId();

    final var valueA = "aaa-" + Strings.newRandomValidIdentityId();
    final var valueB = "bbb-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "SortRole", "desc");
    createMapping(
        mappingA, Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId(), valueA);
    createMapping(
        mappingB, Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId(), valueB);

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingA)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingB)
        .send()
        .join();

    Awaitility.await("Mappings are sorted by claimValue")
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
  void shouldFilterByMappingName() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();
    final var nonMatchingMappingId = Strings.newRandomValidIdentityId();
    final var name = "filter-name-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "FilterRole", "desc");
    createMapping(
        mappingId, name, Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId());
    createMapping(
        nonMatchingMappingId,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingId)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(nonMatchingMappingId)
        .send()
        .join();

    Awaitility.await("Mapping is filtered by name")
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
    final var mappingId = Strings.newRandomValidIdentityId();
    final var nonMatchingMappingId = Strings.newRandomValidIdentityId();
    final var claimName = "filter-claimName-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "FilterRole", "desc");
    createMapping(
        mappingId,
        Strings.newRandomValidIdentityId(),
        claimName,
        Strings.newRandomValidIdentityId());
    createMapping(
        nonMatchingMappingId,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());

    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingId)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(nonMatchingMappingId)
        .send()
        .join();

    Awaitility.await("Mapping is filtered by claimName")
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
    final var mappingId = Strings.newRandomValidIdentityId();
    final var nonMatchingMappingId = Strings.newRandomValidIdentityId();
    final var claimValue = "filter-claimValue-" + Strings.newRandomValidIdentityId();

    createRole(roleId, "FilterRole", "desc");
    createMapping(
        mappingId,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        claimValue);
    createMapping(
        nonMatchingMappingId,
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId(),
        Strings.newRandomValidIdentityId());
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingId)
        .send()
        .join();
    camundaClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(nonMatchingMappingId)
        .send()
        .join();

    Awaitility.await("Mapping is filtered by claimValue")
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

  private static void createMapping(
      final String mappingId, final String name, final String claimName, final String claimValue) {
    camundaClient
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingId)
        .name(name)
        .claimName(claimName)
        .claimValue(claimValue)
        .send()
        .join();
  }

  private static void verifyRoleIsAssignedToMapping(final String roleId, final String mappingId) {
    Awaitility.await("Mapping is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(searchMappingRuleByRole(roleId).items())
                    .hasSize(1)
                    .anyMatch(m -> mappingId.equals(m.getMappingRuleId())));
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
