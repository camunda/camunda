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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.response.DecisionRequirements;
import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
class DecisionSearchTest {
  private static final Map<Long, Decision> DEPLOYED_DECISIONS = new HashMap<>();
  private static final Map<Long, DecisionRequirements> DEPLOYED_DECISION_REQUIREMENTS =
      new HashMap<>();
  private static CamundaClient camundaClient;
  private static Decision exampleDecision;
  private static DecisionRequirements exampleDecisionRequirements;

  private static Decision multiVersionDecisionV1;
  private static Decision multiVersionDecisionV2;

  @BeforeAll
  static void beforeAll() {
    exampleDecision = deployResource("decisions/decision_model.dmn");
    multiVersionDecisionV1 = deployResource("decisions/decision_model_1.dmn");
    multiVersionDecisionV2 = deployResource("decisions/decision_model_1_v2.dmn");
    assertThat(DEPLOYED_DECISIONS).hasSize(3);
    assertThat(DEPLOYED_DECISION_REQUIREMENTS).hasSize(3);
    exampleDecisionRequirements =
        DEPLOYED_DECISION_REQUIREMENTS.get(exampleDecision.getDecisionRequirementsKey());
    waitForDecisionsBeingExported();
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_DECISIONS.clear();
    DEPLOYED_DECISION_REQUIREMENTS.clear();
  }

  @Test
  void shouldRetrieveAllDecisionDefinitions() {
    // when
    final var result =
        camundaClient
            .newDecisionDefinitionSearchRequest()
            .sort(b -> b.decisionDefinitionKey().asc())
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(3);
    for (final DecisionDefinition dd : result.items()) {
      validateDecisionFields(dd);
      validateDecisionRequirementsFields(dd);
    }
  }

  @Test
  void shouldSearchDecisionDefinitionsByDecisionDefinitionKey() {
    // when
    final var result =
        camundaClient
            .newDecisionDefinitionSearchRequest()
            .filter(f -> f.decisionDefinitionKey(exampleDecision.getDecisionKey()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    validateDecisionFields(result.items().getFirst());
  }

  @Test
  void shouldSearchDecisionDefinitionsByDecisionRequirementsName() {
    // when
    final var result =
        camundaClient
            .newDecisionDefinitionSearchRequest()
            .filter(
                f ->
                    f.decisionRequirementsName(
                        exampleDecisionRequirements.getDmnDecisionRequirementsName()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    validateDecisionFields(result.items().getFirst());
    validateDecisionRequirementsFields(result.items().getFirst());
  }

  @Test
  void shouldSearchDecisionDefinitionsByDecisionRequirementsVersion() {
    // when
    final var result =
        camundaClient
            .newDecisionDefinitionSearchRequest()
            .filter(f -> f.decisionRequirementsVersion(multiVersionDecisionV2.getVersion()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    validateDecisionFields(result.items().getFirst());
  }

  @Test
  void shouldRetrieveDecisionDefinitionWithDdrVersionReverseSorting() {
    // when
    final var result =
        camundaClient
            .newDecisionDefinitionSearchRequest()
            .filter(f -> f.decisionDefinitionId(multiVersionDecisionV1.getDmnDecisionId()))
            .sort(s -> s.decisionRequirementsVersion().desc())
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).getDecisionRequirementsVersion()).isEqualTo(2);
    assertThat(result.items().get(1).getDecisionRequirementsVersion()).isOne();
  }

  @Test
  void shouldRetrieveDecisionDefinitionWithFullFilter() {
    // when
    final long decisionKey = exampleDecision.getDecisionKey();
    final String dmnDecisionId = exampleDecision.getDmnDecisionId();
    final String dmnDecisionRequirementsId = exampleDecision.getDmnDecisionRequirementsId();
    final long decisionRequirementsKey = exampleDecision.getDecisionRequirementsKey();
    final String dmnDecisionName = exampleDecision.getDmnDecisionName();
    final int version = exampleDecision.getVersion();
    final String tenantId = exampleDecision.getTenantId();
    final var drd =
        DEPLOYED_DECISION_REQUIREMENTS.get(exampleDecision.getDecisionRequirementsKey());
    final String drdName = drd.getDmnDecisionRequirementsName();
    final int drdVersion = drd.getVersion();
    final var result =
        camundaClient
            .newDecisionDefinitionSearchRequest()
            .filter(
                f ->
                    f.decisionDefinitionKey(decisionKey)
                        .decisionDefinitionId(dmnDecisionId)
                        .decisionRequirementsKey(decisionRequirementsKey)
                        .name(dmnDecisionName)
                        .decisionRequirementsId(dmnDecisionRequirementsId)
                        .version(version)
                        .decisionRequirementsName(drdName)
                        .decisionRequirementsVersion(drdVersion)
                        .tenantId(tenantId))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    validateDecisionFields(result.items().getFirst());
    validateDecisionRequirementsFields(result.items().getFirst());
  }

  @Test
  void shouldRetrieveDecisionDefinitionWithVersionReverseSorting() {
    // when
    final var result =
        camundaClient
            .newDecisionDefinitionSearchRequest()
            .filter(f -> f.decisionDefinitionId(multiVersionDecisionV1.getDmnDecisionId()))
            .sort(s -> s.version().desc())
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).getVersion()).isEqualTo(2);
    assertThat(result.items().get(1).getVersion()).isOne();
  }

  @Test
  void shouldGetDecisionDefinitionXml() throws IOException {
    // when
    final var result =
        camundaClient
            .newDecisionDefinitionGetXmlRequest(exampleDecision.getDecisionKey())
            .send()
            .join();

    // then
    final String expected =
        Files.readString(
            Paths.get(
                Objects.requireNonNull(
                        getClass().getClassLoader().getResource("decisions/decision_model.dmn"))
                    .getPath()));
    assertThat(result).isNotEmpty().isEqualTo(expected);
  }

  @Test
  void shouldReturn404ForNotFoundDecisionKey() {
    // when
    final long decisionKey = new Random().nextLong();
    final var problemException =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () -> camundaClient.newDecisionDefinitionGetXmlRequest(decisionKey).send().join())
            .actual();
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .contains("Decision Definition with key '%d' not found".formatted(decisionKey));
  }

  @Test
  void shouldGetDecisionDefinition() {
    // when
    final var result =
        camundaClient
            .newDecisionDefinitionGetRequest(exampleDecision.getDecisionKey())
            .send()
            .join();

    // then
    validateDecisionFields(result);
  }

  @Test
  void shouldReturn404ForNotFoundDecisionDefinition() {
    // when
    final long decisionKey = new Random().nextLong();
    final var problemException =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () -> camundaClient.newDecisionDefinitionGetRequest(decisionKey).send().join())
            .actual();
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .contains("Decision Definition with key '%d' not found".formatted(decisionKey));
  }

