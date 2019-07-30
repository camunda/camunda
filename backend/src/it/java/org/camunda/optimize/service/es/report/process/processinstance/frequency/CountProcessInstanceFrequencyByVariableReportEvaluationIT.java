/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.frequency;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessCountReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByVariable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;


public class CountProcessInstanceFrequencyByVariableReportEvaluationIT extends AbstractProcessDefinitionIT {

  @Test
  public void simpleReportEvaluation() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateCountMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is(VariableType.STRING));

    final ProcessCountReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey("bar").get().getValue(), is(1L));
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String reportId = createAndStoreDefaultReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );

    // when
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateCountMapReportById(
      reportId
    );

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is(VariableType.STRING));

    final ProcessCountReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey("bar").get().getValue(), is(1L));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    deployAndStartSimpleServiceTaskProcess(variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateCountMapReport(
      reportData);

    // then
    final ProcessCountReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey("bar").get().getValue(), is(1L));
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(processKey, ALL_VERSIONS, DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_TYPE);
    reportData.setTenantIds(selectedTenants);
    ProcessCountReportMapResultDto result = evaluateCountMapReport(reportData).getResult();

    // then
    assertThat(result.getProcessInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void multipleProcessInstances() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateCountMapReport(
      reportData);

    // then
    final ProcessCountReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete(), is(true));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(2));
    assertThat(resultDto.getDataEntryForKey("bar1").get().getValue(), is(1L));
    assertThat(resultDto.getDataEntryForKey("bar2").get().getValue(), is(2L));
  }

  @Test
  public void multipleBuckets_resultLimitedByConfig() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateCountMapReport(
      reportData);

    // then
    final ProcessCountReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getProcessInstanceCount(), is(3L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(1));
    assertThat(resultDto.getIsComplete(), is(false));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    variables.put("foo", "bar3");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ProcessCountReportMapResultDto result = evaluateCountMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    variables.put("foo", "bar3");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ProcessCountReportMapResultDto result = evaluateCountMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    final List<Long> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(
      bucketValues,
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void variableTypeIsImportant() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", 1);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateCountMapReport(
      reportData);

    // then
    final ProcessCountReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey("1").get().getValue(), is(1L));
  }


  @Test
  public void otherVariablesDoNotDistortTheResult() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo1", "bar1");
    variables.put("foo2", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo1",
      VariableType.STRING
    );
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateCountMapReport(
      reportData);

    // then
    final ProcessCountReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey("bar1").get().getValue(), is(2L));
  }

  @Test
  public void worksWithAllVariableTypes() {
    // given
    Map<String, VariableType> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());
      ProcessReportDataDto reportData = createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        entry.getKey(),
        variableType
      );
      ProcessCountReportMapResultDto result = evaluateCountMapReport(reportData).getResult();

      // then
      assertThat(result.getData(), is(notNullValue()));
      List<MapResultEntryDto<Long>> resultData = result.getData();
      assertThat(resultData.size(), is(1));
      if (VariableType.DATE.equals(variableType)) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(entry.getKey());
        String dateAsString = embeddedOptimizeRule.getDateTimeFormatter()
          .format(temporal.atZoneSimilarLocal(ZoneId.systemDefault()));
        assertThat(resultData.get(0).getKey(), is(dateAsString));
        assertThat(resultData.get(0).getValue(), is(1L));
      } else {
        assertThat(resultData.get(0).getKey(), is(entry.getValue().toString()));
        assertThat(resultData.get(0).getValue(), is(1L));
      }
    }
  }

  private Map<String, VariableType> createVarNameToTypeMap() {
    Map<String, VariableType> varToType = new HashMap<>();
    varToType.put("dateVar", VariableType.DATE);
    varToType.put("boolVar", VariableType.BOOLEAN);
    varToType.put("shortVar", VariableType.SHORT);
    varToType.put("intVar", VariableType.INTEGER);
    varToType.put("longVar", VariableType.LONG);
    varToType.put("doubleVar", VariableType.DOUBLE);
    varToType.put("stringVar", VariableType.STRING);
    return varToType;
  }


  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("testVar", "withValue");

    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put("testVar", null);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    engineRule.startProcessInstance(processInstanceDto.getDefinitionId());

    variables = new HashMap<>();
    variables.put("differentStringValue", "test");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "testVar",
      VariableType.STRING
    );
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateCountMapReport(
      reportData);

    // then
    final ProcessCountReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(result.getDataEntryForKey("withValue").get().getValue(), is(1L));
    assertThat(result.getDataEntryForKey("missing").get().getValue(), is(3L));
  }



  @Test
  public void dateVariablesAreSortedDescByDefault() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());

    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables = Collections.singletonMap("dateVar", OffsetDateTime.now().minusDays(2));
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    variables = Collections.singletonMap("dateVar", OffsetDateTime.now().minusDays(1));
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
      processInstanceDto.getProcessDefinitionKey(),
      Collections.singletonList(processInstanceDto.getProcessDefinitionVersion()),
      "dateVar",
      VariableType.DATE
    );
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> response = evaluateCountMapReport(
      reportData);

    // then
    final List<MapResultEntryDto<Long>> resultData = response.getResult().getData();
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }


  @Test
  public void dateFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(past.minusSeconds(1L))
                           .add()
                           .buildList());
    ProcessCountReportMapResultDto result = evaluateCountMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(0));

    // when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(past).end(null).add().buildList());
    result = evaluateCountMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey("bar").get().getValue(), is(1L));
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void optimizeExceptionOnGroupByValueNameIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setName(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByValueTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(Map<String, Object> variables) {
    return deployAndStartSimpleProcesses(1, variables).get(0);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int number, Map<String, Object> variables) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, number)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineRule.startProcessInstance(processDefinition.getId(), variables);
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey,
                                                       String processDefinitionVersion,
                                                       String variableName,
                                                       VariableType variableType) {
    String id = createNewReport();
    ProcessReportDataDto reportData = createReport(
      processDefinitionKey,
      processDefinitionVersion,
      variableName,
      variableType
    );
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId(id);
    report.setLastModifier("something");
    report.setName("something");
    report.setCreated(OffsetDateTime.now());
    report.setLastModified(OffsetDateTime.now());
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private ProcessReportDataDto createReport(String processDefinitionKey,
                                            String processDefinitionVersion,
                                            String variableName,
                                            VariableType variableType) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setVariableName(variableName)
      .setVariableType(variableType)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .build();
  }

}
