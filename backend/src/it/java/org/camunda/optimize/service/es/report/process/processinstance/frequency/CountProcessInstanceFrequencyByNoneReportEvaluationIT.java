/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.frequency;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class CountProcessInstanceFrequencyByNoneReportEvaluationIT extends AbstractProcessDefinitionIT {

  public static final String PROCESS_DEFINITION_KEY = "123";

  @Test
  public void reportEvaluationForOneProcess() {

    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    ProcessReportEvaluationResultDto<ProcessReportNumberResultDto> evaluationResponse = evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.NONE));

    final ProcessReportNumberResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getProcessInstanceCount(), is(1L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData(), is(1L));
  }

  @Test
  public void evaluateNumberReportForMultipleInstances() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineRule.startProcessInstance(engineDto.getDefinitionId());
    engineRule.startProcessInstance(engineDto.getDefinitionId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
      engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion()
    );
    ProcessReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(3L));
  }

  @Test
  public void reportAcrossAllVersions() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineRule.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
      engineDto.getProcessDefinitionKey(), ReportConstants.ALL_VERSIONS
    );
    ProcessReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(3L));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineRule.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
      engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion()
    );
    ProcessReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(2L));
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Lists.newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      Lists.newArrayList(null, tenantId1, tenantId2)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
      processKey, ReportConstants.ALL_VERSIONS
    );
    reportData.setTenantIds(selectedTenants);
    ProcessReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getData(), is((long) selectedTenants.size()));
  }

  @Test
  public void dateFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(past.minusSeconds(1L))
                           .add()
                           .buildList());
    ProcessReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData(), is(0L));

    // when
    reportData = ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(past).end(null).add().buildList());
    result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData(), is(1L));
  }

  @Test
  public void flowNodeFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
      processDefinition.getKey(), String.valueOf(processDefinition.getVersion())
    );
    List<ProcessFilterDto> flowNodeFilter = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id("task1")
      .add()
      .buildList();
    reportData.getFilter().addAll(flowNodeFilter);
    ProcessReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getProcessInstanceCount(), is(1L));
  }


  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto =
      ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto =
      ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void frequencyReportCanAlsoBeEvaluatedIfAggregationTypeIsDifferentFromDefault() {

    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setAggregationType(AggregationType.MAX);
    ProcessReportEvaluationResultDto<ProcessReportNumberResultDto> evaluationResponse =
      evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    final ProcessReportNumberResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getProcessInstanceCount(), is(1L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData(), is(1L));
  }

}