  @Test
  void shouldRetrieveDecisionRequirements() {
    // when
    final var result = camundaClient.newDecisionRequirementsSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(3);
  }

  @Test
  void shouldRetrieveDrdByDecisionRequirementsKey() {
    // when
    final var result =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .filter(f -> f.decisionRequirementsKey(exampleDecision.getDecisionRequirementsKey()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getDecisionRequirementsKey())
        .isEqualTo(exampleDecision.getDecisionRequirementsKey());
  }

  @Test
  void shouldGetDecisionRequirementsXml() throws IOException {
    // when
    final var result =
        camundaClient
            .newDecisionRequirementsGetXmlRequest(exampleDecision.getDecisionRequirementsKey())
            .send()
            .join();

    // then
    final String expected =
        Files.readString(
            Paths.get(
                Objects.requireNonNull(
                        getClass().getClassLoader().getResource("decisions/decision_model.dmn"))
                    .getPath()));
    assertThat(result).isNotEmpty().isEqualTo(expected);
  }

  @Test
  void shouldRetrieveDrdByDecisionRequirementsId() {
    // when
    final var result =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .filter(f -> f.decisionRequirementsId(exampleDecision.getDmnDecisionRequirementsId()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getDmnDecisionRequirementsId())
        .isEqualTo(exampleDecision.getDmnDecisionRequirementsId());
  }

  @Test
  void shouldRetrieveDrdByDecisionRequirementsName() {
    // when
    final var result =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .filter(
                f ->
                    f.decisionRequirementsName(
                        exampleDecisionRequirements.getDmnDecisionRequirementsName()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getDmnDecisionRequirementsName())
        .isEqualTo(exampleDecisionRequirements.getDmnDecisionRequirementsName());
  }

  @Test
  void shouldRetrieveByTenantId() {
    // when
    final var result =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .filter(f -> f.tenantId("<default>"))
            .send()
            .join();

    final var resultWithNoTenant =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .filter(f -> f.tenantId("Test"))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(resultWithNoTenant.items()).isEmpty();
    result.items().forEach(item -> assertThat(item.getTenantId()).isEqualTo("<default>"));
  }

  @Test
  void shouldRetrieveByResourceName() {
    // when
    final var result =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .filter(f -> f.resourceName("decisions/decision_model_1.dmn"))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    result
        .items()
        .forEach(
            item -> assertThat(item.getResourceName()).isEqualTo("decisions/decision_model_1.dmn"));
  }

  @Test
  void shouldSortByDecisionRequirementsKey() {
    // when
    final var resultAsc =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .sort(s -> s.decisionRequirementsKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .sort(s -> s.decisionRequirementsKey().desc())
            .send()
            .join();

    // Assert that the creation date of item 0 is before item 1
    assertThat(resultAsc.items().get(0).getDecisionRequirementsKey())
        .isLessThan(resultAsc.items().get(1).getDecisionRequirementsKey());

    // Assert that the creation date of item 0 is before item 1
    assertThat(resultDesc.items().get(0).getDecisionRequirementsKey())
        .isGreaterThan(resultDesc.items().get(1).getDecisionRequirementsKey());
  }

  @Test
  void shouldSortByDecisionRequirementsId() {
    // when
    final var resultAsc =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .sort(s -> s.decisionRequirementsId().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .sort(s -> s.decisionRequirementsId().desc())
            .send()
            .join();

    // Extract unique IDs from the results
    // As we have 2 process with the same definition, it is necessary check the full list of IDs
    final List<String> uniqueAscIds =
        resultAsc.items().stream()
            .map(DecisionRequirements::getDmnDecisionRequirementsId)
            .distinct()
            .toList();

    final List<String> uniqueDescIds =
        resultDesc.items().stream()
            .map(DecisionRequirements::getDmnDecisionRequirementsId)
            .distinct()
            .toList();

    // Ensure there are at least two unique IDs to compare
    assertThat(uniqueAscIds).hasSizeGreaterThan(1);
    assertThat(uniqueDescIds).hasSizeGreaterThan(1);

    // Assert that the first unique ID in ascending order is less than the second unique ID
    assertThat(uniqueAscIds.get(0)).isLessThan(uniqueAscIds.get(1));

    // Assert that the first unique ID in descending order is greater than the second unique ID
    assertThat(uniqueDescIds.get(0)).isGreaterThan(uniqueDescIds.get(1));
  }

  @Test
  void shouldSortByDecisionRequirementsName() {
    // when
    final var resultAsc =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .sort(s -> s.decisionRequirementsName().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .sort(s -> s.decisionRequirementsName().desc())
            .send()
            .join();

    // Extract unique names from the results
    final List<String> uniqueAscNames =
        resultAsc.items().stream()
            .map(DecisionRequirements::getDmnDecisionRequirementsName)
            .distinct()
            .toList();

    final List<String> uniqueDescNames =
        resultDesc.items().stream()
            .map(DecisionRequirements::getDmnDecisionRequirementsName)
            .distinct()
            .toList();

    // Ensure there are at least two unique names to compare
    assertThat(uniqueAscNames).hasSizeGreaterThan(1);
    assertThat(uniqueDescNames).hasSizeGreaterThan(1);

    // Assert that the first unique name in ascending order is less than the second unique name
    assertThat(uniqueAscNames.get(0)).isLessThan(uniqueAscNames.get(1));

    // Assert that the first unique name in descending order is greater than the second unique name
    assertThat(uniqueDescNames.get(0)).isGreaterThan(uniqueDescNames.get(1));
  }

  @Test
  void shouldSortByDecisionRequirementsVersion() {
    // when
    final var resultAsc =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .sort(s -> s.version().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .sort(s -> s.version().desc())
            .send()
            .join();

    // Extract unique names from the results
    final List<Integer> uniqueAscVersions =
        resultAsc.items().stream().map(DecisionRequirements::getVersion).distinct().toList();

    final List<Integer> uniqueDescVersions =
        resultDesc.items().stream().map(DecisionRequirements::getVersion).distinct().toList();

    // Ensure there are at least two unique names to compare
    assertThat(uniqueAscVersions).hasSizeGreaterThan(1);
    assertThat(uniqueDescVersions).hasSizeGreaterThan(1);

    // Assert that the first unique name in ascending order is less than the second unique name
    assertThat(uniqueAscVersions.get(0)).isLessThan(uniqueAscVersions.get(1));

    // Assert that the first unique name in descending order is greater than the second unique name
    assertThat(uniqueDescVersions.get(0)).isGreaterThan(uniqueDescVersions.get(1));
  }

  @Test
  public void shouldValidatePagination() {
    final var result =
        camundaClient.newDecisionRequirementsSearchRequest().page(p -> p.limit(1)).send().join();
    assertThat(result.items()).hasSize(1);
    final var key = result.items().getFirst().getDecisionRequirementsKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .page(p -> p.after(result.page().endCursor()))
            .send()
            .join();

    assertThat(resultAfter.items()).hasSize(2);

    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .page(p -> p.before(resultAfter.page().startCursor()))
            .send()
            .join();
    assertThat(result.items()).hasSize(1);
    assertThat(resultBefore.items().getFirst().getDecisionRequirementsKey()).isEqualTo(key);
  }

  @Test
  void shouldGetDecisionRequirements() {
    // when
    final var result =
        camundaClient
            .newDecisionRequirementsGetRequest(
                exampleDecisionRequirements.getDecisionRequirementsKey())
            .send()
            .join();

    // then
    assertThat(result.getDecisionRequirementsKey())
        .isEqualTo(exampleDecisionRequirements.getDecisionRequirementsKey());
  }

  @Test
  void shouldReturn404ForNotFoundDecisionRequirements() {
    // when
    final long decisionRequirementsKey = new Random().nextLong();
    final var problemException =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newDecisionRequirementsGetRequest(decisionRequirementsKey)
                        .send()
                        .join())
            .actual();
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .contains(
            "Decision Requirements with key '%d' not found".formatted(decisionRequirementsKey));
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newDecisionRequirementsSearchRequest().send().join();

    final var resultWithLimit =
        camundaClient.newDecisionRequirementsSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(resultWithLimit.items()).hasSize(2);

    final var thirdKey = resultAll.items().get(2).getDecisionRequirementsKey();

    final var resultSearchFrom =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .page(p -> p.limit(2).from(2))
            .send()
            .join();

    // then
    assertThat(resultSearchFrom.items().stream().findFirst().get().getDecisionRequirementsKey())
        .isEqualTo(thirdKey);
  }

  private static void waitForDecisionsBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(camundaClient.newDecisionDefinitionSearchRequest().send().join().items())
                  .hasSameSizeAs(DEPLOYED_DECISIONS.keySet());
              assertThat(camundaClient.newDecisionRequirementsSearchRequest().send().join().items())
                  .hasSameSizeAs(DEPLOYED_DECISION_REQUIREMENTS.keySet());
            });
  }

  private static Decision deployResource(final String resourceName) {
    final var deploymentEvent =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath(resourceName)
            .send()
            .join();

    DEPLOYED_DECISIONS.putAll(
        deploymentEvent.getDecisions().stream()
            .collect(Collectors.toMap(Decision::getDecisionKey, d -> d)));
    DEPLOYED_DECISION_REQUIREMENTS.putAll(
        deploymentEvent.getDecisionRequirements().stream()
            .collect(Collectors.toMap(DecisionRequirements::getDecisionRequirementsKey, dr -> dr)));
    return deploymentEvent.getDecisions().getFirst();
  }

  private void validateDecisionFields(final DecisionDefinition decisionDefinition) {
    assertThat(DEPLOYED_DECISIONS).containsKey(decisionDefinition.getDecisionKey());
    final Decision decision = DEPLOYED_DECISIONS.get(decisionDefinition.getDecisionKey());
    assertThat(decisionDefinition.getDecisionKey()).isEqualTo(decision.getDecisionKey());
    assertThat(decisionDefinition.getDmnDecisionId()).isEqualTo(decision.getDmnDecisionId());
    assertThat(decisionDefinition.getDmnDecisionName()).isEqualTo(decision.getDmnDecisionName());
    assertThat(decisionDefinition.getVersion()).isEqualTo(decision.getVersion());
    assertThat(decisionDefinition.getDmnDecisionRequirementsId())
        .isEqualTo(decision.getDmnDecisionRequirementsId());
    assertThat(decisionDefinition.getDecisionRequirementsKey())
        .isEqualTo(decision.getDecisionRequirementsKey());
    assertThat(decisionDefinition.getTenantId()).isEqualTo(decision.getTenantId());
  }

  private void validateDecisionRequirementsFields(final DecisionDefinition decisionDefinition) {
    assertThat(DEPLOYED_DECISION_REQUIREMENTS)
        .containsKey(decisionDefinition.getDecisionRequirementsKey());
    final DecisionRequirements drd =
        DEPLOYED_DECISION_REQUIREMENTS.get(decisionDefinition.getDecisionRequirementsKey());
    assertThat(decisionDefinition.getDecisionRequirementsName())
        .isEqualTo(drd.getDmnDecisionRequirementsName());
    assertThat(decisionDefinition.getDecisionRequirementsVersion()).isEqualTo(drd.getVersion());
  }
}
