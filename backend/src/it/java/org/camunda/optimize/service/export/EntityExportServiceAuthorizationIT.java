/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class EntityExportServiceAuthorizationIT extends AbstractIT {

  @Test
  public void exportReportAsJson_asSuperuser() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add("demo");
    final String reportId = createSimpleProcessReport();

    // when
    final Response response = exportClient.exportReportAsJson(ReportType.PROCESS, reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void exportReportAsJson_asNonSuperuser() {
    // given
    final String reportId = createSimpleProcessReport();

    // when
    final Response response = exportClient.exportReportAsJson(ReportType.PROCESS, reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void exportReportAsJson_asSuperuser_withoutDefinitionAuth() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);
    final String reportId = createSimpleProcessReport();

    // when
    final Response response = exportClient.exportReportAsJsonAsUser(
      KERMIT_USER,
      KERMIT_USER,
      ReportType.PROCESS,
      reportId,
      "my_file.json"
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private String createSimpleProcessReport() {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("aKey")
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    return reportClient.createSingleProcessReport(reportData);
  }

}