/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessVariableAuthorizationIT extends AbstractIT {

  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
  private static final String VARIABLE_NAME = "variableName";
  private static final String VARIABLE_VALUE = "variableValue";

  private final AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  @Test
  public void variableRequest_authorized() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startSimpleProcess(processDefinition);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeVariableRequestsAsKermit(processDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void variableRequest_noneTenantAuthorized() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startSimpleProcess(processDefinition);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeVariableRequestsAsKermit(processDefinition, Collections.singletonList(null));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void variableRequestWithoutAuthorization() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startSimpleProcess(processDefinition);
    importAllEngineEntitiesFromScratch();

    // when
    Response variableNameResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildProcessVariableNamesRequest(Collections.singletonList(
        createVariableNameRequest("someKey", "1", Collections.emptyList())
      ), false)
      .execute();

    Response variableValueResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildProcessVariableValuesRequestExternal(
        createVariableValueRequest("", "", Collections.emptyList())
      )
      .execute();

    // then
    assertThat(variableNameResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(variableValueResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void variableRequest_authorizedTenant() {
    // given
    final String tenantId = "tenantId";
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinitionWithTenant(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);
    startSimpleProcess(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeVariableRequestsAsKermit(processDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void variableRequest_unauthorizedTenant() {
    // given
    final String tenantId = "tenantId";
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinitionWithTenant(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    startSimpleProcess(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeVariableRequestsAsKermit(processDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void variableRequest_partiallyUnauthorizedTenants() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinitionWithTenant(tenantId1);
    final ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinitionWithTenant(tenantId2);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    startSimpleProcess(processDefinition1);
    startSimpleProcess(processDefinition2);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeVariableRequestsAsKermit(
      processDefinition1,
      Lists.newArrayList(tenantId1, tenantId2)
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getVariableNamesForReports_noAuth() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildProcessVariableNamesForReportsRequest(Collections.singletonList("someReportId"))
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getVariableNamesForReports_canOnlySeeVariableNamesFromAuthorizedReports() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition("someKey");
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of("var1", "val1"));
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      processDefinition1.getKey(),
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
    final String reportId1 = createSingleReport(processDefinition1, KERMIT_USER);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition("otherKey");
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of("var2", "val2"));
    final String reportId2 = createSingleReport(processDefinition2, DEFAULT_USERNAME);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildProcessVariableNamesForReportsRequest(Arrays.asList(reportId1, reportId2))
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .executeAndReturnList(ProcessVariableNameResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(variableResponse)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactly(Tuple.tuple("var1", VariableType.STRING));
  }

  @Test
  public void getVariableValuesForReports_noAuth() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildProcessVariableValuesForReportsRequest(createVariableValuesForReportsRequest(
        Collections.emptyList(), "someName", STRING
      ))
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getVariableValuesForReports_canOnlySeeVariableValuesFromAuthorizedReports() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition("someKey");
    final String variableName = "var1";
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of(variableName, "val1"));
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      processDefinition1.getKey(),
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
    final String reportId1 = createSingleReport(processDefinition1, KERMIT_USER);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition("otherKey");
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of(variableName, "val2"));
    final String reportId2 = createSingleReport(processDefinition2, DEFAULT_USERNAME);

    // when
    List<String> variableValues = embeddedOptimizeExtension.getRequestExecutor()
      .buildProcessVariableValuesForReportsRequest(createVariableValuesForReportsRequest(
        Arrays.asList(reportId1, reportId2), variableName, STRING
      ))
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(variableValues).containsExactly("val1");
  }

  private Response executeVariableRequestsAsKermit(final ProcessDefinitionEngineDto processDefinition) {
    return executeVariableRequestsAsKermit(
      processDefinition,
      processDefinition.getTenantId().map(Collections::singletonList).orElse(Collections.emptyList())
    );
  }

  private Response executeVariableRequestsAsKermit(final ProcessDefinitionEngineDto processDefinition,
                                                         List<String> tenantIds) {

    Response variableValueResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildProcessVariableValuesRequest(createVariableValueRequest(
        processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), tenantIds
      ))
      .execute();


    return variableValueResponse;
  }

  private ProcessVariableReportValuesRequestDto createVariableValuesForReportsRequest(final List<String> reportIds,
                                                                                      final String name,
                                                                                      final VariableType type) {
    ProcessVariableReportValuesRequestDto dto = new ProcessVariableReportValuesRequestDto();
    dto.setReportIds(reportIds);
    dto.setName(name);
    dto.setType(type);
    return dto;
  }

  private ProcessVariableNameRequestDto createVariableNameRequest(final String processDefinitionKey,
                                                                  final String processDefinitionVersion,
                                                                  final List<String> tenantIds) {
    ProcessVariableNameRequestDto dto = new ProcessVariableNameRequestDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionVersion(processDefinitionVersion);
    dto.setTenantIds(tenantIds);
    return dto;
  }

  private ProcessVariableValueRequestDto createVariableValueRequest(final String processDefinitionKey,
                                                                    final String processDefinitionVersion,
                                                                    final List<String> tenantIds) {
    ProcessVariableValueRequestDto dto = new ProcessVariableValueRequestDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionVersion(processDefinitionVersion);
    dto.setTenantIds(tenantIds);
    dto.setName(VARIABLE_NAME);
    dto.setType(STRING);
    return dto;
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    return deploySimpleProcessDefinitionWithTenant(null);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey) {
    return deploySimpleProcessDefinition(processDefinitionKey, null);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinitionWithTenant(String tenantId) {
    return deploySimpleProcessDefinition(PROCESS_DEFINITION_KEY, tenantId);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey,
                                                                   String tenantId) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSimpleBpmnDiagram(processDefinitionKey),
      tenantId
    );
  }

  private String createSingleReport(final ProcessDefinitionEngineDto processDefinition, String user) {
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      reportClient.createSingleProcessReportDefinitionDto(
        null,
        processDefinition.getKey(),
        new ArrayList<>(Collections.singletonList(null))
      );
    singleProcessReportDefinitionDto.getData().setProcessDefinitionVersion(processDefinition.getVersionAsString());
    return reportClient.createSingleProcessReportAsUser(singleProcessReportDefinitionDto, user, user);
  }

  private void startInstanceAndImportEngineEntities(final ProcessDefinitionEngineDto processDefinition,
                                                    final Map<String, Object> variables) {
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();
  }

  private void startSimpleProcess(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, VARIABLE_VALUE);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
  }
}
