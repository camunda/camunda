/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.extension;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.HistoricIncidentEngineDto;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.rest.engine.dto.EngineUserDto;
import org.camunda.optimize.rest.engine.dto.ExternalTaskEngineDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.client.SimpleEngineClient;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockserver.integration.ClientAndServer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.camunda.optimize.test.it.extension.MockServerUtil.MOCKSERVER_HOST;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.util.DmnModels.createDefaultDmnModel;

/**
 * Extension that performs clean up of engine on integration test startup and one more clean up after integration test.
 * Includes configuration of retrievable Engine MockServer
 * <p>
 * Relies on it-plugin being deployed on Camunda Platform Tomcat.
 */
@Slf4j
public class EngineIntegrationExtension implements BeforeEachCallback, AfterEachCallback {
  public static final String DEFAULT_EMAIL_DOMAIN = "@camunda.org";
  public static final String DEFAULT_FIRSTNAME = "firstName";
  public static final String DEFAULT_LASTNAME = "lastName";
  public static final String DEFAULT_FULLNAME = DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME;
  public static final String KERMIT_GROUP_NAME = "anyGroupName";

  private static final Set<String> DEPLOYED_ENGINES = new HashSet<>(Collections.singleton("default"));
  private static final ClientAndServer mockServerClient = initMockServer();

  private final boolean shouldCleanEngine;
  @Getter
  private final String engineName;

  private final SimpleEngineClient engineClient;

  private boolean usingMockServer = false;

  public EngineIntegrationExtension() {
    this(true);
  }

  public EngineIntegrationExtension(final String customEngineName) {
    this(customEngineName, true);
  }

  public EngineIntegrationExtension(final boolean shouldCleanEngine) {
    this(null, shouldCleanEngine);
  }

  public EngineIntegrationExtension(final String customEngineName, final boolean shouldCleanEngine) {
    this.engineName = Optional.ofNullable(customEngineName)
      .map(IntegrationTestConfigurationUtil::resolveFullEngineName)
      .orElseGet(IntegrationTestConfigurationUtil::resolveFullDefaultEngineName);
    this.engineClient = new SimpleEngineClient(getEngineUrl());
    this.shouldCleanEngine = shouldCleanEngine;
    initEngine();
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    if (shouldCleanEngine) {
      cleanEngine();
    }
  }

  @Override
  public void afterEach(final ExtensionContext context) {
    if (usingMockServer) {
      log.info("Resetting all MockServer expectations and logs");
      mockServerClient.reset();
      log.info("No longer using Engine MockServer");
    }
    usingMockServer = false;
  }

  public void cleanEngine() {
    engineClient.cleanEngine(engineName);
    addUser("demo", "demo");
    grantAllAuthorizations("demo");
  }

  private void initEngine() {
    if (!DEPLOYED_ENGINES.contains(engineName)) {
      engineClient.deployEngine(engineName);
      DEPLOYED_ENGINES.add(engineName);
    }
  }

  private static ClientAndServer initMockServer() {
    log.debug("Setting up Engine MockServer on port {}", IntegrationTestConfigurationUtil.getEngineMockServerPort());
    return MockServerUtil.createProxyMockServer(
      IntegrationTestConfigurationUtil.getEngineHost(),
      Integer.parseInt(IntegrationTestConfigurationUtil.getEnginePort()),
      IntegrationTestConfigurationUtil.getEngineMockServerPort()
    );
  }

  public ClientAndServer useEngineMockServer() {
    usingMockServer = true;
    log.debug("Using Engine MockServer");
    return mockServerClient;
  }

  public String getEnginePath() {
    return "/engine-rest/engine/" + getEngineName();
  }

  public UUID createIndependentUserTask() throws IOException {
    return engineClient.createIndependentUserTask();
  }

  public void addCandidateGroupForAllRunningUserTasks(final String groupId) {
    engineClient.addCandidateGroupForAllRunningUserTasks(null, groupId);
  }

