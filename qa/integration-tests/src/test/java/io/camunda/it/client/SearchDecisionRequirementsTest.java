/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class SearchDecisionRequirementsTest {
  private static Long decisionRequirementKey;
  private static String decisionRequirementId;
  private static String decisionRequirementName;

  @TestZeebe
  private static TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();

  private static ZeebeClient zeebeClient;

  @BeforeAll
  public static void setup() {
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    deployProcessModel("decision/decision_model.dmn");
    deployProcessModel("decision/decision_model_1.dmn");
    deployProcessModel("decision/decision_model_1_v2.dmn");

    waitForDecisionRequirementsBeingExported();
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
  void shouldRetrieveByDecisionRequirementsId() {
    // when
    final var result =
        zeebeClient
            .newDecisionRequirementsQuery()
            .filter(f -> f.dmnDecisionRequirementsId(decisionRequirementId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getDmnDecisionRequirementsId())
        .isEqualTo(decisionRequirementId);
  }

  @Test
  void shouldRetrieveByDecisionRequirementsName() {
    // when
    final var result =
        zeebeClient
            .newDecisionRequirementsQuery()
            .filter(f -> f.dmnDecisionRequirementsName(decisionRequirementName))
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
            .sort(s -> s.dmnDecisionRequirementsId().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newDecisionRequirementsQuery()
            .sort(s -> s.dmnDecisionRequirementsId().desc())
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
        zeebeClient
            .newDecisionRequirementsQuery()
            .sort(s -> s.dmnDecisionRequirementsName().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newDecisionRequirementsQuery()
            .sort(s -> s.dmnDecisionRequirementsName().desc())
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

  private static void waitForDecisionRequirementsBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newDecisionRequirementsQuery().send().join();
              assertThat(result.items().size()).isEqualTo(3);

              // Store the decision requirements key for later use for filter tests
              decisionRequirementKey = result.items().get(0).getDecisionRequirementsKey();
              decisionRequirementId = result.items().get(0).getDmnDecisionRequirementsId();
              decisionRequirementName = result.items().get(0).getDmnDecisionRequirementsName();
            });
  }

  private static void deployProcessModel(final String resourceName) {
    zeebeClient.newDeployResourceCommand().addResourceFromClasspath(resourceName).send().join();
  }
}
