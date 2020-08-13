/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.date.distributed_by.none;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.flownode.ModelElementFrequencyByModelElementDateReportEvaluationIT;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public abstract class FlowNodeFrequencyByFlowNodeDateReportEvaluationIT
  extends ModelElementFrequencyByModelElementDateReportEvaluationIT {

  @Override
  protected void startInstancesWithDayRangeForDefinition(ProcessDefinitionEngineDto processDefinition,
                                                         ZonedDateTime min,
                                                         ZonedDateTime max) {
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(instance, START_EVENT, min.toOffsetDateTime());
    changeModelElementDate(instance, END_EVENT, max.toOffsetDateTime());
  }

  @Override
  protected ProcessDefinitionEngineDto deployTwoModelElementDefinition() {
    return deployStartEndDefinition();
  }

  @Override
  protected ProcessDefinitionEngineDto deploySimpleModelElementDefinition() {
    return deployStartEndDefinition();
  }

  @Override
  protected ProcessInstanceEngineDto startAndCompleteInstance(String definitionId) {
    return engineIntegrationExtension.startProcessInstance(definitionId);
  }

  @Override
  protected ProcessInstanceEngineDto startAndCompleteInstanceWithDates(String definitionId,
                                                                       OffsetDateTime firstElementDate,
                                                                       OffsetDateTime secondElementDate) {
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(definitionId);
    changeModelElementDate(processInstanceDto, START_EVENT, firstElementDate);
    changeModelElementDate(processInstanceDto, END_EVENT, secondElementDate);
    return processInstanceDto;
  }

  @Override
  protected ProcessViewEntity getExpectedViewEntity() {
    return ProcessViewEntity.FLOW_NODE;
  }

}
