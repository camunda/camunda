/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.Variable;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
class VariableSearchTest {

  private static CamundaClient camundaClient;

  private static Variable variable;

  @BeforeAll
  static void beforeAll() {
    delpoyProcessFromResourcePath("/process/bpm_variable_test.bpmn", "bpm_variable_test.bpmn");

    startProcessInstance("bpmProcessVariable");

    waitForTasksBeingExported();

    variable = camundaClient.newVariableSearchRequest().send().join().items().get(0);
  }

  @Test
  void shouldQueryVariables() {
    // when
    final var result = camundaClient.newVariableSearchRequest().send().join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
  }

  @Test
  void shouldQueryVariableByKey() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
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
            .newVariableSearchRequest()
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
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.name(variable.getName()))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().stream().allMatch(v -> v.getName().equals(variable.getName())))
        .isTrue();
  }

  @Test
  void shouldQueryByNameFilterIn() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.name(b -> b.in("not-found", variable.getName())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).extracting("name").allMatch(name -> name.equals(variable.getName()));
  }

  @Test
  void shouldQueryByNameFilterLike() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.name(b -> b.like(variable.getName().replace("proc", "*"))))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).extracting("name").allMatch(name -> name.equals(variable.getName()));
  }

  @Test
  void shouldQueryByNameAndValue() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.name(variable.getName()).value(variable.getValue()))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getVariableKey()).isEqualTo(variable.getVariableKey());
    assertThat(result.items().get(0).getValue()).isEqualTo(variable.getValue());
  }

  @Test
  void shouldQueryByValueFilterIn() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.value(b -> b.in("not-found", variable.getValue())))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    final var first = result.items().getFirst();
    assertThat(first.getVariableKey()).isEqualTo(variable.getVariableKey());
    assertThat(first.getValue()).isEqualTo(variable.getValue());
  }

  @Test
  void shouldQueryByValueFilterLike() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.value(b -> b.like(variable.getValue().replace("p", "?"))))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    final var first = result.items().getFirst();
    assertThat(first.getVariableKey()).isEqualTo(variable.getVariableKey());
    assertThat(first.getValue()).isEqualTo(variable.getValue());
  }

  @Test
  void shouldQueryByProcessInstanceKey() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
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
  void shouldQueryByProcessInstanceKeyFilterIn() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.processInstanceKey(b -> b.in(variable.getProcessInstanceKey())))
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
  void shouldQueryByProcessInstanceKeyFilterNotIn() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.processInstanceKey(b -> b.notIn(variable.getProcessInstanceKey())))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(0);
  }

  @Test
  void shouldQueryByTenantId() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
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
            .newVariableSearchRequest()
            .filter(f -> f.isTruncated(variable.isTruncated()))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items().stream().allMatch(Variable::isTruncated)).isFalse();

    // when
    final var resultTruncatedTrue =
        camundaClient.newVariableSearchRequest().filter(f -> f.isTruncated(true)).send().join();

    // then
    assertThat(resultTruncatedTrue.items().size()).isEqualTo(0);
  }

  private static void delpoyProcessFromResourcePath(
      final String resource, final String resourceName) {
    final InputStream process = UserTaskSearchTest.class.getResourceAsStream(resource);

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(Bpmn.readModelFromStream(process), resourceName)
        .send()
        .join();
  }

  @Test
  void shouldSortByNameASC() {
    // when

    final var result =
        camundaClient.newVariableSearchRequest().sort(s -> s.name().asc()).send().join();

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
    final var result =
        camundaClient.newVariableSearchRequest().sort(s -> s.value().desc()).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(5);

    final List<String> values =
        result.items().stream().map(item -> item.getValue()).collect(Collectors.toList());

    assertThat(values).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newVariableSearchRequest().send().join();
    final var thirdKey = resultAll.items().get(2).getVariableKey();

    final var resultSearchFrom =
        camundaClient.newVariableSearchRequest().page(p -> p.limit(2).from(2)).send().join();

    // then
    assertThat(resultSearchFrom.items().size()).isEqualTo(2);
    assertThat(resultSearchFrom.items().stream().findFirst().get().getVariableKey())
        .isEqualTo(thirdKey);
  }

  private static void waitForTasksBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newUserTaskSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(2);

              final var resultVariable = camundaClient.newVariableSearchRequest().send().join();
              assertThat(resultVariable.items().size()).isEqualTo(5);
            });
  }

  private static void startProcessInstance(final String processId) {
    camundaClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }
}
