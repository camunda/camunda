/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class KpiServiceIT extends AbstractPlatformIT {
//
//  private final String PROCESS_DEFINITION_KEY = "procDef";
//
//  @Test
//  public void getKpisForDefinition() {
//    // given
//    final String reportId1 = createKpiReport();
//    final String reportId2 = createKpiReport();
//
//    // when
//    final List<SingleProcessReportDefinitionRequestDto> reports =
//        embeddedOptimizeExtension
//            .getKpiService()
//            .getValidKpiReportsForProcessDefinition(PROCESS_DEFINITION_KEY);
//
//    // then
//    assertThat(reports).hasSize(2);
//    assertThat(reports)
//        .satisfiesOnlyOnce(report -> assertThat(report.getId()).isEqualTo(reportId1));
//    assertThat(reports)
//        .satisfiesOnlyOnce(report -> assertThat(report.getId()).isEqualTo(reportId2));
//  }
//
//  @Test
//  public void reportIsNotReturnedIfNotKpi() {
//    // given
//    final String kpiReportId = createKpiReport();
//    final ProcessReportDataDto reportDataDto =
//        TemplatedProcessReportDataBuilder.createReportData()
//            .setReportDataType(ProcessReportDataType.RAW_DATA)
//            .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
//            .build();
//    reportDataDto.getConfiguration().getTargetValue().setIsKpi(false);
//    reportClient.createSingleProcessReport(reportDataDto);
//
//    // when
//    final List<SingleProcessReportDefinitionRequestDto> reports =
//        embeddedOptimizeExtension
//            .getKpiService()
//            .getValidKpiReportsForProcessDefinition(PROCESS_DEFINITION_KEY);
//
//    // then
//    assertThat(reports)
//        .singleElement()
//        .satisfies(report -> assertThat(report.getId()).isEqualTo(kpiReportId));
//  }
//
//  @Test
//  public void reportIsNotReturnedIfInvalidKpi() {
//    // given
//    final String validReportId = createKpiReport();
//    final ProcessReportDataDto reportDataDto =
//        TemplatedProcessReportDataBuilder.createReportData()
//            .setReportDataType(ProcessReportDataType.RAW_DATA)
//            .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
//            .build();
//    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
//    // null view is invalid
//    reportDataDto.setView(null);
//    reportClient.createSingleProcessReport(reportDataDto);
//
//    // when
//    final List<SingleProcessReportDefinitionRequestDto> reports =
//        embeddedOptimizeExtension
//            .getKpiService()
//            .getValidKpiReportsForProcessDefinition(PROCESS_DEFINITION_KEY);
//
//    // then
//    assertThat(reports)
//        .singleElement()
//        .satisfies(report -> assertThat(report.getId()).isEqualTo(validReportId));
//  }
//
//  @Test
//  public void otherProcessDefinitionKpiReportIsNotReturned() {
//    // given
//    final String reportId = createKpiReport();
//    final ProcessReportDataDto reportDataDto =
//        TemplatedProcessReportDataBuilder.createReportData()
//            .setReportDataType(ProcessReportDataType.RAW_DATA)
//            .definitions(List.of(new ReportDataDefinitionDto("someProcessDefinition")))
//            .build();
//    reportDataDto.getConfiguration().getTargetValue().setIsKpi(false);
//    reportClient.createSingleProcessReport(reportDataDto);
//
//    // when
//    final List<SingleProcessReportDefinitionRequestDto> reports =
//        embeddedOptimizeExtension
//            .getKpiService()
//            .getValidKpiReportsForProcessDefinition(PROCESS_DEFINITION_KEY);
//
//    // then
//    assertThat(reports)
//        .singleElement()
//        .satisfies(report -> assertThat(report.getId()).isEqualTo(reportId));
//  }
//
//  private String createKpiReport() {
//    final ProcessReportDataDto reportDataDto =
//        TemplatedProcessReportDataBuilder.createReportData()
//            .setReportDataType(ProcessReportDataType.RAW_DATA)
//            .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
//            .build();
//    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
//    return reportClient.createSingleProcessReport(reportDataDto);
//  }
// }
