/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProcessExecutionPlanTest {

  @Test
  void shouldSupportGroupByLimitOnlyForAgenticTotalTokensGroupedByProcessDefinitionKey() {
    // given
    final ProcessExecutionPlan agenticPlan =
        ProcessExecutionPlan.PROCESS_AGENT_TOTAL_TOKENS_GROUP_BY_PROCESS_DEFINITION_KEY;

    // then the agentic top consumers plan opts into server-side top-N limiting
    assertThat(agenticPlan.supportsGroupByLimit()).isTrue();

    // and no other plan does, so pagination stays rejected everywhere else
    assertThat(Arrays.stream(ProcessExecutionPlan.values()))
        .filteredOn(plan -> plan != agenticPlan)
        .allSatisfy(plan -> assertThat(plan.supportsGroupByLimit()).isFalse());
  }
}