  public void addCandidateGroupForAllRunningUserTasks(final String processInstanceId,
                                                      final String groupId) {
    engineClient.addCandidateGroupForAllRunningUserTasks(processInstanceId, groupId);
  }

  public void deleteCandidateGroupForAllRunningUserTasks(final String groupId) {
    engineClient.deleteCandidateGroupForAllRunningUserTasks(groupId);
  }

  public void finishAllRunningUserTasks() {
    engineClient.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, null);
  }

  public void finishAllRunningUserTasks(final String user, final String password) {
    engineClient.finishAllRunningUserTasks(user, password, null);
  }

  public void finishAllRunningUserTasks(final String processInstanceId) {
    engineClient.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceId);
  }

  public void finishAllRunningUserTasks(final String user, final String password, final String processInstanceId) {
    engineClient.finishAllRunningUserTasks(user, password, processInstanceId);
  }

  public void claimAllRunningUserTasks() {
    engineClient.claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, null);
  }

  public void claimAllRunningUserTasks(final String processInstanceId) {
    engineClient.claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceId);
  }

  public void claimAllRunningUserTasksWithAssignee(final String assigneeId, final String processInstanceId) {
    engineClient.claimAllRunningUserTasksWithAssignee(
      assigneeId,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceId
    );
  }

  public void claimAllRunningUserTasks(final String user, final String password, final String processInstanceId) {
    engineClient.claimAllRunningUserTasks(user, password, processInstanceId);
  }

  public void unclaimAllRunningUserTasks() {
    engineClient.unclaimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, null);
  }

  public void completeUserTaskWithoutClaim(final String processInstanceId) {
    engineClient.completeUserTaskWithoutClaim(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceId);
  }

  public String getProcessDefinitionId() {
    return engineClient.getProcessDefinitionId();
  }

  public ProcessInstanceEngineDto deployAndStartProcess(BpmnModelInstance bpmnModelInstance) {
    return engineClient.deployAndStartProcessWithVariables(bpmnModelInstance, new HashMap<>(), "aBusinessKey", null);
  }

  public ProcessInstanceEngineDto deployAndStartProcess(BpmnModelInstance bpmnModelInstance, String tenantId) {
    return engineClient.deployAndStartProcessWithVariables(
      bpmnModelInstance,
      new HashMap<>(),
      "aBusinessKey",
      tenantId
    );
  }

  public ProcessInstanceEngineDto deployAndStartProcessWithVariables(BpmnModelInstance bpmnModelInstance,
                                                                     Map<String, Object> variables) {
    return engineClient.deployAndStartProcessWithVariables(bpmnModelInstance, variables, "aBusinessKey", null);
  }

  public ProcessInstanceEngineDto deployAndStartProcessWithVariables(BpmnModelInstance bpmnModelInstance,
                                                                     Map<String, Object> variables,
                                                                     String tenantId) {
    return engineClient.deployAndStartProcessWithVariables(bpmnModelInstance, variables, "aBusinessKey", tenantId);
  }

  public ProcessInstanceEngineDto deployAndStartProcessWithVariables(BpmnModelInstance bpmnModelInstance,
                                                                     Map<String, Object> variables,
                                                                     String businessKey,
                                                                     String tenantId) {
    return engineClient.deployAndStartProcessWithVariables(bpmnModelInstance, variables, businessKey, tenantId);
  }

  public HistoricProcessInstanceDto getHistoricProcessInstance(String processInstanceId) {
    return engineClient.getHistoricProcessInstance(processInstanceId);
  }

  public List<HistoricActivityInstanceEngineDto> getHistoricActivityInstances() {
    return engineClient.getHistoricActivityInstances();
  }

  @SneakyThrows
  public void cancelActivityInstance(final String processInstanceId, final String activityId) {
    engineClient.cancelActivityInstance(processInstanceId, activityId);
  }

  public void deleteHistoricProcessInstance(String processInstanceId) {
    engineClient.deleteHistoricProcessInstance(processInstanceId);
  }

  public List<HistoricUserTaskInstanceDto> getHistoricTaskInstances(String processInstanceId) {
    return engineClient.getHistoricTaskInstances(processInstanceId, null);
  }

  public List<HistoricUserTaskInstanceDto> getHistoricTaskInstances(String processInstanceId,
                                                                    String taskDefinitionKey) {
    return engineClient.getHistoricTaskInstances(processInstanceId, taskDefinitionKey);
  }

  public void externallyTerminateProcessInstance(String processInstanceId) {
    engineClient.deleteProcessInstance(processInstanceId);
  }

  @SneakyThrows
  public void suspendProcessInstanceByInstanceId(final String processInstanceId) {
    engineClient.performProcessInstanceByInstanceIdSuspensionRequest(processInstanceId, true);
  }

  @SneakyThrows
  public void suspendProcessInstanceByDefinitionId(final String processDefinitionId) {
    engineClient.performProcessInstanceByDefinitionIdSuspensionRequest(processDefinitionId, true);
  }

  @SneakyThrows
  public void suspendProcessInstanceByDefinitionKey(final String processDefinitionKey) {
    engineClient.performProcessInstanceByDefinitionKeySuspensionRequest(processDefinitionKey, true);
  }

  @SneakyThrows
  public void suspendProcessDefinitionById(final String processDefinitionId) {
    engineClient.performProcessDefinitionSuspensionByIdRequest(processDefinitionId, true);
  }

  @SneakyThrows
  public void suspendProcessDefinitionByKey(final String processDefinitionKey) {
    engineClient.performProcessDefinitionSuspensionByKeyRequest(processDefinitionKey, true);
  }

  @SneakyThrows
  public void unsuspendProcessInstanceByInstanceId(final String processInstanceId) {
    engineClient.performProcessInstanceByInstanceIdSuspensionRequest(processInstanceId, false);
  }

  @SneakyThrows
  public void unsuspendProcessInstanceByDefinitionId(final String processDefinitionId) {
    engineClient.performProcessInstanceByDefinitionIdSuspensionRequest(processDefinitionId, false);
  }

  @SneakyThrows
  public void unsuspendProcessInstanceByDefinitionKey(final String processDefinitionKey) {
    engineClient.performProcessInstanceByDefinitionKeySuspensionRequest(processDefinitionKey, false);
  }

  @SneakyThrows
  public void unsuspendProcessDefinitionById(final String processDefinitionId) {
    engineClient.performProcessDefinitionSuspensionByIdRequest(processDefinitionId, false);
  }

  @SneakyThrows
  public void unsuspendProcessDefinitionByKey(final String processDefinitionKey) {
    engineClient.performProcessDefinitionSuspensionByKeyRequest(processDefinitionKey, false);
  }

  @SneakyThrows
  public void suspendProcessInstanceViaBatch(final String processInstanceId) {
    engineClient.performProcessInstanceSuspensionViaBatchRequestAndForceBatchExecution(processInstanceId, true);
  }

  @SneakyThrows
  public void unsuspendProcessInstanceViaBatch(final String processInstanceId) {
    engineClient.performProcessInstanceSuspensionViaBatchRequestAndForceBatchExecution(processInstanceId, false);
  }

  public void deleteVariableInstanceForProcessInstance(final String variableName, final String processInstanceId) {
    engineClient.deleteVariableInstanceForProcessInstance(variableName, processInstanceId);
  }

  public void updateVariableInstanceForProcessInstance(final String processInstanceId, final String variableName,
                                                       final String varValue) {
    engineClient.updateVariableInstanceForProcessInstance(processInstanceId, variableName, varValue);
  }

  public String deployProcessAndGetId(BpmnModelInstance modelInstance) {
    ProcessDefinitionEngineDto processDefinitionId = deployProcessAndGetProcessDefinition(modelInstance, null);
    return processDefinitionId.getId();
  }

  public ProcessDefinitionEngineDto deployProcessAndGetProcessDefinition(BpmnModelInstance modelInstance) {
    return deployProcessAndGetProcessDefinition(modelInstance, null);
  }

  public ProcessDefinitionEngineDto deployProcessAndGetProcessDefinition(BpmnModelInstance modelInstance,
                                                                         String tenantId) {
    DeploymentDto deploymentDto = engineClient.deployProcess(modelInstance, tenantId);
    return engineClient.getProcessDefinitionEngineDto(deploymentDto);
  }

  public void startDecisionInstance(String decisionDefinitionId) {
    engineClient.startDecisionInstance(decisionDefinitionId, new HashMap<>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
  }

  public void startDecisionInstance(String decisionDefinitionId,
                                    Map<String, Object> variables) {
    engineClient.startDecisionInstance(decisionDefinitionId, variables);
  }

  private String getEngineUrl() {
    if (usingMockServer) {
      return "http://" + MOCKSERVER_HOST + ":" + IntegrationTestConfigurationUtil.getEngineMockServerPort()
        + IntegrationTestConfigurationUtil.getEngineRestPath() + engineName;
    }
    return IntegrationTestConfigurationUtil.getEngineRestEndpoint() + engineName;
  }

  @SneakyThrows
  public void failExternalTasks(final String businessKey) {
    engineClient.failExternalTasks(businessKey);
  }

  public void completeExternalTasks(final String processInstanceId) {
    List<ExternalTaskEngineDto> externalTasks = engineClient.getExternalTasks(processInstanceId);
    engineClient.increaseRetry(externalTasks);
    externalTasks.forEach(this::completeExternalTask);
  }

  @SneakyThrows
  private void completeExternalTask(final ExternalTaskEngineDto externalTaskEngineDto) {
    engineClient.completeExternalTask(externalTaskEngineDto);
  }

  @SneakyThrows
  public List<HistoricIncidentEngineDto> getHistoricIncidents() {
    return engineClient.getHistoricIncidents();
  }

  @SneakyThrows
  public void createIncident(final String processInstanceId, final String customIncidentType) {
    engineClient.createIncident(processInstanceId, customIncidentType);
  }

  @SneakyThrows
  public void deleteProcessInstance(final String processInstanceId) {
    engineClient.deleteProcessInstance(processInstanceId);
  }

  @SneakyThrows
  public void deleteProcessDefinition(final String definitionId) {
    engineClient.deleteProcessDefinition(definitionId);
  }

  @SneakyThrows
  public void deleteDeploymentById(final String deploymentId) {
    engineClient.deleteDeploymentById(deploymentId);
  }

  public ProcessInstanceEngineDto startProcessInstance(String processDefinitionId) {
    return startProcessInstance(processDefinitionId, new HashMap<>());
  }

  public ProcessInstanceEngineDto startProcessInstance(String processDefinitionId, Map<String, Object> variables) {
    return engineClient.startProcessInstance(processDefinitionId, variables, "aBusinessKey");
  }

  public ProcessInstanceEngineDto startProcessInstance(final String processDefinitionId, final String businessKey) {
    return engineClient.startProcessInstance(processDefinitionId, new HashMap<>(), businessKey);
  }

  public ProcessInstanceEngineDto startProcessInstance(String procDefId,
                                                       Map<String, Object> variables,
                                                       String businessKey) {
    return engineClient.startProcessInstance(procDefId, variables, businessKey);
  }

  public void waitForAllProcessesToFinish() {
    engineClient.waitForAllProcessesToFinish();
  }

  public void createTenant(final String id) {
    createTenant(id, id);
  }

  public void createTenant(final String id, final String name) {
    engineClient.createTenant(id, name);
  }

  public void updateTenant(final String id, final String name) {
    engineClient.updateTenant(id, name);
  }

  public void addUser(final String username, final String password) {
    EngineUserDto userDto = engineClient.createEngineUserDto(username, password);
    engineClient.createUser(userDto);
  }

  public void addUser(final String username, final String firstName, final String lastName) {
    EngineUserDto userDto = engineClient.createEngineUserDto(username, firstName, lastName);
    engineClient.createUser(userDto);
  }

  public void addUser(final EngineUserDto userDto) {
    engineClient.createUser(userDto);
  }

  @SneakyThrows
  public void unlockUser(String username) {
    engineClient.unlockUser(username);
  }

  public void createAuthorization(AuthorizationDto authorizationDto) {
    engineClient.createAuthorization(authorizationDto);
  }

  public void grantUserOptimizeAccess(final String userId) {
    AuthorizationDto authorizationDto = engineClient.createOptimizeApplicationAuthorizationDto();
    authorizationDto.setUserId(userId);
    engineClient.createAuthorization(authorizationDto);
  }

  public void grantGroupOptimizeAccess(final String groupId) {
    AuthorizationDto authorizationDto = engineClient.createOptimizeApplicationAuthorizationDto();
    authorizationDto.setGroupId(groupId);
    engineClient.createAuthorization(authorizationDto);
  }

  public void grantAllAuthorizations(String username) {
    engineClient.grantAllAuthorizations(username);
  }

  public void createGroup(final String id) {
    engineClient.createGroup(id, KERMIT_GROUP_NAME);
  }

  public void createGroup(final String id, final String name) {
    engineClient.createGroup(id, name);
  }

  public void addUserToGroup(String userId, String groupId) {
    engineClient.addUserToGroup(userId, groupId);
  }

  public DecisionDefinitionEngineDto deployAndStartDecisionDefinition() {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDecisionDefinition(
      createDefaultDmnModel()
    );
    engineClient.startDecisionInstance(
      decisionDefinitionEngineDto.getId(),
      new HashMap<>() {{
        put("amount", 200);
        put("invoiceCategory", "Misc");
      }}
    );
    return decisionDefinitionEngineDto;
  }

  public DecisionDefinitionEngineDto deployAndStartDecisionDefinition(DmnModelInstance dmnModelInstance) {
    return deployAndStartDecisionDefinition(dmnModelInstance, null);
  }

  public DecisionDefinitionEngineDto deployAndStartDecisionDefinition(DmnModelInstance dmnModelInstance,
                                                                      String tenantId) {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDecisionDefinition(
      dmnModelInstance,
      tenantId
    );
    engineClient.startDecisionInstance(decisionDefinitionEngineDto.getId(), new HashMap<String, Object>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    return decisionDefinitionEngineDto;
  }

  public DecisionDefinitionEngineDto deployDecisionDefinitionWithTenant(String tenantId) {
    return deployDecisionDefinition(createDefaultDmnModel(), tenantId);
  }

  public DecisionDefinitionEngineDto deployDecisionDefinition() {
    return deployDecisionDefinition(createDefaultDmnModel());
  }

  public DecisionDefinitionEngineDto deployDecisionDefinition(String dmnPath) {
    return deployDecisionDefinition(dmnPath, null);
  }

  public DecisionDefinitionEngineDto deployDecisionDefinition(String dmnPath, String tenantId) {
    final DmnModelInstance dmnModelInstance = Dmn.readModelFromStream(
      getClass().getClassLoader().getResourceAsStream(dmnPath)
    );
    return deployDecisionDefinition(dmnModelInstance, tenantId);
  }

  public DecisionDefinitionEngineDto deployDecisionDefinition(DmnModelInstance dmnModelInstance) {
    return deployDecisionDefinition(dmnModelInstance, null);
  }

  public DecisionDefinitionEngineDto deployDecisionDefinition(DmnModelInstance dmnModelInstance, String tenantId) {
    DeploymentDto deploymentDto = engineClient.deployDecision(dmnModelInstance, tenantId);
    return engineClient.getDecisionDefinitionByDeployment(deploymentDto);
  }
}
