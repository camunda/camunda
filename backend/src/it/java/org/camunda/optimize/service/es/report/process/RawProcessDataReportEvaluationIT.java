/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;


public class RawProcessDataReportEvaluationIT {

  private static final String BUSINESS_KEY = "aBusinessKey";


  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  @Test
  public void reportAcrossAllVersions() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), ReportConstants.ALL_VERSIONS);
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(result.getProcessInstanceCount(), is(2L));
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(ReportConstants.ALL_VERSIONS));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);

    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getBusinessKey(), is(BUSINESS_KEY));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationForOneProcessInstance() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);

    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationByIdForOneProcessInstance() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String reportId = createAndStoreDefaultReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );

    // when
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      evaluateReportById(reportId);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationWithSeveralProcessInstances() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    ProcessInstanceEngineDto processInstance2 = engineRule.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(result.getIsComplete(), is(true));
    Set<String> expectedProcessInstanceIds = new HashSet<>();
    expectedProcessInstanceIds.add(processInstance.getId());
    expectedProcessInstanceIds.add(processInstance2.getId());
    for (RawDataProcessInstanceDto rawDataProcessInstanceDto : result.getData()) {
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
      String actualProcessInstanceId = rawDataProcessInstanceDto.getProcessInstanceId();
      assertThat(expectedProcessInstanceIds.contains(actualProcessInstanceId), is(true));
      expectedProcessInstanceIds.remove(actualProcessInstanceId);
    }
  }

  @Test
  public void reportEvaluationOnProcessInstanceWithAllVariableTypes() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "Hello World!");
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 2);
    variables.put("longVar", "Hello World!");
    variables.put("dateVar", new Date());

    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
    rawDataProcessInstanceDto.getVariables().
      forEach((varName, varValue) -> {
                assertThat(variables.keySet().contains(varName), is(true));
                assertThat(variables.get(varName), is(notNullValue()));
              }
      );
  }

  @Test
  public void resultShouldBeOrderAccordingToStartDate() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processInstance.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(2));
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstance.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(rawDataList, isInDescendingOrdering());
  }

  private Matcher<? super List<RawDataProcessInstanceDto>> isInDescendingOrdering() {
    return new TypeSafeMatcher<List<RawDataProcessInstanceDto>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("The given list should be sorted in ascending order!");
      }

      @Override
      protected boolean matchesSafely(List<RawDataProcessInstanceDto> items) {
        for (int i = (items.size() - 1); i > 0; i--) {
          if (items.get(i).getStartDate().isAfter(items.get(i - 1).getStartDate())) {
            return false;
          }
        }
        return true;
      }
    };
  }

  @Test
  public void testCustomOrderOnProcessInstancePropertyIsApplied() {
    // given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleProcess();

    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());

    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    final Object[] processInstanceIdsOrderedAsc = Lists.newArrayList(
      processInstanceDto1.getId(), processInstanceDto2.getId(), processInstanceDto3.getId()
    ).stream().sorted(Collections.reverseOrder()).toArray();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstanceDto1.getProcessDefinitionKey(), processInstanceDto1.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto(ProcessInstanceType.PROCESS_INSTANCE_ID, SortOrder.DESC));
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(
      rawDataList.stream().map(RawDataProcessInstanceDto::getProcessInstanceId).collect(Collectors.toList()),
      contains(processInstanceIdsOrderedAsc)
    );
  }

  @Test
  public void testInvalidSortPropertyNameSilentlyIgnored() {
    // given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstanceDto1.getProcessDefinitionKey(), processInstanceDto1.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto("lalalala", SortOrder.ASC));
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(rawDataList.size(), is(1));
  }

  @Test
  public void testCustomOrderOnProcessInstanceVariableIsApplied() {
    // given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleProcessWithVariables(
      toImmutableMap(new SimpleEntry<>("intVar", 0))
    );

    ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(
      processInstanceDto1.getDefinitionId(), toImmutableMap(new SimpleEntry<>("intVar", 2))
    );

    ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(
      processInstanceDto1.getDefinitionId(), toImmutableMap(new SimpleEntry<>("intVar", 1))
    );

    ProcessInstanceEngineDto processInstanceDto4 = engineRule.startProcessInstance(
      processInstanceDto1.getDefinitionId(), toImmutableMap(new SimpleEntry<>("otherVar", 0))
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    final Object[] processInstanceIdsOrderedAsc = Lists.newArrayList(
      processInstanceDto1.getId(), processInstanceDto2.getId(), processInstanceDto3.getId(), processInstanceDto4.getId()
    ).toArray();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstanceDto1.getProcessDefinitionKey(), processInstanceDto1.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto("variable:intVar", SortOrder.ASC));
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(
      rawDataList.stream().map(RawDataProcessInstanceDto::getProcessInstanceId).collect(Collectors.toList()),
      contains(processInstanceIdsOrderedAsc)
    );
  }

  @Test
  public void testInvalidSortVariableNameSilentlyIgnored() {
    // given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstanceDto1.getProcessDefinitionKey(), processInstanceDto1.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto("variable:lalalala", SortOrder.ASC));
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getData();
    assertThat(rawDataList.size(), is(1));
  }

  @Test
  public void variablesOfOneProcessInstanceAreAddedToOther() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("varName1", "value1");

    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    variables.clear();
    variables.put("varName2", "value2");
    engineRule.startProcessInstance(processInstance.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    result.getData().forEach(
      rawDataProcessInstanceDto1 -> {
        Map<String, Object> vars = rawDataProcessInstanceDto1.getVariables();
        assertThat(vars.keySet().size(), is(2));
        assertThat(vars.values().contains(""), is(true));
        // ensure is ordered
        List<String> actual = new ArrayList<>(vars.keySet());
        List<String> expected = new ArrayList<>(vars.keySet());
        Collections.sort(expected);
        assertThat(actual, contains(expected.toArray()));
      }
    );
  }

  @Test
  public void evaluationReturnsOnlyDataToGivenProcessDefinitionId() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
  }


  //test that basic support for filter is there
  @Test
  public void durationFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder
                           .filter()
                           .duration()
                           .unit("Days")
                           .value((long) 1)
                           .operator(">")
                           .add()
                           .buildList());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult1 =
      evaluateReport(reportData);
    final RawDataProcessReportResultDto result1 = evaluationResult1.getResult();

    // then
    final ProcessReportDataDto resultDataDto1 = evaluationResult1.getReportDefinition().getData();
    assertThat(resultDataDto1.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto1.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto1.getView(), is(notNullValue()));
    assertThat(resultDataDto1.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result1.getData(), is(notNullValue()));
    assertThat(result1.getData().size(), is(0));

    // when
    reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder
                           .filter()
                           .duration()
                           .unit("Days")
                           .value((long) 1)
                           .operator("<")
                           .add()
                           .buildList());

    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult2 =
      evaluateReport(reportData);
    final RawDataProcessReportResultDto result2 = evaluationResult2.getResult();

    // then
    final ProcessReportDataDto resultDataDto2 = evaluationResult2.getReportDefinition().getData();
    assertThat(resultDataDto2.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto2.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto2.getView(), is(notNullValue()));
    assertThat(resultDataDto2.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result2.getData(), is(notNullValue()));
    assertThat(result2.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result2.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void dateFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(past.minusSeconds(1L))
                           .add()
                           .buildList());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult1 =
      evaluateReport(reportData);
    final RawDataProcessReportResultDto result1 = evaluationResult1.getResult();

    // then
    final ProcessReportDataDto resultDataDto1 = evaluationResult1.getReportDefinition().getData();
    assertThat(resultDataDto1.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto1.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto1.getView(), is(notNullValue()));
    assertThat(resultDataDto1.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result1.getData(), is(notNullValue()));
    assertThat(result1.getData().size(), is(0));

    // when
    reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(past).end(null).add().buildList());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult2 =
      evaluateReport(reportData);
    final RawDataProcessReportResultDto result2 = evaluationResult2.getResult();

    // then
    final ProcessReportDataDto resultDataDto2 = evaluationResult2.getReportDefinition().getData();
    assertThat(resultDataDto2.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto2.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto2.getView(), is(notNullValue()));
    assertThat(resultDataDto2.getView().getProperty(), is(ProcessViewProperty.RAW_DATA));
    assertThat(result2.getData(), is(notNullValue()));
    assertThat(result2.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result2.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void variableFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    engineRule.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(createVariableFilter());
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  private List<ProcessFilterDto> createVariableFilter() {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto("true");
    data.setName("var");

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    return Collections.singletonList(variableFilterDto);
  }

  @Test
  public void flowNodeFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    List<ProcessFilterDto> flowNodeFilter = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id("task1")
      .add()
      .buildList();

    reportData.getFilter().addAll(flowNodeFilter);
    final ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult = evaluateReport(reportData);
    final RawDataProcessReportResultDto result = evaluationResult.getResult();

    // then
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(String.valueOf(processDefinition.getVersion())));
    assertThat(result.getData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void testValidationExceptionOnNullDto() {
    //when
    Response response = evaluateReportAndReturnResponse(null);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void missingProcessDefinition() {

    //when
    ProcessReportDataDto dataDto = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(null, null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingViewField() {
    //when
    ProcessReportDataDto dataDto = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(null, null);
    dataDto.setView(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingPropertyField() {
    //when
    ProcessReportDataDto dataDto = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(null, null);
    dataDto.getView().setProperty(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingVisualizationField() {
    //when
    ProcessReportDataDto dataDto = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(null, null);
    dataDto.setVisualization(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    String id = createNewReport();
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
      processDefinitionKey,
      processDefinitionVersion
    );
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  @SafeVarargs
  private final Map<String, Object> toImmutableMap(SimpleEntry<String, Object>... Entry) {
    return Collections.unmodifiableMap(
      Stream.of(Entry).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue))
    );
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables, BUSINESS_KEY);
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway("splittingGateway")
      .name("Should we go to task 1?")
      .condition("yes", "${goToTask1}")
      .serviceTask("task1")
      .camundaExpression("${true}")
      .exclusiveGateway("mergeGateway")
      .endEvent("endEvent")
      .moveToNode("splittingGateway")
      .condition("no", "${!goToTask1}")
      .serviceTask("task2")
      .camundaExpression("${true}")
      .connectTo("mergeGateway")
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReportById(final String reportId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      .execute(new TypeReference<ProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {
      });
  }

  private ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReport(final ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  private String createNewReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();
    assertThat(response.getStatus(), is(204));
  }
}
