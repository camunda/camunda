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
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.response.DecisionRequirements;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.impl.search.response.DecisionDefinitionImpl;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
class DecisionSearchTest {
  private static final List<Decision> DEPLOYED_DECISIONS = new ArrayList<>();
  private static final List<DecisionRequirements> DEPLOYED_DECISION_REQUIREMENTS =
      new ArrayList<>();
  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    Stream.of(
            "decisions/decision_model.dmn",
            "decisions/decision_model_1.dmn",
            "decisions/decision_model_1_v2.dmn")
        .map(dmn -> deployResource(dmn))
        .forEach(
            deploymentEvent -> {
              DEPLOYED_DECISIONS.addAll(deploymentEvent.getDecisions());
              DEPLOYED_DECISION_REQUIREMENTS.addAll(deploymentEvent.getDecisionRequirements());
            });
    assertThat(DEPLOYED_DECISIONS.size()).isEqualTo(3);
    assertThat(DEPLOYED_DECISION_REQUIREMENTS.size()).isEqualTo(3);
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
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items())
        .isEqualTo(
            DEPLOYED_DECISIONS.stream()
                .map(this::toDecisionDefinition)
                .sorted(Comparator.comparing(DecisionDefinition::getDecisionKey))
                .toList());
  }

  @Test
  void shouldSearchDecisionDefinitionsByDecisionDefinitionKey() {
    // when
    final long decisionKey = DEPLOYED_DECISIONS.get(0).getDecisionKey();
    final var result =
        camundaClient
            .newDecisionDefinitionSearchRequest()
            .filter(f -> f.decisionDefinitionKey(decisionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0)).isEqualTo(toDecisionDefinition(DEPLOYED_DECISIONS.get(0)));
  }

  @Test
  void shouldRetrieveDecisionDefinitionWithFullFilter() {
    // when
    final Decision decisionDef = DEPLOYED_DECISIONS.get(1);
    final long decisionKey = decisionDef.getDecisionKey();
    final String dmnDecisionId = decisionDef.getDmnDecisionId();
    final String dmnDecisionRequirementsId = decisionDef.getDmnDecisionRequirementsId();
    final long decisionRequirementsKey = decisionDef.getDecisionRequirementsKey();
    final String dmnDecisionName = decisionDef.getDmnDecisionName();
    final int version = decisionDef.getVersion();
    final String tenantId = decisionDef.getTenantId();
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
                        .tenantId(tenantId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0)).isEqualTo(toDecisionDefinition(decisionDef));
  }

  @Test
  void shouldRetrieveDecisionDefinitionWithVersionReverseSorting() {
    // when
    final Decision decisionDefV1 = DEPLOYED_DECISIONS.get(1);
    final Decision decisionDefV2 = DEPLOYED_DECISIONS.get(2);
    final String dmnDecisionId = decisionDefV1.getDmnDecisionId();
    final var result =
        camundaClient
            .newDecisionDefinitionSearchRequest()
            .filter(f -> f.decisionDefinitionId(dmnDecisionId))
            .sort(s -> s.version().desc())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().get(0)).isEqualTo(toDecisionDefinition(decisionDefV2));
    assertThat(result.items().get(1)).isEqualTo(toDecisionDefinition(decisionDefV1));
  }

  @Test
  void shouldGetDecisionDefinitionXml() throws IOException {
    // when
    final long decisionKey = DEPLOYED_DECISIONS.get(0).getDecisionKey();
    final var result = camundaClient.newDecisionDefinitionGetXmlRequest(decisionKey).send().join();

    // then
    final String expected =
        Files.readString(
            Paths.get(
                Objects.requireNonNull(
                        getClass().getClassLoader().getResource("decisions/decision_model.dmn"))
                    .getPath()));
    assertThat(result).isNotEmpty();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldReturn404ForNotFoundDecisionKey() {
    // when
    final long decisionKey = new Random().nextLong();
    final var problemException =
        assertThrows(
            ProblemException.class,
            () -> camundaClient.newDecisionDefinitionGetXmlRequest(decisionKey).send().join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Decision definition with key %d not found".formatted(decisionKey));
  }

  @Test
  void shouldGetDecisionDefinition() {
    // when
    final long decisionKey = DEPLOYED_DECISIONS.get(0).getDecisionKey();
    final var result = camundaClient.newDecisionDefinitionGetRequest(decisionKey).send().join();

    // then
    assertThat(result).isEqualTo(toDecisionDefinition(DEPLOYED_DECISIONS.get(0)));
  }

  @Test
  void shouldReturn404ForNotFoundDecisionDefinition() {
    // when
    final long decisionKey = new Random().nextLong();
    final var problemException =
        assertThrows(
            ProblemException.class,
            () -> camundaClient.newDecisionDefinitionGetRequest(decisionKey).send().join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Decision definition with key %d not found".formatted(decisionKey));
  }

  @Test
  void shouldRetrieveDecisionRequirements() {
    // when
    final var result = camundaClient.newDecisionRequirementsSearchRequest().send().join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
  }

  @Test
  void shouldRetrieveByDecisionRequirementsKey() {
    // given
    final long decisionRequirementKey =
        DEPLOYED_DECISION_REQUIREMENTS.get(0).getDecisionRequirementsKey();

    // when
    final var result =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .filter(f -> f.decisionRequirementsKey(decisionRequirementKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getDecisionRequirementsKey())
        .isEqualTo(decisionRequirementKey);
  }

  @Test
  void shouldGetDecisionRequirementsXml() throws IOException {
    // when
    final long decisionRequirementKey =
        DEPLOYED_DECISION_REQUIREMENTS.get(0).getDecisionRequirementsKey();

    final var result =
        camundaClient.newDecisionRequirementsGetXmlRequest(decisionRequirementKey).send().join();

    // then
    final String expected =
        Files.readString(
            Paths.get(
                Objects.requireNonNull(
                        getClass().getClassLoader().getResource("decisions/decision_model.dmn"))
                    .getPath()));
    assertThat(result).isNotEmpty();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldRetrieveByDecisionRequirementsId() {
    // given
    final String decisionRequirementId =
        DEPLOYED_DECISION_REQUIREMENTS.get(0).getDmnDecisionRequirementsId();

    // when
    final var result =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .filter(f -> f.decisionRequirementsId(decisionRequirementId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getDmnDecisionRequirementsId())
        .isEqualTo(decisionRequirementId);
  }

  @Test
  void shouldRetrieveByDecisionRequirementsName() {
    // given
    final String decisionRequirementName =
        DEPLOYED_DECISION_REQUIREMENTS.get(0).getDmnDecisionRequirementsName();

    // when
    final var result =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .filter(f -> f.decisionRequirementsName(decisionRequirementName))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getDmnDecisionRequirementsName())
        .isEqualTo(decisionRequirementName);
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
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(resultWithNoTenant.items().size()).isEqualTo(0);
    result.items().forEach(item -> assertThat(item.getTenantId()).isEqualTo("<default>"));
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
            .map(item -> item.getDmnDecisionRequirementsId())
            .distinct()
            .collect(Collectors.toList());

    final List<String> uniqueDescIds =
        resultDesc.items().stream()
            .map(item -> item.getDmnDecisionRequirementsId())
            .distinct()
            .collect(Collectors.toList());

    // Ensure there are at least two unique IDs to compare
    assertThat(uniqueAscIds.size()).isGreaterThan(1);
    assertThat(uniqueDescIds.size()).isGreaterThan(1);

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
            .map(item -> item.getDmnDecisionRequirementsName())
            .distinct()
            .collect(Collectors.toList());

    final List<String> uniqueDescNames =
        resultDesc.items().stream()
            .map(item -> item.getDmnDecisionRequirementsName())
            .distinct()
            .collect(Collectors.toList());

    // Ensure there are at least two unique names to compare
    assertThat(uniqueAscNames.size()).isGreaterThan(1);
    assertThat(uniqueDescNames.size()).isGreaterThan(1);

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
        resultAsc.items().stream()
            .map(item -> item.getVersion())
            .distinct()
            .collect(Collectors.toList());

    final List<Integer> uniqueDescVersions =
        resultDesc.items().stream()
            .map(item -> item.getVersion())
            .distinct()
            .collect(Collectors.toList());

    // Ensure there are at least two unique names to compare
    assertThat(uniqueAscVersions.size()).isGreaterThan(1);
    assertThat(uniqueDescVersions.size()).isGreaterThan(1);

    // Assert that the first unique name in ascending order is less than the second unique name
    assertThat(uniqueAscVersions.get(0)).isLessThan(uniqueAscVersions.get(1));

    // Assert that the first unique name in descending order is greater than the second unique name
    assertThat(uniqueDescVersions.get(0)).isGreaterThan(uniqueDescVersions.get(1));
  }

  @Test
  public void shouldValidatePagination() {
    final var result =
        camundaClient.newDecisionRequirementsSearchRequest().page(p -> p.limit(1)).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    final var key = result.items().getFirst().getDecisionRequirementsKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(2);
    final var keyAfter = resultAfter.items().getFirst().getDecisionRequirementsKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newDecisionRequirementsSearchRequest()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(resultBefore.items().getFirst().getDecisionRequirementsKey()).isEqualTo(key);
  }

  private static void waitForDecisionsBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newDecisionDefinitionSearchRequest()
                          .send()
                          .join()
                          .items()
                          .size())
                  .isEqualTo(DEPLOYED_DECISIONS.size());
              assertThat(
                      camundaClient
                          .newDecisionRequirementsSearchRequest()
                          .send()
                          .join()
                          .items()
                          .size())
                  .isEqualTo(DEPLOYED_DECISION_REQUIREMENTS.size());
            });
  }

  private static DeploymentEvent deployResource(final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private DecisionDefinition toDecisionDefinition(final Decision decision) {
    return new DecisionDefinitionImpl(
        decision.getDmnDecisionId(),
        decision.getDmnDecisionName(),
        decision.getVersion(),
        decision.getDecisionKey(),
        decision.getDmnDecisionRequirementsId(),
        decision.getDecisionRequirementsKey(),
        decision.getTenantId());
  }

  @Test
  void shouldGetDecisionRequirements() {
    // when
    final long decisionRequirementsKey = DEPLOYED_DECISIONS.get(0).getDecisionRequirementsKey();
    final var result =
        camundaClient.newDecisionRequirementsGetRequest(decisionRequirementsKey).send().join();

    // then
    assertThat(result.getDecisionRequirementsKey())
        .isEqualTo(DEPLOYED_DECISIONS.get(0).getDecisionRequirementsKey());
  }

  @Test
  void shouldReturn404ForNotFoundDecisionRequirements() {
    // when
    final long decisionRequirementsKey = new Random().nextLong();
    final var problemException =
        assertThrows(
            ProblemException.class,
            () ->
                camundaClient
                    .newDecisionRequirementsGetRequest(decisionRequirementsKey)
                    .send()
                    .join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Decision requirements with key %d not found".formatted(decisionRequirementsKey));
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newDecisionRequirementsSearchRequest().send().join();

    final var resultWithLimit =
        camundaClient.newDecisionRequirementsSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(resultWithLimit.items().size()).isEqualTo(2);

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
}
