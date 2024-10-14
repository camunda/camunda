/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.search.response.Variable;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.InputStream;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class VariableQueryTest {
  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  private static ZeebeClient camundaClient;

  private static Variable variable;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda();
  }

  @BeforeAll
  static void beforeAll() {
    camundaClient = testStandaloneCamunda.newClientBuilder().build();
    delpoyProcessFromResourcePath("/process/bpm_variable_test.bpmn", "bpm_variable_test.bpmn");

    startProcessInstance("bpmProcessVariable");

    waitForTasksBeingExported();

    variable = camundaClient.newVariableQuery().send().join().items().get(0);
  }

  @Test
  void shouldQueryVariables() {
    // when
    final var result = camundaClient.newVariableQuery().send().join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
  }

  @Test
  void shouldQueryVariableByKey() {
    // when
    final var result =
        camundaClient
            .newVariableQuery()
            .filter(f -> f.variableKey(variable.getVariableKey()))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getVariableKey()).isEqualTo(variable.getVariableKey());
  }

  @Test
  void shouldQueryByScopeKey() {
    // when
    final var result =
        camundaClient
            .newVariableQuery()
            .filter(f -> f.scopeKey(variable.getScopeKey()))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getVariableKey()).isEqualTo(variable.getVariableKey());
    assertThat(result.items().get(0).getScopeKey()).isEqualTo(variable.getScopeKey());
  }

  @Test
  void shouldQueryByName() {
    // when
    final var result =
        camundaClient.newVariableQuery().filter(f -> f.name(variable.getName())).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().stream().allMatch(v -> v.getName().equals(variable.getName())))
        .isTrue();
  }

  @Test
  void shouldQueryByNameAndValue() {
    // when
    final var result =
        camundaClient
            .newVariableQuery()
            .filter(f -> f.name(variable.getName()).value(variable.getValue()))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getVariableKey()).isEqualTo(variable.getVariableKey());
    assertThat(result.items().get(0).getValue()).isEqualTo(variable.getValue());
  }

  @Test
  void shouldQueryByProcessInstanceKey() {
    // when
    final var result =
        camundaClient
            .newVariableQuery()
            .filter(f -> f.processInstanceKey(variable.getProcessInstanceKey()))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(
            result.items().stream()
                .allMatch(v -> v.getProcessInstanceKey().equals(variable.getProcessInstanceKey())))
        .isTrue();
  }

  @Test
  void shouldQueryByTenantId() {
    // when
    final var result =
        camundaClient
            .newVariableQuery()
            .filter(f -> f.tenantId(variable.getTenantId()))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(
            result.items().stream().allMatch(v -> v.getTenantId().equals(variable.getTenantId())))
        .isTrue();
  }

  @Test
  void shouldQueryByTruncated() {
    // when
    final var result =
        camundaClient
            .newVariableQuery()
            .filter(f -> f.isTruncated(variable.isTruncated()))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items().stream().allMatch(Variable::isTruncated)).isFalse();

    // when
    final var resultTruncatedTrue =
        camundaClient.newVariableQuery().filter(f -> f.isTruncated(true)).send().join();

    // then
    assertThat(resultTruncatedTrue.items().size()).isEqualTo(0);
  }

  private static void delpoyProcessFromResourcePath(
      final String resource, final String resourceName) {
    final InputStream process = UserTaskQueryTest.class.getResourceAsStream(resource);

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(Bpmn.readModelFromStream(process), resourceName)
        .send()
        .join();
  }

  @Test
  void shouldSortByNameASC() {
    // when

    final var result = camundaClient.newVariableQuery().sort(s -> s.name().asc()).send().join();

    assertThat(result.items().size()).isEqualTo(5);

    final List<String> names =
        result.items().stream().map(item -> item.getName()).collect(Collectors.toList());

    assertThat(names).isSorted();
  }

  @Test
  void shouldVariableByKey() {
    // when
    final var result = camundaClient.newVariableGetRequest(variable.getVariableKey()).send().join();

    // then
    assertThat(result.getVariableKey()).isEqualTo(variable.getVariableKey());
  }

  @Test
  void shouldReturn404ForNotFoundVariableKey() {
    // when
    final long variableKey = new Random().nextLong();
    final var problemException =
        assertThrows(
            ProblemException.class,
            () -> camundaClient.newVariableGetRequest(variableKey).send().join());

    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Variable with key %d not found".formatted(variableKey));
  }

  @Test
  void shouldSortByValueDESC() {
    // when
    final var result = camundaClient.newVariableQuery().sort(s -> s.value().desc()).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(5);

    final List<String> values =
        result.items().stream().map(item -> item.getValue()).collect(Collectors.toList());

    assertThat(values).isSortedAccordingTo(Comparator.reverseOrder());
  }

  private static void waitForTasksBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newUserTaskQuery().send().join();
              assertThat(result.items().size()).isEqualTo(2);

              final var resultVariable = camundaClient.newVariableQuery().send().join();
              assertThat(resultVariable.items().size()).isEqualTo(5);
            });
  }

  private static void startProcessInstance(final String processId) {
    camundaClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }
}
