/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import java.util.Arrays;
import java.util.List;
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

  // A report's plan is looked up by its command key; duplicate keys fail that lookup at startup.
  @Test
  void shouldGiveEveryPlanItsOwnCommandKey() {
    // when
    final List<String> commandKeys =
        Arrays.stream(ProcessExecutionPlan.values())
            .map(ProcessExecutionPlan::getCommandKey)
            .toList();

    // then
    assertThat(commandKeys).doesNotHaveDuplicates();
  }

  @Test
  void shouldMatchAnAgentRunReportOnlyToThePlanWithItsDistribution() {
    // given an agentRun report grouped by end date but not split by model
    final EndDateGroupByDto byEndDate = new EndDateGroupByDto();
    byEndDate.setValue(new DateGroupByValueDto(AggregateByDateUnit.DAY));
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .view(new ProcessViewDto(ProcessViewEntity.AGENT_RUN, ViewProperty.TOTAL_TOKENS))
            .groupBy(byEndDate)
            .distributedBy(new NoneDistributedByDto())
            .build();

    // when
    final List<String> commandKeys = reportData.createCommandKeys();

    // then it does not silently evaluate as the split-by-model plan
    assertThat(commandKeys)
        .doesNotContain(
            ProcessExecutionPlan.AGENT_RUN_TOTAL_TOKENS_GROUP_BY_END_DATE_BY_AGENT_MODEL
                .getCommandKey());
  }
}
