/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.apache.http.HttpStatus;
import org.assertj.core.util.Lists;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.PRIVATE_COLLECTION_ID;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;

public class ReportDefinitionAuthorizationIT extends AbstractIT {

  private static final String PROCESS_KEY = "aprocess";
  private static final String DECISION_KEY = "aDecision";

  private static final Stream<Integer> definitionType() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  private AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateUnauthorizedStoredReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void deleteUnauthorizedStoredReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildDeleteReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateUnauthorizedOnTheFlyReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    // when
    ReportDefinitionDto<SingleReportDataDto> definition = constructReportWithDefinition(definitionResourceType);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(definition.getData())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void shareUnauthorizedReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);
    ReportShareDto reportShareDto = new ReportShareDto();
    reportShareDto.setReportId(reportId);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildShareReportRequest(reportShareDto)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);

    // when
    Response otherUserResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(otherUserResponse.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

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
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    final CombinedReportDefinitionDto combinedReportUpdate = new CombinedReportDefinitionDto();
    combinedReportUpdate.getData().getReportIds().add(unauthorizedReportId);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportUpdate, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    final CombinedReportDefinitionDto combinedReportUpdate = new CombinedReportDefinitionDto();
    combinedReportUpdate.getData().setReports(Collections.emptyList());
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportUpdate, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
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
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildDeleteReportRequest(combinedReportId, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void createEventProcessReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);

    SingleProcessReportDefinitionDto reportDefinitionDto = reportClient.createSingleProcessReportDefinitionDto(
      null,
      PROCESS_KEY,
      Lists.emptyList()
    );

    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateSingleProcessReportRequest(reportDefinitionDto)
      .execute();

    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void getEventProcessReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);

    String reportId = reportClient.createSingleReport(null, DefinitionType.PROCESS, PROCESS_KEY, Lists.emptyList());

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void evaluateEventProcessReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);

    String reportId = reportClient.createSingleReport(null, DefinitionType.PROCESS, PROCESS_KEY, Lists.emptyList());

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void updateEventProcessReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);

    String reportId = reportClient.createSingleReport(null, DefinitionType.PROCESS, PROCESS_KEY, Lists.emptyList());

    ReportDefinitionDto updatedReport = createReportUpdate(RESOURCE_TYPE_PROCESS_DEFINITION);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateSingleReportRequest(reportId, updatedReport)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  public void deleteEventBasedReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addEventProcessDefinitionDtoToElasticsearch(PROCESS_KEY);

    String reportId = reportClient.createSingleReport(null, DefinitionType.PROCESS, PROCESS_KEY, Lists.emptyList());

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildDeleteReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
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
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
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

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void deployAndStartSimpleProcessDefinition(String processKey) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processKey)
      .startEvent()
      .endEvent()
      .done();
    engineIntegrationExtension.deployAndStartProcess(modelInstance);
  }

  private void deployAndStartSimpleDecisionDefinition(String decisionKey) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    engineIntegrationExtension.deployAndStartDecisionDefinition(modelInstance);
  }

  private ReportDefinitionDto createReportUpdate(int definitionResourceType) {
    switch (definitionResourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        ProcessReportDataDto processReportData = new ProcessReportDataDto();
        processReportData.setProcessDefinitionKey("procdef");
        processReportData.setProcessDefinitionVersion("123");
        processReportData.setFilter(Collections.emptyList());
        SingleProcessReportDefinitionDto processReport = new SingleProcessReportDefinitionDto();
        processReport.setData(processReportData);
        processReport.setName("MyReport");
        return processReport;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        DecisionReportDataDto decisionReportData = new DecisionReportDataDto();
        decisionReportData.setDecisionDefinitionKey("Decisionef");
        decisionReportData.setDecisionDefinitionVersion("123");
        decisionReportData.setFilter(Collections.emptyList());
        SingleDecisionReportDefinitionDto decisionReport = new SingleDecisionReportDefinitionDto();
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
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .withUserAuthentication(user, password)
          .buildCreateSingleProcessReportRequest()
          .execute(IdDto.class, HttpStatus.SC_OK)
          .getId();
      case RESOURCE_TYPE_DECISION_DEFINITION:
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .withUserAuthentication(user, password)
          .buildCreateSingleDecisionReportRequest()
          .execute(IdDto.class, HttpStatus.SC_OK)
          .getId();
    }
  }

  private void updateReportAsUser(String id, ReportDefinitionDto updatedReport, final String user,
                                  final String password) {
    Response response = getUpdateReportResponse(id, updatedReport, user, password);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
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
        SingleProcessReportDefinitionDto processReportDefinitionDto = new SingleProcessReportDefinitionDto();
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
        SingleDecisionReportDefinitionDto decisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
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
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .withUserAuthentication(user, password)
          .buildUpdateSingleProcessReportRequest(id, updatedReport)
          .execute();
      case DECISION:
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .withUserAuthentication(user, password)
          .buildUpdateSingleDecisionReportRequest(id, updatedReport)
          .execute();
    }
  }


  private EventProcessDefinitionDto addEventProcessDefinitionDtoToElasticsearch(final String key) {
    final String version = "1";
    final String name = "eventBasedName";
    final EventProcessDefinitionDto eventProcessDefinitionDto = EventProcessDefinitionDto.eventProcessBuilder()
      .id(key + "-" + version)
      .key(key)
      .name(name)
      .version(version)
      .bpmn20Xml(key + version)
      .flowNodeNames(Collections.emptyMap())
      .userTaskNames(Collections.emptyMap())
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      EVENT_PROCESS_DEFINITION_INDEX_NAME,
      eventProcessDefinitionDto.getId(),
      eventProcessDefinitionDto
    );
    return eventProcessDefinitionDto;
  }
}
