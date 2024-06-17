/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.process.duration;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Primary
public class ProcessViewInstanceDuration extends ProcessViewDuration {

  @Override
  protected String getReferenceDateFieldName(final ProcessReportDataDto reportData) {
    return ProcessInstanceIndex.START_DATE;
  }

  @Override
  protected String getDurationFieldName(final ProcessReportDataDto definitionData) {
    return ProcessInstanceIndex.DURATION;
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    view.setProperties(ViewProperty.DURATION);
    dataForCommandKey.setView(view);
  }
}
