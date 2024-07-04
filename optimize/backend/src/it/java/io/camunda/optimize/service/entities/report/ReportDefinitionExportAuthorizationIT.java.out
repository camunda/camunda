/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.entities.report;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.ReportType;
// import io.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
// import io.camunda.optimize.util.SuperUserType;
// import jakarta.ws.rs.core.Response;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// /**
//  * These are authIT for the export via UI with user authorization. For the public API, please
// refer
//  * to PublicJsonExportRestServiceIT.
//  */
// @Tag(OPENSEARCH_PASSING)
// public class ReportDefinitionExportAuthorizationIT extends AbstractExportImportEntityDefinitionIT
// {
//
//   @ParameterizedTest
//   @MethodSource("reportAndAuthType")
//   public void exportReportAsJson_asSuperuser(
//       final ReportType reportType, final SuperUserType superUserType) {
//     // given
//     final String reportId = createSimpleReport(reportType);
//
//     // when
//     final Response response;
//     if (superUserType == SuperUserType.USER) {
//       response = exportClient.exportReportAsJsonAsDemo(reportId, "my_file.json");
//     } else {
//       setAuthorizedSuperGroup();
//       response =
//           exportClient.exportReportAsJsonAsUser(KERMIT_USER, KERMIT_USER, reportId,
// "my_file.json");
//     }
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("reportTypes")
//   public void exportReportAsJson_asNonSuperuser(final ReportType reportType) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     final String reportId = createSimpleReport(reportType);
//
//     // when
//     final Response response =
//         exportClient.exportReportAsJsonAsUser(KERMIT_USER, KERMIT_USER, reportId,
// "my_file.json");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("reportTypes")
//   public void exportReportAsJson_asSuperuser_withoutDefinitionAuth(final ReportType reportType) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getAuthConfiguration()
//         .getSuperUserIds()
//         .add(KERMIT_USER);
//     final String reportId = createSimpleReport(reportType);
//
//     // when
//     final Response response =
//         exportClient.exportReportAsJsonAsUser(KERMIT_USER, KERMIT_USER, reportId,
// "my_file.json");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
// }
