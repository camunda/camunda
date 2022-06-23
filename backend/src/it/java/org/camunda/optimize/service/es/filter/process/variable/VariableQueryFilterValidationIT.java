/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.service.es.filter.process.AbstractFilterIT;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class VariableQueryFilterValidationIT extends AbstractFilterIT {

  @Test
  public void validationExceptionOnNullValueField() {
    // given
    List<ProcessFilterDto<?>> variableFilterDto = ProcessFilterBuilder.filter()
      .variable()
      .booleanType()
      .values(null)
      .name("foo")
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void validationExceptionOnNullNumericValuesField() {
    // given
    List<ProcessFilterDto<?>> variableFilterDto = ProcessFilterBuilder
      .filter()
      .variable()
      .longType()
      .operator(FilterOperator.IN)
      .values(null)
      .name("foo")
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void validationExceptionOnNullNameField() {
    // given
    List<ProcessFilterDto<?>> variableFilterDto = ProcessFilterBuilder.filter()
      .variable()
      .booleanTrue()
      .name(null)
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private Response evaluateReportWithFilterAndGetResponse(List<ProcessFilterDto<?>> filterList) {
    final String TEST_DEFINITION_KEY = "testDefinition";
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(TEST_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(filterList);
    return reportClient.evaluateReportAndReturnResponse(reportData);
  }

}
