/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.duration;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Primary
public class ProcessViewFlowNodeDuration extends ProcessViewDuration {

  @Override
  protected String getReferenceDateFieldName(final ProcessReportDataDto reportData) {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE;
  }

  @Override
  protected String getDurationFieldName(final ProcessReportDataDto definitionData) {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION;
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperties(ViewProperty.DURATION);
    dataForCommandKey.setView(view);
  }
}
