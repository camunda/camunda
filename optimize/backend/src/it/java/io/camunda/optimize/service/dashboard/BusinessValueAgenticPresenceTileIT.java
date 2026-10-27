/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithTokens;
import static io.camunda.optimize.BusinessValueInstanceFixtures.bvdInstanceWithDuration;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AGENT_PRESENCE_BY_PROCESS_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Evaluates the agentic-presence tile, which combines {@code AGENT_INSTANCE TOTAL_TOKENS SUM} view
 * with the {@code hasAgentInstances()} filter — so non-agentic instances must be excluded even when
 * they belong to the same process definition key.
 */
class BusinessValueAgenticPresenceTileIT extends AbstractBrokerlessZeebeCCSMIT {

  private AgenticReportEvaluator reports;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(BusinessValueDashboardService.class).reconcile();
    reports =
        new AgenticReportEvaluator(
            embeddedOptimizeExtension.getBean(ReportEvaluationService.class));
  }

  @Test
  void shouldExcludeNonAgenticInstancesFromAgenticPresenceTile() {
    // given two agentic processes with different token totals, and one plain non-agentic process
    final String agentA = "agentic-a";
    final String agentB = "agentic-b";
    final String plain = "non-agentic";
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(agentA, 100L, 50L).build(),
            agenticInstanceWithTokens(agentB, 200L, 100L).build(),
            bvdInstanceWithDuration(plain, 1_000L).build()));

    // when evaluating the agent-presence-by-process tile
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(AGENT_PRESENCE_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then only the two agentic processes appear — the tile's hasAgentInstances filter drops the
    // plain process even though it is a completed instance
    assertThat(result)
        .extracting(MapResultEntryDto::getKey)
        .containsExactlyInAnyOrder(agentA, agentB);
  }
}
