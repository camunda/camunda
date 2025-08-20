/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.request.VariableSearchRequest;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class VariablesManagementTest {

  private static CamundaClient camundaClient;

  private static VariableSearchRequest clusterVariableSearchRequest;

  @BeforeAll
  static void beforeAll() {
    clusterVariableSearchRequest =
        camundaClient.newVariableSearchRequest().filter(f -> f.scopeKey(-1L));
  }

  @BeforeEach
  public void beforeEach() {
    clusterVariableSearchRequest
        .send()
        .join()
        .items()
        .forEach(
            variable ->
                camundaClient
                    .newVariableDeleteCommand()
                    .key(variable.getVariableKey())
                    .send()
                    .join());

    Awaitility.await("All variables deleted")
        .untilAsserted(
            () -> {
              final var result = clusterVariableSearchRequest.send().join();
              assertThat(result.items()).isEmpty();
            });
  }

  @Test
  void shouldCreateVariable() {
    camundaClient
        .newVariableCreationCommand()
        .variable("KEY_1", "VALUE")
        .clusterLevel()
        .send()
        .join();

    awaitSingleClusterVar("KEY_1", -1L, "\"VALUE\"");
  }

  @Test
  void shouldUpdateVariable() {
    camundaClient
        .newVariableCreationCommand()
        .variable("KEY_2", "VALUE")
        .clusterLevel()
        .send()
        .join();

    awaitSingleClusterVar("KEY_2", -1L, "\"VALUE\"");
    final var result = clusterVariableSearchRequest.send().join();

    camundaClient
        .newVariableUpdateCommand()
        .variable(result.items().getFirst().getVariableKey(), "VALUE_2")
        .send()
        .join();

    awaitSingleClusterVar("KEY_2", -1L, "\"VALUE_2\"");
  }

  @Test
  void shouldDeleteVariable() {
    camundaClient
        .newVariableCreationCommand()
        .variable("KEY_3", "VALUE")
        .clusterLevel()
        .send()
        .join();

    awaitSingleClusterVar("KEY_3", -1L, "\"VALUE\"");
    final var result = clusterVariableSearchRequest.send().join();

    camundaClient
        .newVariableDeleteCommand()
        .key(result.items().getFirst().getVariableKey())
        .send()
        .join();

    awaitSingleClusterUntilEmpty();
  }

  @Test
  void shouldHandleCommandRejectionProperly_updateRejectionDoesNotWriteInSecondaryStorage() {
    camundaClient
        .newVariableCreationCommand()
        .variable("KEY", "VALUE")
        .clusterLevel()
        .send()
        .join();

    awaitSingleClusterVar("KEY", -1L, "\"VALUE\"");
    final var result = clusterVariableSearchRequest.send().join();

    camundaClient
        .newVariableDeleteCommand()
        .key(result.items().getFirst().getVariableKey())
        .send()
        .join();

    Assertions.assertThrows(
        Exception.class,
        () ->
            camundaClient
                .newVariableUpdateCommand()
                .variable(result.items().getFirst().getVariableKey(), "VALUE_5")
                .send()
                .join());

    camundaClient
        .newVariableCreationCommand()
        .variable("KEY_2", "VALUE_2")
        .clusterLevel()
        .send()
        .join();

    awaitSingleClusterVar("KEY_2", -1L, "\"VALUE_2\"");
    final var result2 = clusterVariableSearchRequest.send().join();

    assertThat(result2.items().size()).isEqualTo(1);
    assertThat(result2.items().getFirst().getValue()).isEqualTo("\"VALUE_2\"");
  }

  private void awaitSingleClusterUntilEmpty() {
    Awaitility.await("No variables exist")
        .untilAsserted(
            () -> {
              final var result = clusterVariableSearchRequest.send().join();
              assertThat(result.items()).isEmpty();
            });
  }

  private void awaitSingleClusterVar(
      final String expectedName, final long expectedScopeKey, final String expectedJsonValue) {
    Awaitility.await("Cluster variable reaches expected state")
        .untilAsserted(
            () -> assertSingleClusterVar(expectedName, expectedScopeKey, expectedJsonValue));
  }

  private void assertSingleClusterVar(
      final String expectedName, final long expectedScopeKey, final String expectedJsonValue) {
    final var result = clusterVariableSearchRequest.send().join();
    assertThat(result).isNotNull();
    assertThat(result.items()).hasSize(1);

    final var item = result.items().getFirst();
    assertThat(item.getName()).isEqualTo(expectedName);
    assertThat(item.getScopeKey()).isEqualTo(expectedScopeKey);
    assertThat(item.getValue()).isEqualTo(expectedJsonValue);
  }
}
