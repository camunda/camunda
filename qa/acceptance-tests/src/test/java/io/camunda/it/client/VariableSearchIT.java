/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.*;
import static io.camunda.search.schema.config.IndexConfiguration.DEFAULT_VARIABLE_SIZE_THRESHOLD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.Variable;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
class VariableSearchIT {

  private static CamundaClient camundaClient;
  private static Variable variable;
  private static final String NORMAL_VAR_NAME = "process01";
  private static final String LARGE_VAR_NAME = "largeVariable";
  private static final String LARGE_VALUE = "b".repeat(DEFAULT_VARIABLE_SIZE_THRESHOLD + 10);

  @BeforeAll
  static void beforeAll() {
    final InputStream process =
        VariableSearchIT.class.getResourceAsStream("/process/bpm_variable_test.bpmn");
    deployProcessAndWaitForIt(
        camundaClient, Bpmn.readModelFromStream(process), "bpm_variable_test.bpmn");

    startProcessInstance(camundaClient, "bpmProcessVariable", Map.of(LARGE_VAR_NAME, LARGE_VALUE));

    waitForTasksBeingExported();

    variable =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.name(NORMAL_VAR_NAME))
            .send()
            .join()
            .items()
            .getFirst();
  }

  @Test
  void shouldQueryVariables() {
    // when
    final var result = camundaClient.newVariableSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(6);
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
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getVariableKey()).isEqualTo(variable.getVariableKey());
  }

  @Test
  void shouldQueryByNameAndScopeKey() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.scopeKey(variable.getScopeKey()).name(variable.getName()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getVariableKey()).isEqualTo(variable.getVariableKey());
    assertThat(result.items().getFirst().getScopeKey()).isEqualTo(variable.getScopeKey());
    assertThat(result.items().getFirst().getRootProcessInstanceKey())
        .isEqualTo(variable.getProcessInstanceKey());
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
    assertThat(result.items()).hasSize(2);
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
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getVariableKey()).isEqualTo(variable.getVariableKey());
    assertThat(result.items().getFirst().getValue()).isEqualTo(variable.getValue());
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
    assertThat(result.items()).hasSize(1);
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
    assertThat(result.items()).hasSize(1);
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
    assertThat(result.items()).hasSize(6);
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
    assertThat(result.items()).hasSize(6);
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
    assertThat(result.items()).isEmpty();
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
    assertThat(result.items()).hasSize(6);
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
    assertThat(result.items()).hasSize(5);
    assertThat(result.items().stream().allMatch(Variable::isTruncated)).isFalse();

    // when
    final var resultTruncatedTrue =
        camundaClient.newVariableSearchRequest().filter(f -> f.isTruncated(true)).send().join();

    // then
    assertThat(resultTruncatedTrue.items()).hasSize(1);
  }

  @Test
  void shouldReturnUntruncatedValues() {
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.name(LARGE_VAR_NAME))
            .withFullValues()
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var resultVar = result.items().getFirst();
    assertThat(resultVar.isTruncated()).isFalse();
  }

  @Test
  void shouldSortByNameASC() {
    // when

    final var result =
        camundaClient.newVariableSearchRequest().sort(s -> s.name().asc()).send().join();

    assertThat(result.items()).hasSize(6);

    final List<String> names =
        result.items().stream().map(Variable::getName).collect(Collectors.toList());

    assertThat(names).isSorted();
  }

  @Test
  void shouldVariableByKey() {
    // when
    final var result = camundaClient.newVariableGetRequest(variable.getVariableKey()).send().join();

    // then
    assertThat(result.getVariableKey()).isEqualTo(variable.getVariableKey());
    assertThat(result.getRootProcessInstanceKey()).isEqualTo(variable.getProcessInstanceKey());
  }

  @Test
  void shouldReturn404ForNotFoundVariableKey() {
    // when
    final long variableKey = new Random().nextLong();
    final var problemException =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(() -> camundaClient.newVariableGetRequest(variableKey).send().join())
            .actual();

    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .contains("Variable with key '%d' not found".formatted(variableKey));
  }

  @Test
  void shouldSortByValue() {
    // when
    final var resultAsc =
        camundaClient.newVariableSearchRequest().sort(s -> s.value().asc()).send().join();

    final var resultDesc =
        camundaClient.newVariableSearchRequest().sort(s -> s.value().desc()).send().join();

    TestHelper.assertSortedFlexible(resultAsc, resultDesc, Variable::getValue);
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newVariableSearchRequest().send().join();
    final var thirdKey = resultAll.items().get(2).getVariableKey();

    final var resultSearchFrom =
        camundaClient.newVariableSearchRequest().page(p -> p.limit(2).from(2)).send().join();

    // then
    assertThat(resultSearchFrom.items()).hasSize(2);
    assertThat(resultSearchFrom.items().stream().findFirst().get().getVariableKey())
        .isEqualTo(thirdKey);
  }

  @Test
  void shouldSearchAllWhereValueExists() {
    // when
    final var resultSearchFrom =
        camundaClient
            .newVariableSearchRequest()
            .filter(f -> f.value(v -> v.exists(true)))
            .send()
            .join();

    // then
    assertThat(resultSearchFrom.items().size()).isEqualTo(6);
  }

  private static void waitForTasksBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newUserTaskSearchRequest().send().join();
              assertThat(result.items()).hasSize(2);

              final var resultVariable = camundaClient.newVariableSearchRequest().send().join();
              assertThat(resultVariable.items()).hasSize(6);
            });
  }
}
