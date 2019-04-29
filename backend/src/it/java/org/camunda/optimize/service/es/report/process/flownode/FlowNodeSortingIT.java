/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.flownode;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessCountReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;


public class FlowNodeSortingIT {

  private static final String LABEL_SUFFIX = "_label";
  private static final String TEST_ACTIVITY = "testActivity";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void customOrderOnResultLabelForFrequencyReports() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithTwoTasksAndLabels();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.ASC));
    final ProcessCountReportMapResultDto result = evaluateFrequencyReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(4));
    final List<String> resultLabels = resultData.stream()
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toList());
    assertThat(
      resultLabels,
      // expect ascending order
      contains(resultLabels.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void customOrderOnResultLabelForDurationReports() {
    // given
    final ProcessInstanceEngineDto processDefinition = deployProcessWithTwoTasksAndLabels();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.ASC));
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse =
      evaluateDurationReport(reportData);

    // then
    List<MapResultEntryDto<AggregationResultDto>> resultData = evaluationResponse.getResult().getData();
    assertThat(resultData.size(), is(4));
    final List<String> resultLabels = resultData.stream()
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toList());
    assertThat(
      resultLabels,
      // expect ascending order
      contains(resultLabels.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void ifNameIsNotAvailableKeyIsUsedAsLabel() {
    // given
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("startKey")
        .name("startName")
      .serviceTask("task1Key")
        .name("task1Name")
        .camundaExpression("${true}")
      .serviceTask("task2Key")
        .name("")
        .camundaExpression("${true}")
      .endEvent("endKey")
        .name(null)
      .done();
    // @formatter:on
    ProcessInstanceEngineDto processInstanceDto = engineRule.deployAndStartProcess(modelInstance);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.ASC));
    final ProcessCountReportMapResultDto result = evaluateFrequencyReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(4));
    final List<String> resultLabels = resultData.stream()
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toList());
    assertThat(
      resultLabels,
      // expect ascending order
      contains("endKey", "startName", "task1Name", "task2Key")
    );
  }

  @Test
  public void labelSortingIsCaseInsensitive() {
    // given
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
        .name("ax")
      .serviceTask(TEST_ACTIVITY)
        .name("fooBar1")
        .camundaExpression("${true}")
      .serviceTask(TEST_ACTIVITY_2)
        .name("Ac")
        .camundaExpression("${true}")
      .endEvent("end")
        .name("foobar2")
      .done();
    // @formatter:on
    ProcessInstanceEngineDto processInstanceDto = engineRule.deployAndStartProcess(modelInstance);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.ASC));
    final ProcessCountReportMapResultDto result = evaluateFrequencyReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(4));
    final List<String> resultLabels = resultData.stream()
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toList());
    assertThat(
      resultLabels,
      // expect ascending order
      contains("Ac", "ax", "fooBar1", "foobar2")
    );
  }

  @Test
  public void keySortingIsCaseInsensitive() {
    // given
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("ax")
      .serviceTask("fooBar1")
        .camundaExpression("${true}")
      .serviceTask("Ac")
        .camundaExpression("${true}")
      .endEvent("foobar2")
      .done();
    // @formatter:on
    ProcessInstanceEngineDto processInstanceDto = engineRule.deployAndStartProcess(modelInstance);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ProcessCountReportMapResultDto result = evaluateFrequencyReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(4));
    final List<String> resultLabels = resultData.stream()
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toList());
    assertThat(
      resultLabels,
      // expect ascending order
      contains("Ac", "ax", "fooBar1", "foobar2")
    );
  }

  private ProcessInstanceEngineDto deployProcessWithTwoTasksAndLabels() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
        .name("start" + LABEL_SUFFIX)
      .serviceTask(TEST_ACTIVITY)
        .name(TEST_ACTIVITY + LABEL_SUFFIX)
        .camundaExpression("${true}")
      .serviceTask(TEST_ACTIVITY_2)
        .name(TEST_ACTIVITY_2 + LABEL_SUFFIX)
        .camundaExpression("${true}")
      .endEvent("end")
        .name(null)
      .done();
    // @formatter:on
    return engineRule.deployAndStartProcess(modelInstance);
  }

  private ProcessReportDataDto getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(ProcessInstanceEngineDto processDefinition) {
    return createFlowNodeDurationGroupByFlowNodeHeatmapReport(
      processDefinition.getProcessDefinitionKey(),
      String.valueOf(processDefinition.getProcessDefinitionVersion())
    );
  }

  private ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluateFrequencyReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>>() {});
      // @formatter:on
  }

  private ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluateDurationReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto>>() {});
      // @formatter:on
  }
}
