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
import io.camunda.zeebe.client.api.response.Decision;
import io.camunda.zeebe.client.api.response.DecisionRequirements;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.EvaluateDecisionResponse;
import io.camunda.zeebe.client.api.search.response.DecisionDefinition;
import io.camunda.zeebe.client.api.search.response.DecisionDefinitionType;
import io.camunda.zeebe.client.api.search.response.DecisionInstance;
import io.camunda.zeebe.client.api.search.response.DecisionInstanceState;
import io.camunda.zeebe.client.impl.search.response.DecisionDefinitionImpl;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
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

@ZeebeIntegration
class DecisionQueryTest {
  private static final List<Decision> DEPLOYED_DECISIONS = new ArrayList<>();
  private static final List<DecisionRequirements> DEPLOYED_DECISION_REQUIREMENTS =
      new ArrayList<>();
  private static final List<EvaluateDecisionResponse> EVALUATED_DECISIONS = new ArrayList<>();
  private static ZeebeClient zeebeClient;

  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda();
  }

  @BeforeAll
  static void beforeAll() {
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
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

    EVALUATED_DECISIONS.addAll(
        DEPLOYED_DECISIONS.stream()
            .map(Decision::getDecisionKey)
            .map(k -> evaluateDecision(k))
            .toList());
    assertThat(EVALUATED_DECISIONS.size()).isEqualTo(3);
    waitForDecisionInstancesBeingExported();
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_DECISIONS.clear();
    DEPLOYED_DECISION_REQUIREMENTS.clear();
    EVALUATED_DECISIONS.clear();
  }

  @Test
  void shouldRetrieveAllDecisionDefinitions() {
    // when
    final var result =
        zeebeClient
            .newDecisionDefinitionQuery()
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
  void shouldRetrieveByDecisionKey() {
    // when
    final long decisionKey = DEPLOYED_DECISIONS.get(0).getDecisionKey();
    final var result =
        zeebeClient
            .newDecisionDefinitionQuery()
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
        zeebeClient
            .newDecisionDefinitionQuery()
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
        zeebeClient
            .newDecisionDefinitionQuery()
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
    final var result = zeebeClient.newDecisionDefinitionGetXmlRequest(decisionKey).send().join();

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
            () -> zeebeClient.newDecisionDefinitionGetXmlRequest(decisionKey).send().join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Decision Definition with decisionKey=%d not found".formatted(decisionKey));
  }

  @Test
  void shouldGetDecisionDefinition() {
    // when
    final long decisionKey = DEPLOYED_DECISIONS.get(0).getDecisionKey();
    final var result = zeebeClient.newDecisionDefinitionGetRequest(decisionKey).send().join();

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
            () -> zeebeClient.newDecisionDefinitionGetRequest(decisionKey).send().join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Decision Definition with decisionKey=%d not found".formatted(decisionKey));
  }

  @Test
  void shouldRetrieveDecisionRequirements() {
    // when
    final var result = zeebeClient.newDecisionRequirementsQuery().send().join();

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
        zeebeClient
            .newDecisionRequirementsQuery()
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
        zeebeClient.newDecisionRequirementsGetXmlRequest(decisionRequirementKey).send().join();

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
        zeebeClient
            .newDecisionRequirementsQuery()
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
        zeebeClient
            .newDecisionRequirementsQuery()
            .filter(f -> f.name(decisionRequirementName))
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
        zeebeClient
            .newDecisionRequirementsQuery()
            .filter(f -> f.tenantId("<default>"))
            .send()
            .join();

    final var resultWithNoTenant =
        zeebeClient.newDecisionRequirementsQuery().filter(f -> f.tenantId("Test")).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(resultWithNoTenant.items().size()).isEqualTo(0);
    result.items().forEach(item -> assertThat(item.getTenantId()).isEqualTo("<default>"));
  }

  @Test
  void shouldSortByDecisionRequirementsKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newDecisionRequirementsQuery()
            .sort(s -> s.decisionRequirementsKey().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newDecisionRequirementsQuery()
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
        zeebeClient
            .newDecisionRequirementsQuery()
            .sort(s -> s.decisionRequirementsId().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newDecisionRequirementsQuery()
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
        zeebeClient.newDecisionRequirementsQuery().sort(s -> s.name().asc()).send().join();
    final var resultDesc =
        zeebeClient.newDecisionRequirementsQuery().sort(s -> s.name().desc()).send().join();

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
        zeebeClient.newDecisionRequirementsQuery().sort(s -> s.version().asc()).send().join();
    final var resultDesc =
        zeebeClient.newDecisionRequirementsQuery().sort(s -> s.version().desc()).send().join();

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
        zeebeClient.newDecisionRequirementsQuery().page(p -> p.limit(1)).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    final var key = result.items().getFirst().getDecisionRequirementsKey();
    // apply searchAfter
    final var resultAfter =
        zeebeClient
            .newDecisionRequirementsQuery()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(2);
    final var keyAfter = resultAfter.items().getFirst().getDecisionRequirementsKey();
    // apply searchBefore
    final var resultBefore =
        zeebeClient
            .newDecisionRequirementsQuery()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(resultBefore.items().getFirst().getDecisionRequirementsKey()).isEqualTo(key);
  }

  @Test
  public void shouldRetrieveAllDecisionInstances() {
    // when
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .sort(b -> b.decisionInstanceKey().asc())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().stream().map(DecisionInstance::getDecisionInstanceKey).toList())
        .isEqualTo(
            EVALUATED_DECISIONS.stream()
                .map(EvaluateDecisionResponse::getDecisionInstanceKey)
                .sorted()
                .toList());
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionKey() {
    // when
    final long decisionKey = DEPLOYED_DECISIONS.get(0).getDecisionKey();
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(f -> f.decisionDefinitionKey(decisionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getDecisionDefinitionKey()).isEqualTo(decisionKey);
    assertThat(result.items().get(0).getDecisionInstanceKey())
        .isEqualTo(EVALUATED_DECISIONS.get(0).getDecisionInstanceKey());
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionInstanceKey() {
    // when
    final long decisionInstanceKey = EVALUATED_DECISIONS.get(0).getDecisionInstanceKey();
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(f -> f.decisionInstanceKey(decisionInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByStateAndType() {
    // when
    final DecisionInstanceState state = DecisionInstanceState.EVALUATED;
    final DecisionDefinitionType type = DecisionDefinitionType.DECISION_TABLE;
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(f -> f.state(state).decisionDefinitionType(type))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDmnDecisionIdAndDecisionVersion() {
    // when
    final String dmnDecisionId = DEPLOYED_DECISIONS.get(1).getDmnDecisionId();
    final int decisionVersion = DEPLOYED_DECISIONS.get(1).getVersion();
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(
                f ->
                    f.decisionDefinitionId(dmnDecisionId)
                        .decisionDefinitionVersion(decisionVersion))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getDecisionInstanceKey())
        .isEqualTo(EVALUATED_DECISIONS.get(1).getDecisionInstanceKey());
  }

  private static void waitForDecisionsBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(zeebeClient.newDecisionDefinitionQuery().send().join().items().size())
                  .isEqualTo(DEPLOYED_DECISIONS.size());
              assertThat(zeebeClient.newDecisionRequirementsQuery().send().join().items().size())
                  .isEqualTo(DEPLOYED_DECISION_REQUIREMENTS.size());
            });
  }

  private static void waitForDecisionInstancesBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(zeebeClient.newDecisionInstanceQuery().send().join().items().size())
                  .isEqualTo(EVALUATED_DECISIONS.size());
            });
  }

  private static DeploymentEvent deployResource(final String resourceName) {
    return zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private static EvaluateDecisionResponse evaluateDecision(final long decisionKey) {
    return zeebeClient
        .newEvaluateDecisionCommand()
        .decisionKey(decisionKey)
        .variables("{\"input1\": \"A\"}")
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
        zeebeClient.newDecisionRequirementsGetRequest(decisionRequirementsKey).send().join();

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
                zeebeClient
                    .newDecisionRequirementsGetRequest(decisionRequirementsKey)
                    .send()
                    .join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Decision requirements with decisionRequirementsKey=%d not found"
                .formatted(decisionRequirementsKey));
  }

  @Test
  void shouldGetDecisionInstance() {
    // when
    final long decisionInstanceKey = EVALUATED_DECISIONS.get(0).getDecisionInstanceKey();
    final var result = zeebeClient.newDecisionInstanceGetRequest(decisionInstanceKey).send().join();

    // then
    assertThat(result.getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
  }

  @Test
  void shouldReturn404ForNotFoundDecisionInstance() {
    // when
    final long decisionInstanceKey = new Random().nextLong();
    final var problemException =
        assertThrows(
            ProblemException.class,
            () -> zeebeClient.newDecisionInstanceGetRequest(decisionInstanceKey).send().join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Decision Instance with decisionInstanceKey=%d not found"
                .formatted(decisionInstanceKey));
  }
}
