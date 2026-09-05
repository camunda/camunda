/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.BusinessValueInstanceFixtures.businessRuleTaskNode;
import static io.camunda.optimize.BusinessValueInstanceFixtures.bvdInstanceWithFlowNodes;
import static io.camunda.optimize.BusinessValueInstanceFixtures.endEventNode;
import static io.camunda.optimize.BusinessValueInstanceFixtures.gatewayNode;
import static io.camunda.optimize.BusinessValueInstanceFixtures.manualTaskNode;
import static io.camunda.optimize.BusinessValueInstanceFixtures.scriptTaskNode;
import static io.camunda.optimize.BusinessValueInstanceFixtures.sendTaskNode;
import static io.camunda.optimize.BusinessValueInstanceFixtures.serviceTaskNode;
import static io.camunda.optimize.BusinessValueInstanceFixtures.startEventNode;
import static io.camunda.optimize.BusinessValueInstanceFixtures.userTaskNode;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AUTOMATION_RATE_AGGREGATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AUTOMATION_RATE_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the automation-rate view interpreter end-to-end through the seeded BVD tiles. The
 * interpreter counts flow-node instances of automated task types (serviceTask, businessRuleTask,
 * scriptTask, sendTask) and human task types (userTask, manualTask); structural elements (events,
 * gateways, containers) are excluded from both sides of the ratio. These tests seed distinct
 * flow-node distributions and assert on the ratio the tile actually returns — the aggregation
 * internals are covered separately by {@code ProcessViewAutomationRateInterpreterES|OSTest}.
 */
class BusinessValueAutomationRateTilesIT extends AbstractBrokerlessZeebeCCSMIT {

  private AgenticReportEvaluator reports;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(BusinessValueDashboardService.class).reconcile();
    reports =
        new AgenticReportEvaluator(
            embeddedOptimizeExtension.getBean(ReportEvaluationService.class));
  }

  @Test
  void shouldReturnHundredPercentWhenAllTasksAreAutomated() {
    // given an instance whose task-typed flow-nodes cover every automated task type
    final String procKey = "auto-full";
    persistProcessInstances(
        List.of(
            bvdInstanceWithFlowNodes(
                procKey,
                serviceTaskNode(),
                businessRuleTaskNode(),
                scriptTaskNode(),
                sendTaskNode())));

    // then aggregate automation rate is 100 %
    assertThat(reports.evaluateNumber(AUTOMATION_RATE_AGGREGATE_REPORT_ID, noExtraFilters()))
        .isEqualTo(100.0);
  }

  @Test
  void shouldReturnZeroPercentWhenAllTasksAreHuman() {
    // given an instance whose task-typed flow-nodes are only user/manual tasks
    final String procKey = "auto-none";
    persistProcessInstances(
        List.of(bvdInstanceWithFlowNodes(procKey, userTaskNode(), manualTaskNode())));

    // then aggregate automation rate is 0 % — distinct from null (which means "no task nodes")
    assertThat(reports.evaluateNumber(AUTOMATION_RATE_AGGREGATE_REPORT_ID, noExtraFilters()))
        .isEqualTo(0.0);
  }

  @Test
  void shouldReturnMixedRatioAcrossAutomatedAndHumanTasks() {
    // given four task flow-nodes: three automated, one human
    final String procKey = "auto-mixed";
    persistProcessInstances(
        List.of(
            bvdInstanceWithFlowNodes(
                procKey, serviceTaskNode(), serviceTaskNode(), serviceTaskNode(), userTaskNode())));

    // then aggregate automation rate is 75 % — 3 automated / (3 + 1) * 100
    assertThat(reports.evaluateNumber(AUTOMATION_RATE_AGGREGATE_REPORT_ID, noExtraFilters()))
        .isEqualTo(75.0);
  }

  @Test
  void shouldIgnoreStructuralNodesWhenComputingRatio() {
    // given instances that include gateways and events alongside a single automated task
    final String procKey = "auto-structural";
    persistProcessInstances(
        List.of(
            bvdInstanceWithFlowNodes(
                procKey, startEventNode(), gatewayNode(), serviceTaskNode(), endEventNode())));

    // then only the automated task is counted — the ratio ignores structural nodes entirely
    assertThat(reports.evaluateNumber(AUTOMATION_RATE_AGGREGATE_REPORT_ID, noExtraFilters()))
        .isEqualTo(100.0);
  }

  @Test
  void shouldReturnNullWhenNoTaskFlowNodesArePresent() {
    // given an instance whose flow-nodes are all structural (no service/user/manual/...)
    final String procKey = "auto-structural-only";
    persistProcessInstances(
        List.of(
            bvdInstanceWithFlowNodes(procKey, startEventNode(), gatewayNode(), endEventNode())));

    // then aggregate automation rate is null — the frontend renders "—" instead of a misleading 0 %
    assertThat(reports.evaluateNumber(AUTOMATION_RATE_AGGREGATE_REPORT_ID, noExtraFilters()))
        .isNull();
  }

  @Test
  void shouldReturnNullWhenNoInstancesExist() {
    // given no completed instances at all — the reconcile ran but nothing was seeded

    // then aggregate automation rate is null
    assertThat(reports.evaluateNumber(AUTOMATION_RATE_AGGREGATE_REPORT_ID, noExtraFilters()))
        .isNull();
  }

  @Test
  void shouldRankProcessesByAutomationRateDescForPerProcessTile() {
    // given three processes with clearly separated automation rates
    final String allAutomated = "rank-100";
    final String half = "rank-50";
    final String mostlyHuman = "rank-25";
    persistProcessInstances(
        List.of(
            bvdInstanceWithFlowNodes(allAutomated, serviceTaskNode(), serviceTaskNode()),
            bvdInstanceWithFlowNodes(half, serviceTaskNode(), userTaskNode()),
            bvdInstanceWithFlowNodes(
                mostlyHuman, serviceTaskNode(), userTaskNode(), userTaskNode(), userTaskNode())));

    // when evaluating the per-process automation rate tile
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(AUTOMATION_RATE_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then all three processes appear with the expected rates, sorted DESC
    assertThat(result)
        .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
        .containsExactly(tuple(allAutomated, 100.0), tuple(half, 50.0), tuple(mostlyHuman, 25.0));
  }

  @Test
  void shouldLabelPerProcessAutomationRateBarsWithTheProcessName() {
    // given a fully automated instance of a process whose BPMN id is not human-readable, plus a
    // later definition version carrying the name
    final String processKey = "order-fulfilment-v2";
    persistProcessInstances(
        List.of(bvdInstanceWithFlowNodes(processKey, serviceTaskNode(), serviceTaskNode())));
    persistProcessDefinitions(
        List.of(
            ProcessDefinitionOptimizeDto.builder()
                .id(processKey + ":2:2")
                .key(processKey)
                .version("2")
                .name("Order fulfilment")
                .dataSource(new ZeebeDataSourceDto("test-source", 1))
                .tenantId(ZEEBE_DEFAULT_TENANT_ID)
                .bpmn20Xml("<definitions/>")
                .build()));

    // when evaluating the per-process automation rate tile
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(AUTOMATION_RATE_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then the bar is keyed by the BPMN process id and labelled with the process name
    assertThat(result)
        .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getLabel)
        .containsExactly(tuple(processKey, "Order fulfilment"));
  }
}
