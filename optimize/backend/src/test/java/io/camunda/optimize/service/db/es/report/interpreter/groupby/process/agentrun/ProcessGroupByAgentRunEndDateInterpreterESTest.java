/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProcessGroupByAgentRunEndDateInterpreterESTest {

  private final ProcessGroupByAgentRunEndDateInterpreterES underTest =
      new ProcessGroupByAgentRunEndDateInterpreterES(
          mock(ConfigurationService.class),
          mock(DateAggregationServiceES.class),
          mock(MinMaxStatsServiceES.class),
          mock(ProcessQueryFilterEnhancerES.class),
          mock(ProcessDistributedByInterpreterFacadeES.class),
          mock(ProcessViewInterpreterFacadeES.class));

  @Test
  void shouldGroupRunsByTheirEndDateInTheAgentIndex() {
    assertThat(underTest.getSupportedGroupBys())
        .containsExactly(ProcessGroupBy.PROCESS_GROUP_BY_AGENT_RUN_END_DATE);
    assertThat(underTest.getDateField()).isEqualTo("endDate");
    assertThat(underTest.getIndexNames(null)).containsExactly("agent-instance");
  }

  @Test
  void shouldLeaveNoAgentRunTrendOnTheRunStartDate() {
    // Trends follow main's trend reports, which group by end date.
    assertThat(ProcessExecutionPlan.values())
        .filteredOn(plan -> plan.name().startsWith("AGENT_RUN_"))
        .noneMatch(plan -> plan.name().contains("START_DATE"));
    assertThat(Arrays.stream(ProcessGroupBy.values()).map(Enum::name))
        .doesNotContain("PROCESS_GROUP_BY_AGENT_RUN_START_DATE");
  }
}
