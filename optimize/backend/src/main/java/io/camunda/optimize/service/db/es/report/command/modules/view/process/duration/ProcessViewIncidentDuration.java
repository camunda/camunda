/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.process.duration;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_CREATE_TIME;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENT_DURATION_IN_MS;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewIncidentDuration extends ProcessViewDuration {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessViewIncidentDuration.class);

  public ProcessViewIncidentDuration() {}

  @Override
  protected String getReferenceDateFieldName(final ProcessReportDataDto reportData) {
    return INCIDENTS + "." + INCIDENT_CREATE_TIME;
  }

  @Override
  protected String getDurationFieldName(final ProcessReportDataDto definitionData) {
    return INCIDENTS + "." + INCIDENT_DURATION_IN_MS;
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    final ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.INCIDENT);
    view.setProperties(ViewProperty.DURATION);
    dataForCommandKey.setView(view);
  }
}
