/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.export.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;

public class ProcessReportExportIT extends AbstractIT {

  private static final String DEFINITION_KEY = "aKey";
  private static final String DEFINITION_VERSION = "1";

  @BeforeEach
  public void setUp() {
    // only superusers are authorized to export reports
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add("demo");
  }

  @ParameterizedTest
  @MethodSource("getTestReports")
  public void exportReportAsJsonFile(final ProcessReportDataDto reportData) {
    // given
    final String reportId = reportClient.createSingleProcessReport(reportData);
    final SingleProcessReportDefinitionExportDto expectedReportExportDto =
      new SingleProcessReportDefinitionExportDto(reportClient.getSingleProcessReportDefinitionDto(reportId));

    // when
    Response response = exportClient.exportReportAsJson(ReportType.PROCESS, reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    final SingleProcessReportDefinitionExportDto actualExportDto =
      response.readEntity(SingleProcessReportDefinitionExportDto.class);

    assertThat(actualExportDto).usingRecursiveComparison().isEqualTo(expectedReportExportDto);
  }

  private static Stream<ProcessReportDataDto> getTestReports() {
    // A raw report with some custom table column config
    final ProcessReportDataDto rawReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    rawReport.getConfiguration().getTableColumns().setIncludeNewVariables(false);
    rawReport.getConfiguration().getTableColumns().getExcludedColumns().add(ProcessInstanceDto.Fields.startDate);

    // A groupBy report with process part and custom sorting
    final ProcessReportDataDto durationWithPartReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setGroupByDateVariableUnit(AggregateByDateUnit.HOUR)
      .setStartFlowNodeId("someStartFlowNode")
      .setEndFlowNodeId("someEndFlowNode")
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_END_DATE_WITH_PART)
      .build();
    durationWithPartReport.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));

    // A distributedBy report with filters and custom bucket config
    final ProcessReportDataDto filteredDistrByReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setDistributeByDateInterval(AggregateByDateUnit.YEAR)
      .setGroupByDateVariableUnit(AggregateByDateUnit.DAY)
      .setVariableType(VariableType.INTEGER)
      .setVariableName("testVariable")
      .setFilter(
        new EndDateFilterDto(
          new RollingDateFilterDataDto(new RollingDateFilterStartDto(4L, DateFilterUnit.DAYS)))
      )
      .setVisualization(ProcessVisualization.BAR)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE_BY_START_DATE)
      .build();
    filteredDistrByReport.getConfiguration().getCustomBucket().setBucketSize(150.0);
    filteredDistrByReport.getConfiguration().getCustomBucket().setBaseline(55.0);
    filteredDistrByReport.getConfiguration().getCustomBucket().setActive(true);

    return Stream.of(
      rawReport,
      durationWithPartReport,
      filteredDistrByReport
    );
  }

}
