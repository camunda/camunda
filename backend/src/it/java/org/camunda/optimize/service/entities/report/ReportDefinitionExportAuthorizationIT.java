/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.report;

import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
import org.camunda.optimize.util.SuperUserType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

/**
 * These are authIT for the export via UI with user authorization. For the public API, please refer to
 * PublicJsonExportRestServiceIT.
 */
public class ReportDefinitionExportAuthorizationIT extends AbstractExportImportEntityDefinitionIT {

  @ParameterizedTest
  @MethodSource("reportAndAuthType")
  public void exportReportAsJson_asSuperuser(final ReportType reportType, final SuperUserType superUserType) {
    // given
    final String reportId = createSimpleReport(reportType);

    // when
    final Response response;
    if (superUserType == SuperUserType.USER) {
      response = exportClient.exportReportAsJsonAsDemo(reportId, "my_file.json");
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
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);
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