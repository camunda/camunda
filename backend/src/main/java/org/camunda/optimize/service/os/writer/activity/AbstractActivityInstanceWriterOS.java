/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.writer.activity.AbstractActivityInstanceWriter;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.os.writer.AbstractProcessInstanceDataWriterOS;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Conditional(OpenSearchCondition.class)
public abstract class AbstractActivityInstanceWriterOS extends AbstractProcessInstanceDataWriterOS<FlowNodeEventDto> implements AbstractActivityInstanceWriter {

  private final ObjectMapper objectMapper;

  protected AbstractActivityInstanceWriterOS(final OptimizeOpenSearchClient osClient,
                                             final OpenSearchSchemaManager openSearchSchemaManager,
                                             final ObjectMapper objectMapper) {
    super(osClient, openSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ImportRequestDto> generateActivityInstanceImports(List<FlowNodeEventDto> activityInstances) {
    //todo will be handled in the OPT-7376
   return new ArrayList<>();
  }

  @Override
  public FlowNodeInstanceDto fromActivityInstance(final FlowNodeEventDto activityInstance) {
    return new FlowNodeInstanceDto(
      activityInstance.getProcessDefinitionKey(),
      activityInstance.getProcessDefinitionVersion(),
      activityInstance.getTenantId(),
      activityInstance.getEngineAlias(),
      activityInstance.getProcessInstanceId(),
      activityInstance.getActivityId(),
      activityInstance.getActivityType(),
      activityInstance.getId(),
      activityInstance.getTaskId()
    )
      .setTotalDurationInMs(activityInstance.getDurationInMs())
      .setStartDate(activityInstance.getStartDate())
      .setEndDate(activityInstance.getEndDate())
      .setCanceled(activityInstance.getCanceled());
  }

  protected abstract String createInlineUpdateScript();

}
