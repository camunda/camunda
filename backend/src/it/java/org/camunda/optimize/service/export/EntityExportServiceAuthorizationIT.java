/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class EntityExportServiceAuthorizationIT extends AbstractIT {

  private static Stream<ReportType> reportTypes() {
    return Stream.of(ReportType.PROCESS, ReportType.DECISION);
  }

  @ParameterizedTest
  @MethodSource("reportTypes")
  public void exportReportAsJson_asSuperuser(final ReportType reportType) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add("demo");
    final String reportId = createSimpleReport(reportType);

    // when
    final Response response = exportClient.exportReportAsJson(reportType, reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportTypes")
  public void exportReportAsJson_asNonSuperuser(final ReportType reportType) {
    // given
    final String reportId = createSimpleReport(reportType);

    // when
    final Response response = exportClient.exportReportAsJson(reportType, reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportTypes")
  public void exportReportAsJson_asSuperuser_withoutDefinitionAuth(final ReportType reportType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);
    final String reportId = createSimpleReport(reportType);

    // when
    final Response response = exportClient.exportReportAsJsonAsUser(
      KERMIT_USER,
      KERMIT_USER,
      reportType,
      reportId,
      "my_file.json"
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private String createSimpleReport(final ReportType reportType) {
    switch (reportType) {
      case PROCESS:
        final ProcessReportDataDto processReportData = TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey("aKey")
          .setProcessDefinitionVersion("1")
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        return reportClient.createSingleProcessReport(processReportData);
      case DECISION:
        final DecisionReportDataDto decisionReportData = new DecisionReportDataDto();
        decisionReportData.setDecisionDefinitionKey("aKey");
        decisionReportData.setDecisionDefinitionVersion("1");
        return reportClient.createSingleDecisionReport(decisionReportData);
      default:
        throw new OptimizeIntegrationTestException("Unknown report type: " + reportType);
    }
  }

}