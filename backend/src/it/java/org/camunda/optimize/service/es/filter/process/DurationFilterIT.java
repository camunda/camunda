/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;

public class DurationFilterIT extends AbstractDurationFilterIT {

  @Test
  public void testGetReportWithMixedDurationCriteria() {
    // given
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    List<ProcessFilterDto<?>> gte = ProcessFilterBuilder
      .filter()
      .duration()
      .unit(DurationUnit.SECONDS)
      .value((long) 2)
      .operator(GREATER_THAN_EQUALS)
      .add()
      .buildList();
    List<ProcessFilterDto<?>> lt = ProcessFilterBuilder
      .filter()
      .duration()
      .unit(DurationUnit.DAYS)
      .value((long) 1)
      .operator(LESS_THAN)
      .add()
      .buildList();
    gte.addAll(lt);
    reportData.setFilter(gte);
    AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> result =
      reportClient.evaluateRawReport(
        reportData);

    // then
    assertResult(processInstance, result);
  }

  @Test
  public void testValidationExceptionOnNullFilterField() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(ProcessFilterBuilder
                           .filter()
                           .duration()
                           .unit(null)
                           .value((long) 2)
                           .operator(GREATER_THAN_EQUALS)
                           .add()
                           .buildList());


    assertThat(reportClient.evaluateReportAndReturnResponse(reportData).getStatus())
      .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

}
