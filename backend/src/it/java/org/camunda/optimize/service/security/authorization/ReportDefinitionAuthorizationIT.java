/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.optimize.CollectionClient.PRIVATE_COLLECTION_ID;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

public class ReportDefinitionAuthorizationIT extends AbstractIT {

  private static final String PROCESS_KEY = "aprocess";
  private static final String DECISION_KEY = "aDecision";

  private static final Stream<Integer> definitionType() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateUnauthorizedStoredReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateUnauthorizedTenantsStoredReport(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    engineIntegrationExtension.createTenant(tenantId);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType, Arrays.asList(tenantId));

    // when
    Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluatePartiallyUnauthorizedTenantsStoredReport(int definitionResourceType) {
    // given
    final String tenantId1 = "tenant1";
    engineIntegrationExtension.createTenant(tenantId1);
    final String tenantId2 = "tenant2";
    engineIntegrationExtension.createTenant(tenantId2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType, Arrays.asList(tenantId1, tenantId2));

    // when
    Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateAllTenantsAuthorizedStoredReport(int definitionResourceType) {
    // given
    final String tenantId1 = "tenant1";
    engineIntegrationExtension.createTenant(tenantId1);
    final String tenantId2 = "tenant2";
    engineIntegrationExtension.createTenant(tenantId2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinitionAsUser(
      definitionResourceType, Arrays.asList(tenantId1, tenantId2), KERMIT_USER, KERMIT_USER
    );

    // when
    Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void deleteUnauthorizedStoredReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = reportClient.evaluateReportAsUserRawResponse(reportId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateUnauthorizedOnTheFlyReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    // when
    ReportDefinitionDto<SingleReportDataDto> definition = constructReportWithDefinition(definitionResourceType);
    Response response = reportClient.evaluateReportAsUserAndReturnResponse(
      definition.getData(),
      KERMIT_USER,
      KERMIT_USER
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void updateUnauthorizedReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    ReportDefinitionDto updatedReport = createReportUpdate(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateSingleReportRequest(reportId, updatedReport)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void getUnauthorizedReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void shareUnauthorizedReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);
    ReportShareRestDto reportShareDto = new ReportShareRestDto();
    reportShareDto.setReportId(reportId);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildShareReportRequest(reportShareDto)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void newPrivateReportsCanOnlyBeAccessedByOwner(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createNewReport(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when
    Response otherUserResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(otherUserResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void unauthorizedReportInCombinedIsNotEvaluated() {
    // given
    final String authorizedProcessDefinitionKey = "aprocess";
    final String notAuthorizedProcessDefinitionKey = "notAuthorizedProcess";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition(authorizedProcessDefinitionKey);
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      authorizedProcessDefinitionKey,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
    deployAndStartSimpleProcessDefinition(notAuthorizedProcessDefinitionKey);
    importAllEngineEntitiesFromScratch();

    String authorizedReportId = createNewSingleMapReportAsUser(
      authorizedProcessDefinitionKey, KERMIT_USER, KERMIT_USER
    );
    String notAuthorizedReportId = createNewSingleMapReportAsUser(
      notAuthorizedProcessDefinitionKey, KERMIT_USER, KERMIT_USER
    );

    // when
    CombinedReportDataDto combinedReport = createCombinedReportData(authorizedReportId, notAuthorizedReportId);

    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateCombinedReport_addUnauthorizedReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
    String unauthorizedReportId = createReportForDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
    final String combinedReportId =
      reportClient.createCombinedReport(PRIVATE_COLLECTION_ID, Collections.emptyList());

    // when
    final CombinedReportDefinitionRequestDto combinedReportUpdate = new CombinedReportDefinitionRequestDto();
    combinedReportUpdate.getData().getReportIds().add(unauthorizedReportId);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportUpdate, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateCombinedReport_removeUnauthorizedReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
    String reportId = createReportForDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
    final String combinedReportId =
      reportClient.createCombinedReport(PRIVATE_COLLECTION_ID, Collections.singletonList(reportId));

    // when
    final CombinedReportDefinitionRequestDto combinedReportUpdate = new CombinedReportDefinitionRequestDto();
    combinedReportUpdate.getData().setReports(Collections.emptyList());
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportUpdate, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getCombinedReport_containsUnauthorizedReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
    String reportId = createReportForDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
    final String combinedReportId =
      reportClient.createCombinedReport(PRIVATE_COLLECTION_ID, Collections.singletonList(reportId));

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetReportRequest(combinedReportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void deleteCombinedReport_containsUnauthorizedReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
    String reportId = createReportForDefinition(RESOURCE_TYPE_PROCESS_DEFINITION);
    final String combinedReportId =
      reportClient.createCombinedReport(PRIVATE_COLLECTION_ID, Collections.singletonList(reportId));

    // when
    Response response = reportClient.deleteReport(combinedReportId, true, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void createEventProcessReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);

    SingleProcessReportDefinitionRequestDto reportDefinitionDto = reportClient.createSingleProcessReportDefinitionDto(
      null,
      PROCESS_KEY,
      Collections.emptyList()
    );

    reportClient.createSingleProcessReportAsUser(reportDefinitionDto, KERMIT_USER, KERMIT_USER);
  }

  @Test
  public void getUnauthorizedEventProcessReport() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);
    String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      PROCESS_KEY,
      Collections.emptyList()
    );
    updateEventProcessRoles(PROCESS_KEY, Collections.singletonList(new IdentityDto(KERMIT_USER, IdentityType.USER)));

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getEventProcessReport() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);
    String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      PROCESS_KEY,
      Collections.emptyList()
    );

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void evaluateUnauthorizedEventProcessReport() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);
    String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      PROCESS_KEY,
      Collections.emptyList()
    );
    updateEventProcessRoles(PROCESS_KEY, Collections.singletonList(new IdentityDto(KERMIT_USER, IdentityType.USER)));

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void evaluateEventProcessReport() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);

    String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      PROCESS_KEY,
      Collections.emptyList()
    );

    // when
    reportClient.evaluateNumberReportById(reportId);
  }

