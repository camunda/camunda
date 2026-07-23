/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.serving.support.CohortScenarioFixtures;
import io.camunda.analytics.lake.serving.tools.ConditionsService.ConditionsResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code POST /api/tools/conditions}: a plain {@code variants} dictionary lookup. */
@SpringBootTest
class ConditionsServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private ConditionsService conditionsService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    CohortScenarioFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldReturnTheVariantsElementAndFlowLists() {
    final ConditionsResult result =
        conditionsService.conditions(new ConditionsQuery("V1", CohortScenarioFixtures.PROCESS_ID));

    assertThat(result.elements()).containsExactly("task1", "task2");
    assertThat(result.flows()).containsExactly("flow1");
    assertThat(result.firstSeen()).isNotNull();
  }

  @Test
  void shouldReturnADifferentVariantsOwnLists() {
    final ConditionsResult result =
        conditionsService.conditions(new ConditionsQuery("V2", CohortScenarioFixtures.PROCESS_ID));

    assertThat(result.elements()).containsExactly("task1", "task3");
    assertThat(result.flows()).containsExactly("flow2");
  }

  @Test
  void shouldRejectAnUnknownVariant() {
    assertThatThrownBy(
            () ->
                conditionsService.conditions(
                    new ConditionsQuery("does-not-exist", CohortScenarioFixtures.PROCESS_ID)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
