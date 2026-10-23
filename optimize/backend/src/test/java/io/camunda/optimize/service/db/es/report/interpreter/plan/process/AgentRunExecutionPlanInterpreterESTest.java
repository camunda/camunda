/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.interpreter.plan.process.GenericProcessExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AgentRunExecutionPlanInterpreterESTest {

  private final AgentRunExecutionPlanInterpreterES underTest =
      new AgentRunExecutionPlanInterpreterES(
          mock(ProcessDefinitionReader.class),
          mock(OptimizeElasticsearchClient.class),
          mock(ProcessQueryFilterEnhancerES.class),
          mock(ProcessGroupByInterpreterFacadeES.class),
          mock(ProcessViewInterpreterFacadeES.class));

  @Test
  void shouldEvaluateExactlyTheAgentRunPlans() {
    // when
    final Set<ProcessExecutionPlan> plans = underTest.getSupportedExecutionPlans();

    // then
    assertThat(plans)
        .hasSize(14)
        .allSatisfy(
            plan ->
                assertThat(plan.getView().getProcessViewDto().getEntity())
                    .isEqualTo(ProcessViewEntity.AGENT_RUN));
  }

  @Test
  void shouldLeaveAgentRunPlansOutOfTheProcessInstanceInterpreter() {
    // given
    final Set<ProcessExecutionPlan> genericPlans =
        mock(GenericProcessExecutionPlanInterpreter.class, CALLS_REAL_METHODS)
            .getSupportedExecutionPlans();

    // then
    assertThat(genericPlans).doesNotContainAnyElementsOf(underTest.getSupportedExecutionPlans());
  }

  @Test
  void shouldGiveEveryPlanAUniqueCommandKey() {
    // Plans are looked up by command key; a duplicate would make Optimize fail on startup.
    assertThat(
            Arrays.stream(ProcessExecutionPlan.values())
                .collect(
                    Collectors.toMap(
                        ProcessExecutionPlan::getCommandKey,
                        Function.identity(),
                        (a, b) -> {
                          throw new IllegalStateException(
                              "Duplicate command key " + a.getCommandKey() + ": " + a + ", " + b);
                        })))
        .isInstanceOf(Map.class);
  }

  @Test
  void shouldQueryTheAgentInstanceIndex() {
    assertThat(underTest.getIndexNames(null)).containsExactly("agent-instance");
  }
}
