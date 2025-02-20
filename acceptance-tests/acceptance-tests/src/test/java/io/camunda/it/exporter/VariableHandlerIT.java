/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BrokerITInvocationProvider.class)
public class VariableHandlerIT {

  private static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;

  @TestTemplate
  void shouldExportVariable(final TestStandaloneBroker testBroker) {
    final var client = testBroker.newClientBuilder().build();

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

    waitForVariables(client, f -> f.processInstanceKey(processInstanceKey));

    final var variables =
        client
            .newVariableQuery()
            .filter(f -> f.processInstanceKey(processInstanceKey).name("smallVariable"))
            .send()
            .join();

    assertThat(variables.items()).isNotEmpty();
    assertThat(variables.items().stream().filter(v -> v.getName().equals("smallVariable")).count())
        .isEqualTo(1);
    final var smallVariable =
        variables.items().stream().filter(v -> v.getName().equals("smallVariable")).findFirst();
    assertThat(smallVariable).isPresent();
    assertThat(smallVariable.get().getValue()).isEqualTo("\"smallValue\"");
    assertThat(smallVariable.get().getFullValue()).isNull();
    assertThat(smallVariable.get().isTruncated()).isFalse();
  }

  @TestTemplate
  void shouldTruncateVariable(final TestStandaloneBroker testBroker) {
    final var client = testBroker.newClientBuilder().build();

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
            .newVariableQuery()
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
    assertThat(largeVariable).isPresent();
    assertThat(largeVariable.get().getFullValue()).isEqualTo("\"" + largeValue + "\"");
    assertThat(largeVariable.get().isTruncated()).isTrue();
    assertThat(largeVariable.get().getValue())
        .isEqualTo("\"" + largeValue.substring(0, DEFAULT_VARIABLE_SIZE_THRESHOLD - 1));
  }

  @TestTemplate
  void replaceTruncatedVariableWithSmallValue(final TestStandaloneBroker testBroker) {
    final var client = testBroker.newClientBuilder().build();

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
            .newVariableQuery()
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
    assertThat(largeVariable.get().getFullValue()).isEqualTo("\"" + largeValue + "\"");
    assertThat(largeVariable.get().isTruncated()).isTrue();

    client
        .newSetVariablesCommand(processInstanceKey)
        .variables(Map.of("largeVariable", "smallValue"))
        .send()
        .join();

    waitForVariables(
        client,
        f -> f.processInstanceKey(processInstanceKey).name("largeVariable").isTruncated(false));

    final var updatedVariables =
        client
            .newVariableQuery()
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
    assertThat(updatedVariable.get().getFullValue()).isNull();
    assertThat(updatedVariable.get().isTruncated()).isFalse();
  }

  private void waitForVariables(
      final CamundaClient client, final Consumer<VariableFilter> filterFn) {
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(() -> !client.newVariableQuery().filter(filterFn).send().join().items().isEmpty());
  }
}
