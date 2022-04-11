/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_CREATE_TIME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_DURATION_IN_MS;

@Slf4j
@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewIncidentDuration extends ProcessViewDuration {

  @Override
  protected String getReferenceDateFieldName(final ProcessReportDataDto reportData) {
    return INCIDENTS + "." + INCIDENT_CREATE_TIME;
  }

  @Override
  protected String getDurationFieldName(final ProcessReportDataDto definitionData) {
    return INCIDENTS + "." + INCIDENT_DURATION_IN_MS;
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.INCIDENT);
    view.setProperties(ViewProperty.DURATION);
    dataForCommandKey.setView(view);
  }
}
