/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@MultiDbTest
public class VariableIT {

  private static CamundaClient client;
  private static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;

  @Test
  void shouldExportVariable() {
    // given
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/bpm_variable_test.bpmn")
            .send()
            .join();

    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(deployment.getProcesses().getFirst().getBpmnProcessId())
            .latestVersion()
            .variables(Map.of("smallVariable", "smallValue"))
            .send()
            .join()
            .getProcessInstanceKey();

    // when
    waitForVariables(client, f -> f.processInstanceKey(processInstanceKey));

    final var variables =
        client
            .newVariableSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey).name("smallVariable"))
            .send()
            .join();

    // then
    assertThat(variables.items()).isNotEmpty();
    assertThat(variables.items().stream().filter(v -> v.getName().equals("smallVariable")).count())
        .isEqualTo(1);
    final var smallVariable =
        variables.items().stream().filter(v -> v.getName().equals("smallVariable")).findFirst();
    assertThat(smallVariable).isPresent();
    assertThat(smallVariable.get().getValue()).isEqualTo("\"smallValue\"");
    assertThat(smallVariable.get().isTruncated()).isFalse();
  }

  @Test
  @DisabledIfSystemProperty(
      named = "test.integration.camunda.database.type",
      matches = "rdbms_oracle")
  void shouldTruncateVariable() {
    shouldTruncateVariable(DEFAULT_VARIABLE_SIZE_THRESHOLD);
  }

  /**
   * Oracle has different limits for VARCHAR2 fields depending on the database settings. In most
   * common configurations, the limit is 4000 bytes for VARCHAR2 fields.
   */
  @Test
  @EnabledIfSystemProperty(
      named = "test.integration.camunda.database.type",
      matches = "rdbms_oracle")
  void shouldTruncateVariableOracle() {
    shouldTruncateVariable(4000);
  }

  void shouldTruncateVariable(final int variableSizeThreshold) {
    // given
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/bpm_variable_test.bpmn")
            .send()
            .join();

    final String largeValue = "b".repeat(variableSizeThreshold + 1);

    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(deployment.getProcesses().getFirst().getBpmnProcessId())
            .latestVersion()
            .variables(Map.of("largeVariable", largeValue))
            .send()
            .join()
            .getProcessInstanceKey();

    // when
    waitForVariables(client, f -> f.processInstanceKey(processInstanceKey));

    final var variables =
        client
            .newVariableSearchRequest()
            .filter(
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .name("largeVariable")
                        .isTruncated(true))
            .send()
            .join();

    // then
    assertThat(variables.items()).isNotEmpty();
    assertThat(variables.items().stream().filter(v -> v.getName().equals("largeVariable")).count())
        .isEqualTo(1);
    final var largeVariable =
        variables.items().stream().filter(v -> v.getName().equals("largeVariable")).findFirst();
    assertThat(largeVariable).isPresent();
    assertThat(largeVariable.get().isTruncated()).isTrue();
    assertThat(largeVariable.get().getValue())
        .isEqualTo("\"" + largeValue.substring(0, variableSizeThreshold - 1));
  }

  @Test
  void shouldGetFullVariable() {
    // given
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/bpm_variable_test.bpmn")
            .send()
            .join();

    final String largeValue = "b".repeat(DEFAULT_VARIABLE_SIZE_THRESHOLD + 1);

    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(deployment.getProcesses().getFirst().getBpmnProcessId())
            .latestVersion()
            .variables(Map.of("largeVariable", largeValue))
            .send()
            .join()
            .getProcessInstanceKey();

    waitForVariables(client, f -> f.processInstanceKey(processInstanceKey));

    final var variables =
        client
            .newVariableSearchRequest()
            .filter(
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .name("largeVariable")
                        .isTruncated(true))
            .send()
            .join();
    assertThat(variables.items()).isNotEmpty();
    assertThat(variables.items().stream().filter(v -> v.getName().equals("largeVariable")).count())
        .isEqualTo(1);
    var largeVariable =
        variables.items().stream()
            .filter(v -> v.getName().equals("largeVariable"))
            .findFirst()
            .get();

    // when
    largeVariable = client.newVariableGetRequest(largeVariable.getVariableKey()).send().join();

    // then
    assertThat(largeVariable.isTruncated()).isFalse();
    assertThat(largeVariable.getValue()).isEqualTo("\"" + largeValue + "\"");
  }

  @Test
  void replaceTruncatedVariableWithSmallValue() {
    // given
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/bpm_variable_test.bpmn")
            .send()
            .join();

    final String largeValue = "b".repeat(DEFAULT_VARIABLE_SIZE_THRESHOLD + 1);

    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(deployment.getProcesses().getFirst().getBpmnProcessId())
            .latestVersion()
            .variables(Map.of("largeVariable", largeValue))
            .send()
            .join()
            .getProcessInstanceKey();

    waitForVariables(
        client,
        f -> f.processInstanceKey(processInstanceKey).name("largeVariable").isTruncated(true));

    final var variables =
        client
            .newVariableSearchRequest()
            .filter(
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .name("largeVariable")
                        .isTruncated(true))
            .send()
            .join();

    assertThat(variables.items()).isNotEmpty();
    assertThat(variables.items().stream().filter(v -> v.getName().equals("largeVariable")).count())
        .isEqualTo(1);
    final var largeVariable =
        variables.items().stream().filter(v -> v.getName().equals("largeVariable")).findFirst();
    assertThat(largeVariable.get().isTruncated()).isTrue();

    // when
    client
        .newSetVariablesCommand(processInstanceKey)
        .variables(Map.of("largeVariable", "smallValue"))
        .send()
        .join();

    // then
    waitForVariables(
        client,
        f -> f.processInstanceKey(processInstanceKey).name("largeVariable").isTruncated(false));

    final var updatedVariables =
        client
            .newVariableSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey).name("largeVariable"))
            .send()
            .join();

    assertThat(updatedVariables.items()).isNotEmpty();
    assertThat(
            updatedVariables.items().stream()
                .filter(v -> v.getName().equals("largeVariable"))
                .count())
        .isEqualTo(1);
    final var updatedVariable =
        updatedVariables.items().stream()
            .filter(v -> v.getName().equals("largeVariable"))
            .findFirst();
    assertThat(updatedVariable).isPresent();
    assertThat(updatedVariable.get().getValue()).isEqualTo("\"smallValue\"");
    assertThat(updatedVariable.get().isTruncated()).isFalse();
  }

  private void waitForVariables(
      final CamundaClient client, final Consumer<VariableFilter> filterFn) {
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(
            () ->
                !client
                    .newVariableSearchRequest()
                    .filter(filterFn)
                    .send()
                    .join()
                    .items()
                    .isEmpty());
  }
}