  @Test
  public void updateUnauthorizedEventProcessReport() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);
    String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      PROCESS_KEY,
      Collections.emptyList()
    );
    updateEventProcessRoles(PROCESS_KEY, Collections.singletonList(new IdentityDto(KERMIT_USER, IdentityType.USER)));

    // when
    ReportDefinitionDto updatedReport = createReportUpdate(RESOURCE_TYPE_PROCESS_DEFINITION);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleReportRequest(reportId, updatedReport)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateEventProcessReport() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);
    String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      PROCESS_KEY,
      Collections.emptyList()
    );

    // when
    ReportDefinitionDto updatedReport = createReportUpdate(RESOURCE_TYPE_PROCESS_DEFINITION);
    Response response = reportClient.updateSingleProcessReport(reportId, updatedReport);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void deleteUnauthorizedEventProcessReport() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);
    String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      PROCESS_KEY,
      Collections.emptyList()
    );
    updateEventProcessRoles(PROCESS_KEY, Collections.singletonList(new IdentityDto(KERMIT_USER, IdentityType.USER)));

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void deleteEventBasedReport() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);

    String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      PROCESS_KEY,
      Collections.emptyList()
    );

    // when
    Response response = reportClient.deleteReport(reportId, false);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private String getDefinitionKey(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY : DECISION_KEY;
  }

  private String createNewSingleMapReportAsUser(final String processDefinitionKey,
                                                final String user,
                                                final String password) {
    String singleReportId = createNewReportAsUser(RESOURCE_TYPE_PROCESS_DEFINITION, user, password);
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    SingleProcessReportDefinitionRequestDto definitionDto = new SingleProcessReportDefinitionRequestDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReportAsUser(singleReportId, definitionDto, user, password);
    return singleReportId;
  }

  private void deployStartAndImportDefinition(int definitionResourceType) {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        deployAndStartSimpleProcessDefinition(PROCESS_KEY);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deployAndStartSimpleDecisionDefinition(DECISION_KEY);
        break;
      default:
        throw new IllegalStateException("Uncovered definitionResourceType: " + definitionResourceType);
    }

    importAllEngineEntitiesFromScratch();
  }

  private void deployAndStartSimpleProcessDefinition(String processKey) {
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(processKey));
  }

  private void deployAndStartSimpleDecisionDefinition(String decisionKey) {
    engineIntegrationExtension.deployAndStartDecisionDefinition(createSimpleDmnModel(decisionKey));
  }

  private ReportDefinitionDto createReportUpdate(int definitionResourceType) {
    switch (definitionResourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        ProcessReportDataDto processReportData = new ProcessReportDataDto();
        processReportData.setProcessDefinitionKey("procdef");
        processReportData.setProcessDefinitionVersion("123");
        processReportData.setFilter(Collections.emptyList());
        SingleProcessReportDefinitionRequestDto processReport = new SingleProcessReportDefinitionRequestDto();
        processReport.setData(processReportData);
        processReport.setName("MyReport");
        return processReport;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        DecisionReportDataDto decisionReportData = new DecisionReportDataDto();
        decisionReportData.setDecisionDefinitionKey("Decisionef");
        decisionReportData.setDecisionDefinitionVersion("123");
        decisionReportData.setFilter(Collections.emptyList());
        SingleDecisionReportDefinitionRequestDto decisionReport = new SingleDecisionReportDefinitionRequestDto();
        decisionReport.setData(decisionReportData);
        decisionReport.setName("MyReport");
        return decisionReport;
    }
  }

  private String createReportForDefinition(final int resourceType) {
    return createReportForDefinition(resourceType, Collections.emptyList());
  }

  private String createReportForDefinition(final int resourceType, final List<String> tenantIds) {
    return createReportForDefinitionAsUser(resourceType, tenantIds, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private String createReportForDefinitionAsUser(final int resourceType,
                                                 final List<String> tenantIds,
                                                 final String user,
                                                 final String password) {
    String id = createNewReportAsUser(resourceType, user, password);
    ReportDefinitionDto definition = constructReportWithDefinition(resourceType, tenantIds);
    updateReportAsUser(id, definition, user, password);
    return id;
  }

  private String createNewReport(int resourceType) {
    return createNewReportAsUser(resourceType, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private String createNewReportAsUser(int resourceType, final String user, final String password) {
    switch (resourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        return reportClient.createSingleProcessReportAsUser(
          new SingleProcessReportDefinitionRequestDto(),
          user,
          password
        );
      case RESOURCE_TYPE_DECISION_DEFINITION:
        return reportClient.createNewDecisionReportAsUser(
          new SingleDecisionReportDefinitionRequestDto(),
          user,
          password
        );
    }
  }

  private void updateReportAsUser(String id, ReportDefinitionDto updatedReport, final String user,
                                  final String password) {
    Response response = getUpdateReportResponse(id, updatedReport, user, password);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private ReportDefinitionDto constructReportWithDefinition(int resourceType) {
    return constructReportWithDefinition(resourceType, getDefinitionKey(resourceType), Collections.emptyList());
  }

  private ReportDefinitionDto constructReportWithDefinition(final int resourceType, final List<String> tenantIds) {
    return constructReportWithDefinition(resourceType, getDefinitionKey(resourceType), tenantIds);
  }

  private ReportDefinitionDto constructReportWithDefinition(final int resourceType, final String definitionKey,
                                                            final List<String> tenantIds) {
    switch (resourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        SingleProcessReportDefinitionRequestDto processReportDefinitionDto =
          new SingleProcessReportDefinitionRequestDto();
        ProcessReportDataDto processReportDataDto = TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(definitionKey)
          .setProcessDefinitionVersion("1")
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        processReportDataDto.setTenantIds(tenantIds);
        processReportDefinitionDto.setData(processReportDataDto);
        return processReportDefinitionDto;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        SingleDecisionReportDefinitionRequestDto decisionReportDefinitionDto =
          new SingleDecisionReportDefinitionRequestDto();
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder.create()
          .setDecisionDefinitionKey(getDefinitionKey(resourceType))
          .setDecisionDefinitionVersion("1")
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        decisionReportDataDto.setTenantIds(tenantIds);
        decisionReportDefinitionDto.setData(decisionReportDataDto);
        return decisionReportDefinitionDto;
    }
  }

  private Response getUpdateReportResponse(final String id,
                                           final ReportDefinitionDto updatedReport,
                                           final String user,
                                           final String password) {
    switch (updatedReport.getReportType()) {
      default:
      case PROCESS:
        return reportClient.updateSingleProcessReport(id, updatedReport, false, user, password);
      case DECISION:
        return reportClient.updateDecisionReport(id, updatedReport, false, user, password);
    }
  }

  private void updateEventProcessRoles(final String eventProcessId, final List<IdentityDto> identityDtos) {
    try {
      UpdateRequest request = new UpdateRequest(EVENT_PROCESS_MAPPING_INDEX_NAME, eventProcessId)
        .script(new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          "ctx._source.roles = params.updatedRoles;",
          Collections.singletonMap(
            "updatedRoles",
            elasticSearchIntegrationTestExtension.getObjectMapper()
              .convertValue(normalizeToSimpleIdentityDtos(identityDtos), Object.class)
          )
        ))
        .setRefreshPolicy(IMMEDIATE);
      final UpdateResponse updateResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
        .update(request);
      if (updateResponse.getShardInfo().getFailed() > 0) {
        throw new OptimizeIntegrationTestException(String.format(
          "Was not able to update event process roles with id [%s].", eventProcessId
        ));
      }
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Unable to update event process roles.", e);
    }
  }

  private List<EventProcessRoleRequestDto<IdentityDto>> normalizeToSimpleIdentityDtos(final List<IdentityDto> identityDtos) {
    return identityDtos.stream()
      .filter(Objects::nonNull)
      .map(identityDto -> new IdentityDto(identityDto.getId(), identityDto.getType()))
      .map(EventProcessRoleRequestDto::new)
      .collect(Collectors.toList());
  }

}
