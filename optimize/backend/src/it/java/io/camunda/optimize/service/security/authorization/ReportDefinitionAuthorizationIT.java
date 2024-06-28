/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.security.authorization;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
// import static
// io.camunda.optimize.service.util.ProcessReportDataBuilderHelper.createCombinedReportData;
// import static
// io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
// import static
// io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
// import static io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
// import static io.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
// import static io.camunda.optimize.test.optimize.CollectionClient.PRIVATE_COLLECTION_ID;
// import static io.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
// import static io.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.DefinitionType;
// import io.camunda.optimize.dto.optimize.IdentityDto;
// import io.camunda.optimize.dto.optimize.IdentityType;
// import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import jakarta.ws.rs.core.Response;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class ReportDefinitionAuthorizationIT extends AbstractPlatformIT {
//
//   private static final String PROCESS_KEY = "aprocess";
//   private static final String DECISION_KEY = "aDecision";
//
//   private static Stream<Integer> definitionType() {
//     return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void evaluateUnauthorizedStoredReport(final int definitionResourceType) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(definitionResourceType);
//
//     final String reportId = createReportForDefinition(definitionResourceType);
//
//     // when
//     final Response response =
//         reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void evaluateUnauthorizedTenantsStoredReport(final int definitionResourceType) {
//     // given
//     final String tenantId = "tenant1";
//     engineIntegrationExtension.createTenant(tenantId);
//
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
//     deployStartAndImportDefinition(definitionResourceType);
//
//     final String reportId =
//         createReportForDefinition(definitionResourceType, Arrays.asList(tenantId));
//
//     // when
//     final Response response =
//         reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void evaluatePartiallyUnauthorizedTenantsStoredReport(final int definitionResourceType)
// {
//     // given
//     final String tenantId1 = "tenant1";
//     engineIntegrationExtension.createTenant(tenantId1);
//     final String tenantId2 = "tenant2";
//     engineIntegrationExtension.createTenant(tenantId2);
//
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
//     authorizationClient.grantSingleResourceAuthorizationsForUser(
//         KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
//     deployStartAndImportDefinition(definitionResourceType);
//
//     final String reportId =
//         createReportForDefinition(definitionResourceType, Arrays.asList(tenantId1, tenantId2));
//
//     // when
//     final Response response =
//         reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void evaluateAllTenantsAuthorizedStoredReport(final int definitionResourceType) {
//     // given
//     final String tenantId1 = "tenant1";
//     engineIntegrationExtension.createTenant(tenantId1);
//     final String tenantId2 = "tenant2";
//     engineIntegrationExtension.createTenant(tenantId2);
//
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
//     authorizationClient.grantSingleResourceAuthorizationsForUser(
//         KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
//     authorizationClient.grantSingleResourceAuthorizationsForUser(
//         KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);
//     deployStartAndImportDefinition(definitionResourceType);
//
//     final String reportId =
//         createReportForDefinitionAsUser(
//             definitionResourceType, Arrays.asList(tenantId1, tenantId2), KERMIT_USER,
// KERMIT_USER);
//
//     // when
//     final Response response =
//         reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void deleteUnauthorizedStoredReport(final int definitionResourceType) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(definitionResourceType);
//
//     final String reportId = createReportForDefinition(definitionResourceType);
//
//     // when
//     final Response response =
//         reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void evaluateUnauthorizedOnTheFlyReport(final int definitionResourceType) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(definitionResourceType);
//
//     // when
//     final ReportDefinitionDto<SingleReportDataDto> definition =
//         constructReportWithDefinition(definitionResourceType);
//     final Response response =
//         reportClient.evaluateReportAsUserAndReturnResponse(
//             definition.getData(), KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void updateUnauthorizedReport(final int definitionResourceType) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(definitionResourceType);
//
//     final String reportId = createReportForDefinition(definitionResourceType);
//
//     final ReportDefinitionDto updatedReport = createReportUpdate(definitionResourceType);
//
//     // when
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .buildUpdateSingleReportRequest(reportId, updatedReport)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void getUnauthorizedReport(final int definitionResourceType) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(definitionResourceType);
//
//     final String reportId = createReportForDefinition(definitionResourceType);
//
//     // when
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .buildGetReportRequest(reportId)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void shareUnauthorizedReport(final int definitionResourceType) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(definitionResourceType);
//
//     final String reportId = createReportForDefinition(definitionResourceType);
//     final ReportShareRestDto reportShareDto = new ReportShareRestDto();
//     reportShareDto.setReportId(reportId);
//
//     // when
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildShareReportRequest(reportShareDto)
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionType")
//   public void newPrivateReportsCanOnlyBeAccessedByOwner(final int definitionResourceType) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(definitionResourceType);
//
//     final String reportId = createNewReport(definitionResourceType);
//
//     // when
//     final Response response =
//         embeddedOptimizeExtension.getRequestExecutor().buildGetReportRequest(reportId).execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//
//     // when
//     final Response otherUserResponse =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildGetReportRequest(reportId)
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .execute();
//
//     // then
//
// assertThat(otherUserResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void unauthorizedReportInCombinedIsNotEvaluated() {
//     // given
//     final String authorizedProcessDefinitionKey = "aprocess";
//     final String notAuthorizedProcessDefinitionKey = "notAuthorizedProcess";
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployAndStartSimpleProcessDefinition(authorizedProcessDefinitionKey);
//     authorizationClient.grantSingleResourceAuthorizationForKermit(
//         authorizedProcessDefinitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);
//     deployAndStartSimpleProcessDefinition(notAuthorizedProcessDefinitionKey);
//     importAllEngineEntitiesFromScratch();
//
//     final String authorizedReportId =
//         createNewSingleMapReportAsUser(authorizedProcessDefinitionKey, KERMIT_USER, KERMIT_USER);
//     final String notAuthorizedReportId =
//         createNewSingleMapReportAsUser(notAuthorizedProcessDefinitionKey, KERMIT_USER,
// KERMIT_USER);
//
//     // when
//     final CombinedReportDataDto combinedReport =
//         createCombinedReportData(authorizedReportId, notAuthorizedReportId);
//
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void updateCombinedReport_addUnauthorizedReport() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final String unauthorizedReportId =
// createReportForDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final String combinedReportId =
//         reportClient.createCombinedReport(PRIVATE_COLLECTION_ID, Collections.emptyList());
//
//     // when
//     final CombinedReportDefinitionRequestDto combinedReportUpdate =
//         new CombinedReportDefinitionRequestDto();
//     combinedReportUpdate.getData().getReportIds().add(unauthorizedReportId);
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportUpdate,
// true)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void updateCombinedReport_removeUnauthorizedReport() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final String reportId = createReportForDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final String combinedReportId =
//         reportClient.createCombinedReport(
//             PRIVATE_COLLECTION_ID, Collections.singletonList(reportId));
//
//     // when
//     final CombinedReportDefinitionRequestDto combinedReportUpdate =
//         new CombinedReportDefinitionRequestDto();
//     combinedReportUpdate.getData().setReports(Collections.emptyList());
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportUpdate,
// true)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void getCombinedReport_containsUnauthorizedReport() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final String reportId = createReportForDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final String combinedReportId =
//         reportClient.createCombinedReport(
//             PRIVATE_COLLECTION_ID, Collections.singletonList(reportId));
//
//     // when
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .buildGetReportRequest(combinedReportId)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void deleteCombinedReport_containsUnauthorizedReport() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     deployStartAndImportDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final String reportId = createReportForDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final String combinedReportId =
//         reportClient.createCombinedReport(
//             PRIVATE_COLLECTION_ID, Collections.singletonList(reportId));
//
//     // when
//     final Response response =
//         reportClient.deleteReport(combinedReportId, true, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void createEventProcessReport() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     databaseIntegrationTestExtension.addEventProcessDefinitionDtoToDatabase(PROCESS_KEY);
//
//     final SingleProcessReportDefinitionRequestDto reportDefinitionDto =
//         reportClient.createSingleProcessReportDefinitionDto(
//             null, PROCESS_KEY, Collections.emptyList());
//
//     reportClient.createSingleProcessReportAsUser(reportDefinitionDto, KERMIT_USER, KERMIT_USER);
//   }
//
//   @Test
//   public void getUnauthorizedEventProcessReport() {
//     // given
//     databaseIntegrationTestExtension.addEventProcessDefinitionDtoToDatabase(PROCESS_KEY);
//     final String reportId =
//         reportClient.createSingleReport(
//             null, DefinitionType.PROCESS, PROCESS_KEY, Collections.emptyList());
//     updateEventProcessRoles(
//         PROCESS_KEY, Collections.singletonList(new IdentityDto(KERMIT_USER, IdentityType.USER)));
//
//     // when
//     final Response response =
//         embeddedOptimizeExtension.getRequestExecutor().buildGetReportRequest(reportId).execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void getEventProcessReport() {
//     // given
//     databaseIntegrationTestExtension.addEventProcessDefinitionDtoToDatabase(PROCESS_KEY);
//     final String reportId =
//         reportClient.createSingleReport(
//             null, DefinitionType.PROCESS, PROCESS_KEY, Collections.emptyList());
//
//     // when
//     final Response response =
//         embeddedOptimizeExtension.getRequestExecutor().buildGetReportRequest(reportId).execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//   }
//
//   @Test
//   public void evaluateUnauthorizedEventProcessReport() {
//     // given
//     databaseIntegrationTestExtension.addEventProcessDefinitionDtoToDatabase(PROCESS_KEY);
//     final String reportId =
//         reportClient.createSingleReport(
//             null, DefinitionType.PROCESS, PROCESS_KEY, Collections.emptyList());
//     updateEventProcessRoles(
//         PROCESS_KEY, Collections.singletonList(new IdentityDto(KERMIT_USER, IdentityType.USER)));
//
//     // when
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildEvaluateSavedReportRequest(reportId)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
//   public void evaluateEventProcessReport() {
//     // given
//     databaseIntegrationTestExtension.addEventProcessDefinitionDtoToDatabase(PROCESS_KEY);
//
//     final String reportId =
//         reportClient.createSingleReport(
//             null, DefinitionType.PROCESS, PROCESS_KEY, Collections.emptyList());
//
//     // when
//     reportClient.evaluateNumberReportById(reportId);
//   }
//
//   @Test
//   public void updateUnauthorizedEventProcessReport() {
//     // given
//     databaseIntegrationTestExtension.addEventProcessDefinitionDtoToDatabase(PROCESS_KEY);
//     final String reportId =
//         reportClient.createSingleReport(
//             null, DefinitionType.PROCESS, PROCESS_KEY, Collections.emptyList());
//     updateEventProcessRoles(
//         PROCESS_KEY, Collections.singletonList(new IdentityDto(KERMIT_USER, IdentityType.USER)));
//
//     // when
//     final ReportDefinitionDto updatedReport =
// createReportUpdate(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildUpdateSingleReportRequest(reportId, updatedReport)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void updateEventProcessReport() {
//     // given
//     databaseIntegrationTestExtension.addEventProcessDefinitionDtoToDatabase(PROCESS_KEY);
//     final String reportId =
//         reportClient.createSingleReport(
//             null, DefinitionType.PROCESS, PROCESS_KEY, Collections.emptyList());
//
//     // when
//     final ReportDefinitionDto updatedReport =
// createReportUpdate(RESOURCE_TYPE_PROCESS_DEFINITION);
//     final Response response = reportClient.updateSingleProcessReport(reportId, updatedReport);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
//   }
//
//   @Test
//   public void deleteUnauthorizedEventProcessReport() {
//     // given
//     databaseIntegrationTestExtension.addEventProcessDefinitionDtoToDatabase(PROCESS_KEY);
//     final String reportId =
//         reportClient.createSingleReport(
//             null, DefinitionType.PROCESS, PROCESS_KEY, Collections.emptyList());
//     updateEventProcessRoles(
//         PROCESS_KEY, Collections.singletonList(new IdentityDto(KERMIT_USER, IdentityType.USER)));
//
//     // when
//     final Response response =
//
// embeddedOptimizeExtension.getRequestExecutor().buildDeleteReportRequest(reportId).execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void deleteEventBasedReport() {
//     // given
//     databaseIntegrationTestExtension.addEventProcessDefinitionDtoToDatabase(PROCESS_KEY);
//
//     final String reportId =
//         reportClient.createSingleReport(
//             null, DefinitionType.PROCESS, PROCESS_KEY, Collections.emptyList());
//
//     // when
//     final Response response = reportClient.deleteReport(reportId, false);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
//   }
//
//   private String getDefinitionKey(final int definitionResourceType) {
//     return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY :
// DECISION_KEY;
//   }
//
//   private String createNewSingleMapReportAsUser(
//       final String processDefinitionKey, final String user, final String password) {
//     final String singleReportId =
//         createNewReportAsUser(RESOURCE_TYPE_PROCESS_DEFINITION, user, password);
//     final ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(processDefinitionKey)
//             .setProcessDefinitionVersion("1")
//             .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
//             .build();
//     final SingleProcessReportDefinitionRequestDto definitionDto =
//         new SingleProcessReportDefinitionRequestDto();
//     definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
//     updateReportAsUser(singleReportId, definitionDto, user, password);
//     return singleReportId;
//   }
//
//   private void deployStartAndImportDefinition(final int definitionResourceType) {
//     switch (definitionResourceType) {
//       case RESOURCE_TYPE_PROCESS_DEFINITION:
//         deployAndStartSimpleProcessDefinition(PROCESS_KEY);
//         break;
//       case RESOURCE_TYPE_DECISION_DEFINITION:
//         deployAndStartSimpleDecisionDefinition(DECISION_KEY);
//         break;
//       default:
//         throw new IllegalStateException(
//             "Uncovered definitionResourceType: " + definitionResourceType);
//     }
//
//     importAllEngineEntitiesFromScratch();
//   }
//
//   private void deployAndStartSimpleProcessDefinition(final String processKey) {
//     engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(processKey));
//   }
//
//   private void deployAndStartSimpleDecisionDefinition(final String decisionKey) {
//
// engineIntegrationExtension.deployAndStartDecisionDefinition(createSimpleDmnModel(decisionKey));
//   }
//
//   private ReportDefinitionDto createReportUpdate(final int definitionResourceType) {
//     switch (definitionResourceType) {
//       default:
//       case RESOURCE_TYPE_PROCESS_DEFINITION:
//         final ProcessReportDataDto processReportData = new ProcessReportDataDto();
//         processReportData.setProcessDefinitionKey("procdef");
//         processReportData.setProcessDefinitionVersion("123");
//         processReportData.setFilter(Collections.emptyList());
//         final SingleProcessReportDefinitionRequestDto processReport =
//             new SingleProcessReportDefinitionRequestDto();
//         processReport.setData(processReportData);
//         processReport.setName("MyReport");
//         return processReport;
//       case RESOURCE_TYPE_DECISION_DEFINITION:
//         final DecisionReportDataDto decisionReportData = new DecisionReportDataDto();
//         decisionReportData.setDecisionDefinitionKey("Decisionef");
//         decisionReportData.setDecisionDefinitionVersion("123");
//         decisionReportData.setFilter(Collections.emptyList());
//         final SingleDecisionReportDefinitionRequestDto decisionReport =
//             new SingleDecisionReportDefinitionRequestDto();
//         decisionReport.setData(decisionReportData);
//         decisionReport.setName("MyReport");
//         return decisionReport;
//     }
//   }
//
//   private String createReportForDefinition(final int resourceType) {
//     return createReportForDefinition(resourceType, Collections.emptyList());
//   }
//
//   private String createReportForDefinition(final int resourceType, final List<String> tenantIds)
// {
//     return createReportForDefinitionAsUser(
//         resourceType, tenantIds, DEFAULT_USERNAME, DEFAULT_PASSWORD);
//   }
//
//   private String createReportForDefinitionAsUser(
//       final int resourceType,
//       final List<String> tenantIds,
//       final String user,
//       final String password) {
//     final String id = createNewReportAsUser(resourceType, user, password);
//     final ReportDefinitionDto definition = constructReportWithDefinition(resourceType,
// tenantIds);
//     updateReportAsUser(id, definition, user, password);
//     return id;
//   }
//
//   private String createNewReport(final int resourceType) {
//     return createNewReportAsUser(resourceType, DEFAULT_USERNAME, DEFAULT_PASSWORD);
//   }
//
//   private String createNewReportAsUser(
//       final int resourceType, final String user, final String password) {
//     switch (resourceType) {
//       default:
//       case RESOURCE_TYPE_PROCESS_DEFINITION:
//         return reportClient.createSingleProcessReportAsUser(
//             new SingleProcessReportDefinitionRequestDto(), user, password);
//       case RESOURCE_TYPE_DECISION_DEFINITION:
//         return reportClient.createNewDecisionReportAsUser(
//             new SingleDecisionReportDefinitionRequestDto(), user, password);
//     }
//   }
//
//   private void updateReportAsUser(
//       final String id,
//       final ReportDefinitionDto updatedReport,
//       final String user,
//       final String password) {
//     final Response response = getUpdateReportResponse(id, updatedReport, user, password);
//     assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
//   }
//
//   private ReportDefinitionDto constructReportWithDefinition(final int resourceType) {
//     return constructReportWithDefinition(
//         resourceType, getDefinitionKey(resourceType), Collections.emptyList());
//   }
//
//   private ReportDefinitionDto constructReportWithDefinition(
//       final int resourceType, final List<String> tenantIds) {
//     return constructReportWithDefinition(resourceType, getDefinitionKey(resourceType),
// tenantIds);
//   }
//
//   private ReportDefinitionDto constructReportWithDefinition(
//       final int resourceType, final String definitionKey, final List<String> tenantIds) {
//     switch (resourceType) {
//       default:
//       case RESOURCE_TYPE_PROCESS_DEFINITION:
//         final SingleProcessReportDefinitionRequestDto processReportDefinitionDto =
//             new SingleProcessReportDefinitionRequestDto();
//         final ProcessReportDataDto processReportDataDto =
//             TemplatedProcessReportDataBuilder.createReportData()
//                 .setProcessDefinitionKey(definitionKey)
//                 .setProcessDefinitionVersion("1")
//                 .setReportDataType(ProcessReportDataType.RAW_DATA)
//                 .build();
//         processReportDataDto.setTenantIds(tenantIds);
//         processReportDefinitionDto.setData(processReportDataDto);
//         return processReportDefinitionDto;
//       case RESOURCE_TYPE_DECISION_DEFINITION:
//         final SingleDecisionReportDefinitionRequestDto decisionReportDefinitionDto =
//             new SingleDecisionReportDefinitionRequestDto();
//         final DecisionReportDataDto decisionReportDataDto =
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(getDefinitionKey(resourceType))
//                 .setDecisionDefinitionVersion("1")
//                 .setReportDataType(DecisionReportDataType.RAW_DATA)
//                 .build();
//         decisionReportDataDto.setTenantIds(tenantIds);
//         decisionReportDefinitionDto.setData(decisionReportDataDto);
//         return decisionReportDefinitionDto;
//     }
//   }
//
//   private Response getUpdateReportResponse(
//       final String id,
//       final ReportDefinitionDto updatedReport,
//       final String user,
//       final String password) {
//     switch (updatedReport.getReportType()) {
//       default:
//       case PROCESS:
//         return reportClient.updateSingleProcessReport(id, updatedReport, false, user, password);
//       case DECISION:
//         return reportClient.updateDecisionReport(id, updatedReport, false, user, password);
//     }
//   }
//
//   private void updateEventProcessRoles(
//       final String eventProcessId, final List<IdentityDto> identityDtos) {
//     databaseIntegrationTestExtension.updateEventProcessRoles(eventProcessId, identityDtos);
//   }
// }
