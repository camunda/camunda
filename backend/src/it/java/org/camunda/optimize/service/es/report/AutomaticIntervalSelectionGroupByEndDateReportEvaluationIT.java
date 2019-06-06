/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByEndDate;

public class AutomaticIntervalSelectionGroupByEndDateReportEvaluationIT
  extends AbstractAutomaticIntervalSelectionGroupByDateReportEvaluationIT {

  @Override
  protected ProcessReportDataDto getReportData(String key, String version) {
    return createCountProcessInstanceFrequencyGroupByEndDate(key, version, GroupByDateUnit.AUTOMATIC);
  }

  @Override
  protected void updateProcessInstanceDates(final Map<String, OffsetDateTime> updates) throws SQLException {
    engineDatabaseRule.updateProcessInstanceEndDates(updates);
  }

  @Override
  protected void updateProcessInstanceDate(final ZonedDateTime min, final ProcessInstanceEngineDto procInstMin) throws
                                                                                                                SQLException {
    engineDatabaseRule.changeProcessInstanceEndDate(procInstMin.getId(), min.toOffsetDateTime());
  }
}
