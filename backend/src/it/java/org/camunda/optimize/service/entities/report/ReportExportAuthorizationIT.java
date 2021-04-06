/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.report;

import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.service.entities.AbstractExportImportIT;
import org.camunda.optimize.util.SuperUserType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class ReportExportAuthorizationIT extends AbstractExportImportIT {

  @ParameterizedTest
  @MethodSource("reportAndAuthType")
  public void exportReportAsJson_asSuperuser(final ReportType reportType, final SuperUserType superUserType) {
    // given
    final String reportId = createSimpleReport(reportType);

    // when
    final Response response;
    if (superUserType == SuperUserType.USER) {
      response = exportClient.exportReportAsJson(reportId, "my_file.json");
    } else {
      setAuthorizedSuperGroup();
      response = exportClient.exportReportAsJsonAsUser(
        KERMIT_USER,
        KERMIT_USER,
        reportId,
        "my_file.json"
      );
    }

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportTypes")
  public void exportReportAsJson_asNonSuperuser(final ReportType reportType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String reportId = createSimpleReport(reportType);

    // when
    final Response response = exportClient.exportReportAsJsonAsUser(
      KERMIT_USER,
      KERMIT_USER,
      reportId,
      "my_file.json"
    );

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
      reportId,
      "my_file.json"
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

}