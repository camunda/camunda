/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MixedFilterIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  private final static String USER_TASK_ACTIVITY_ID = "userTask";

  @Test
  public void applyAllPossibleFilters() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");

      // this is the process instance that should be filtered
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    final String expectedInstanceId = instanceEngineDto.getId();
    engineIntegrationExtensionRule.finishAllRunningUserTasks(expectedInstanceId);

      // wrong not executed flow node
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);

    // wrong variable
    variables.put("var", "anotherValue");
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());

    // wrong date
    Thread.sleep(1000L);
    variables.put("var", "value");
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    OffsetDateTime start = engineIntegrationExtensionRule.getHistoricProcessInstance(instanceEngineDto.getId()).getStartTime();
    OffsetDateTime end = engineIntegrationExtensionRule.getHistoricProcessInstance(instanceEngineDto.getId()).getEndTime();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filterList = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(USER_TASK_ACTIVITY_ID)
      .add()
      .variable()
      .stringType()
      .values(Collections.singletonList("value"))
      .name("var")
      .operator(IN)
      .add()
      .fixedStartDate()
      .start(null)
      .end(start.minusSeconds(1L))
      .add()
      .fixedEndDate()
      .start(null)
      .end(end.minusSeconds(1L))
      .add()
      .buildList();

    RawDataProcessReportResultDto rawDataReportResultDto = evaluateReportWithFilter(processDefinition, filterList);

    // then
    assertThat(rawDataReportResultDto.getData().size(), is(1));
    assertThat(rawDataReportResultDto.getData().get(0).getProcessInstanceId(), is(expectedInstanceId));
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition, List<ProcessFilterDto> filter) {
    ProcessReportDataDto reportData =
      createReport(processDefinition);
    reportData.setFilter(filter);
    return evaluateReport(reportData).getResult();
  }

  private ProcessReportDataDto createReport(ProcessDefinitionEngineDto processDefinition) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(processDefinition.getVersionAsString())
      .setReportDataType(RAW_DATA)
      .setFilter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
      .build();
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReport(final ProcessReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .userTask(USER_TASK_ACTIVITY_ID)
      .endEvent()
      .done();
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

}
