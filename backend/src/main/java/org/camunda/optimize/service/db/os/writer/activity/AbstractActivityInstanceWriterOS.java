/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer.activity;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.writer.activity.AbstractActivityInstanceWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
@Slf4j
public abstract class AbstractActivityInstanceWriterOS implements AbstractActivityInstanceWriter {

  @Override
  public List<ImportRequestDto> generateActivityInstanceImports(
      List<FlowNodeEventDto> activityInstances) {
    log.error("Functionality not implemented for OpenSearch");
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
            activityInstance.getTaskId())
        .setTotalDurationInMs(activityInstance.getDurationInMs())
        .setStartDate(activityInstance.getStartDate())
        .setEndDate(activityInstance.getEndDate())
        .setCanceled(activityInstance.getCanceled());
  }

  protected abstract String createInlineUpdateScript();
}
