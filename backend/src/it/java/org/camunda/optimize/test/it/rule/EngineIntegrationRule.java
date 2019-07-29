/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.rule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.engine.TenantEngineDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.rest.engine.dto.GroupDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.engine.dto.TaskDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.mapper.CustomDeserializer;
import org.camunda.optimize.service.util.mapper.CustomSerializer;
import org.camunda.optimize.test.engine.EnginePluginClient;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Rule that performs clean up of engine on integration test startup and one more clean up after integration test.
 * <p>
 * Relies on it-plugin being deployed on cambpm platform Tomcat.
 */
@Slf4j
public class EngineIntegrationRule extends TestWatcher {
  private static final Set<String> DEPLOYED_ENGINES = new HashSet<>(Collections.singleton("default"));

  private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
  private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
  private static final EnginePluginClient ENGINE_PLUGIN_CLIENT = new EnginePluginClient(HTTP_CLIENT);

  public static final String DEFAULT_DMN_DEFINITION_PATH = "dmn/invoiceBusinessDecision_withName_and_versionTag.xml";
  private static final int MAX_WAIT = 10;
  private static final String COUNT = "count";

  private boolean shouldCleanEngine;

  @Getter
  private final String engineName;

  public EngineIntegrationRule() {
    this(null);
  }

  public EngineIntegrationRule(final String customEngineName) {
    this(customEngineName, true);
  }

  public EngineIntegrationRule(final String customEngineName, final boolean shouldCleanEngine) {
    this.engineName = Optional.ofNullable(customEngineName)
      .map(IntegrationTestConfigurationUtil::resolveFullEngineName)
      .orElseGet(IntegrationTestConfigurationUtil::resolveFullDefaultEngineName);
    this.shouldCleanEngine = shouldCleanEngine;
    initEngine();
  }

  @Override
  protected void starting(final Description description) {
    super.starting(description);

    if (shouldCleanEngine) {
      ENGINE_PLUGIN_CLIENT.cleanEngine(engineName);
      addUser("demo", "demo");
      grantAllAuthorizations("demo");
    }
  }

  private void initEngine() {
    if (!DEPLOYED_ENGINES.contains(engineName)) {
      ENGINE_PLUGIN_CLIENT.deployEngine(engineName);
      DEPLOYED_ENGINES.add(engineName);
    }
  }

  private static ObjectMapper createObjectMapper() {
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(IntegrationTestConfigurationUtil.getEngineDateFormat());
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(formatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(formatter));

    return Jackson2ObjectMapperBuilder
      .json()
      .modules(javaTimeModule)
      .featuresToDisable(
        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
      )
      .featuresToEnable(
        JsonParser.Feature.ALLOW_COMMENTS,
        SerializationFeature.INDENT_OUTPUT
      )
      .build();
  }

  public UUID createIndependentUserTask() throws IOException {
    final UUID taskId = UUID.randomUUID();
    final HttpPost completePost = new HttpPost(getEngineUrl() + "/task/create");
    completePost.setEntity(new StringEntity(
      String.format("{\"id\":\"%s\",\"name\":\"name\"}", taskId.toString())
    ));
    completePost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = HTTP_CLIENT.execute(completePost)) {
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException(
          "Could not create user task! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
    return taskId;
  }

  public void addCandidateGroupForAllRunningUserTasks(final String groupId) {
    try (final CloseableHttpClient httpClient = createClientWithDefaultBasicAuth()) {
      final List<TaskDto> tasks = getUserTasks(httpClient, null);
      for (TaskDto task : tasks) {
        addCandidateGroupToUserTask(httpClient, task.getId(), groupId);
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  private void addCandidateGroupToUserTask(final CloseableHttpClient httpClient, final String taskId,
                                           final String groupId) throws IOException {
    HttpPost addCandidateGroupPost = new HttpPost(getAddIdentityLinkUrl(taskId));
    String bodyAsString = String.format("{\"groupId\": \"%s\", \"type\": \"candidate\"}", groupId);
    addCandidateGroupPost.setEntity(new StringEntity(bodyAsString));
    addCandidateGroupPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = httpClient.execute(addCandidateGroupPost)) {
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException(
          "Could not add candidate group! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  private String getAddIdentityLinkUrl(final String taskId) {
    return getEngineUrl() + "/task/" + taskId + "/identity-links";
  }

  public void deleteCandidateGroupForAllRunningUserTasks(final String groupId) {
    try (final CloseableHttpClient httpClient = createClientWithDefaultBasicAuth()) {
      final List<TaskDto> tasks = getUserTasks(httpClient, null);
      for (TaskDto task : tasks) {
        deleteCandidateGroupToUserTask(httpClient, task.getId(), groupId);
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  private void deleteCandidateGroupToUserTask(final CloseableHttpClient httpClient,
                                              final String taskId,
                                              final String groupId) throws IOException {
    HttpPost addCandidateGroupPost = new HttpPost(getDeleteIdentityLinkUrl(taskId));
    String bodyAsString = String.format("{\"groupId\": \"%s\", \"type\": \"candidate\"}", groupId);
    addCandidateGroupPost.setEntity(new StringEntity(bodyAsString));
    addCandidateGroupPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = httpClient.execute(addCandidateGroupPost)) {
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException(
          "Could not add candidate group! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  private String getDeleteIdentityLinkUrl(final String taskId) {
    return getEngineUrl() + "/task/" + taskId + "/identity-links/delete";
  }

  public void finishAllRunningUserTasks() {
    finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public void finishAllRunningUserTasks(final String user, final String password) {
    finishAllRunningUserTasks(user, password, null);
  }

  public void finishAllRunningUserTasks(final String processInstanceId) {
    finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceId);
  }

  public void finishAllRunningUserTasks(final String user, final String password, final String processInstanceId) {
    try (final CloseableHttpClient httpClient = createClientWithBasicAuth(user, password)) {
      final List<TaskDto> tasks = getUserTasks(httpClient, processInstanceId);
      for (TaskDto task : tasks) {
        claimAndCompleteUserTask(httpClient, user, task);
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  public void claimAllRunningUserTasks() {
    claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, null);
  }

  public void claimAllRunningUserTasks(final String processInstanceId) {
    claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceId);
  }

  public void claimAllRunningUserTasks(final String user, final String password, final String processInstanceId) {
    try (final CloseableHttpClient httpClient = createClientWithBasicAuth(user, password)) {
      final List<TaskDto> tasks = getUserTasks(httpClient, processInstanceId);
      for (TaskDto task : tasks) {
        claimUserTask(httpClient, user, task);
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  public void unclaimAllRunningUserTasks() {
    unclaimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, null);
  }

  private void unclaimAllRunningUserTasks(final String user, final String password, final String processInstanceId) {
    try (final CloseableHttpClient httpClient = createClientWithBasicAuth(user, password)) {
      final List<TaskDto> tasks = getUserTasks(httpClient, processInstanceId);
      for (TaskDto task : tasks) {
        unclaimUserTask(httpClient, user, task);
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  private void unclaimUserTask(final CloseableHttpClient authenticatingClient, final String userId, final TaskDto task)
    throws IOException {
    HttpPost claimPost = new HttpPost(getSecuredUnclaimTaskUri(task.getId()));
    claimPost.setEntity(new StringEntity("{ \"userId\" : \"" + userId + "\" }"));
    claimPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = authenticatingClient.execute(claimPost)) {
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException(
          "Could not unclaim user task! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  public void completeUserTaskWithoutClaim(final String processInstanceId) {
    completeUserTaskWithoutClaim(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceId);
  }

  private void completeUserTaskWithoutClaim(final String user, final String password, final String processInstanceId) {
    try (final CloseableHttpClient httpClient = createClientWithBasicAuth(user, password)) {
      final List<TaskDto> tasks = getUserTasks(httpClient, processInstanceId);
      for (TaskDto task : tasks) {
        completeUserTask(httpClient, task);
      }
    } catch (IOException e) {
      log.error("Error while trying to complete user task!", e);
    }
  }

  private List<TaskDto> getUserTasks(final CloseableHttpClient authenticatingClient,
                                     final String processInstanceIdFilter) {
    final List<TaskDto> tasks;
    try {
      final URIBuilder uriBuilder = new URIBuilder(getTaskListUri());
      if (processInstanceIdFilter != null) {
        uriBuilder.addParameter("processInstanceId", processInstanceIdFilter);
      }
      final HttpGet get = new HttpGet(uriBuilder.build());
      try (CloseableHttpResponse response = authenticatingClient.execute(get)) {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        // @formatter:off
        tasks = OBJECT_MAPPER.readValue(responseString, new TypeReference<List<TaskDto>>() {});
        // @formatter:on
      } catch (IOException e) {
        throw new RuntimeException("Error while trying to finish the user task!!");
      }
    } catch (URISyntaxException e) {
      throw new RuntimeException("Error while trying to create task list url !!");
    }
    return tasks;
  }

  private String getTaskListUri() {
    return getEngineUrl() + "/task";
  }

  private void claimAndCompleteUserTask(final CloseableHttpClient authenticatingClient, final String userId,
                                        final TaskDto task)
    throws IOException {
    claimUserTaskAsDefaultUser(authenticatingClient, userId, task);
    completeUserTask(authenticatingClient, task);
  }

  private void claimUserTaskAsDefaultUser(final CloseableHttpClient authenticatingClient, final String userId,
                                          final TaskDto task)
    throws IOException {
    claimUserTask(authenticatingClient, userId, task);
  }

  private void claimUserTask(final CloseableHttpClient authenticatingClient, final String userId, final TaskDto task)
    throws IOException {
    HttpPost claimPost = new HttpPost(getSecuredClaimTaskUri(task.getId()));
    claimPost.setEntity(new StringEntity("{ \"userId\" : \"" + userId + "\" }"));
    claimPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = authenticatingClient.execute(claimPost)) {
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException(
          "Could not claim user task! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  private void completeUserTask(final CloseableHttpClient authenticatingClient, final TaskDto task)
    throws IOException {
    HttpPost completePost = new HttpPost(getSecuredCompleteTaskUri(task.getId()));
    completePost.setEntity(new StringEntity("{}"));
    completePost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = authenticatingClient.execute(completePost)) {
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException(
          "Could not complete user task! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  private String getSecuredUnclaimTaskUri(final String taskId) {
    return getSecuredEngineUrl() + "/task/" + taskId + "/unclaim";
  }

  private String getSecuredClaimTaskUri(final String taskId) {
    return getSecuredEngineUrl() + "/task/" + taskId + "/claim";
  }

  private String getSecuredCompleteTaskUri(final String taskId) {
    return getSecuredEngineUrl() + "/task/" + taskId + "/complete";
  }

  public String getProcessDefinitionId() {
    HttpRequestBase get = new HttpGet(getProcessDefinitionUri());
    CloseableHttpResponse response;
    try {
      response = HTTP_CLIENT.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      List<ProcessDefinitionEngineDto> procDefs =
        OBJECT_MAPPER.readValue(responseString, new TypeReference<List<ProcessDefinitionEngineDto>>() {
        });
      response.close();
      assertThat(procDefs.size(), is(1));
      return procDefs.get(0).getId();
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not fetch the process definition!", e);
    }
  }

  public List<ProcessDefinitionEngineDto> getLatestProcessDefinitions() {
    URI uri;
    try {
      uri = new URIBuilder(getProcessDefinitionUri())
        .setParameter("latestVersion", "true")
        .build();
    } catch (URISyntaxException e) {
      throw new OptimizeRuntimeException("Could not create URI!", e);
    }
    HttpRequestBase get = new HttpGet(uri);
    CloseableHttpResponse response;
    try {
      response = HTTP_CLIENT.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      List<ProcessDefinitionEngineDto> procDefs =
        OBJECT_MAPPER.readValue(responseString, new TypeReference<List<ProcessDefinitionEngineDto>>() {
        });
      response.close();
      return procDefs;
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not fetch the process definition!", e);
    }
  }

  public ProcessDefinitionXmlEngineDto getProcessDefinitionXml(String processDefinitionId) {
    HttpRequestBase get = new HttpGet(getProcessDefinitionXmlUri(processDefinitionId));
    CloseableHttpResponse response;
    try {
      response = HTTP_CLIENT.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      ProcessDefinitionXmlEngineDto xml =
        OBJECT_MAPPER.readValue(responseString, ProcessDefinitionXmlEngineDto.class);
      response.close();
      return xml;
    } catch (IOException e) {
      String errorMessage =
        String.format("Could not fetch the process definition xml for id [%s]!", processDefinitionId);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public ProcessInstanceEngineDto deployAndStartProcess(BpmnModelInstance bpmnModelInstance) {
    return deployAndStartProcess(bpmnModelInstance, null);
  }

  public ProcessInstanceEngineDto deployAndStartProcess(BpmnModelInstance bpmnModelInstance, String tenantId) {
    return deployAndStartProcessWithVariables(bpmnModelInstance, new HashMap<>(), tenantId);
  }


  public ProcessInstanceEngineDto deployAndStartProcessWithVariables(BpmnModelInstance bpmnModelInstance,
                                                                     Map<String, Object> variables) {
    return deployAndStartProcessWithVariables(bpmnModelInstance, variables, null);
  }

  public ProcessInstanceEngineDto deployAndStartProcessWithVariables(BpmnModelInstance bpmnModelInstance,
                                                                     Map<String, Object> variables,
                                                                     String tenantId) {
    return deployAndStartProcessWithVariables(bpmnModelInstance, variables, "aBusinessKey", tenantId);
  }

  public ProcessInstanceEngineDto deployAndStartProcessWithVariables(BpmnModelInstance bpmnModelInstance,
                                                                     Map<String, Object> variables,
                                                                     String businessKey,
                                                                     String tenantId) {
    final DeploymentDto deployment = deployProcess(bpmnModelInstance, tenantId);
    final List<ProcessDefinitionEngineDto> procDefs = getAllProcessDefinitions(deployment, HTTP_CLIENT);
    assertThat(procDefs.size(), is(1));
    final ProcessDefinitionEngineDto processDefinitionEngineDto = procDefs.get(0);
    final ProcessInstanceEngineDto processInstanceDto = startProcessInstance(
      processDefinitionEngineDto.getId(), variables, businessKey
    );
    processInstanceDto.setProcessDefinitionKey(processDefinitionEngineDto.getKey());
    processInstanceDto.setProcessDefinitionVersion(String.valueOf(processDefinitionEngineDto.getVersion()));

    return processInstanceDto;
  }

  public HistoricProcessInstanceDto getHistoricProcessInstance(String processInstanceId) {
    HttpRequestBase get = new HttpGet(getHistoricGetProcessInstanceUri(processInstanceId));
    HistoricProcessInstanceDto processInstanceDto = new HistoricProcessInstanceDto();
    try {
      CloseableHttpResponse response = HTTP_CLIENT.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      processInstanceDto = OBJECT_MAPPER.readValue(responseString, new TypeReference<HistoricProcessInstanceDto>() {
      });
      response.close();
    } catch (IOException e) {
      log.error("Could not get process definition for process instance: " + processInstanceId, e);
    }
    return processInstanceDto;
  }

  public List<HistoricActivityInstanceEngineDto> getHistoricActivityInstances() {
    HttpRequestBase get = new HttpGet(getHistoricGetActivityInstanceUri());
    try (CloseableHttpResponse response = HTTP_CLIENT.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      return OBJECT_MAPPER.readValue(responseString, new TypeReference<List<HistoricActivityInstanceEngineDto>>() {
      });
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not fetch historic activity instances", e);
    }
  }

  public void deleteHistoricProcessInstance(String processInstanceId) {
    HttpDelete delete = new HttpDelete(getHistoricGetProcessInstanceUri(processInstanceId));
    try {
      CloseableHttpResponse response = HTTP_CLIENT.execute(delete);
      if (response.getStatusLine().getStatusCode() != 204) {
        log.error(
          "Could not delete process definition for process instance [{}]. Reason: wrong response code [{}]",
          processInstanceId,
          response.getStatusLine().getStatusCode()
        );
      }
    } catch (Exception e) {
      log.error("Could not delete process definition for process instance: " + processInstanceId, e);
    }
  }

  public List<HistoricUserTaskInstanceDto> getHistoricTaskInstances(String processInstanceId) {
    return getHistoricTaskInstances(processInstanceId, null);
  }

  public List<HistoricUserTaskInstanceDto> getHistoricTaskInstances(String processInstanceId,
                                                                    String taskDefinitionKey) {
    try {
      final URIBuilder historicGetProcessInstanceUriBuilder = new URIBuilder(getHistoricTaskInstanceUri())
        .addParameter("processInstanceId", processInstanceId);

      if (taskDefinitionKey != null) {
        historicGetProcessInstanceUriBuilder.addParameter("taskDefinitionKey", taskDefinitionKey);
      }

      final HttpRequestBase get = new HttpGet(historicGetProcessInstanceUriBuilder.build());
      try (final CloseableHttpResponse response = HTTP_CLIENT.execute(get)) {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        // @formatter:off
        final List<HistoricUserTaskInstanceDto> historicUserTaskInstanceDto = OBJECT_MAPPER.readValue(
          responseString,
          new TypeReference<List<HistoricUserTaskInstanceDto>>() {}
        );
        return historicUserTaskInstanceDto;
        // @formatter:on
      } catch (IOException e) {
        throw new OptimizeIntegrationTestException(
          "Could not get process definition for process instance: " + processInstanceId, e
        );
      }
    } catch (URISyntaxException e) {
      throw new OptimizeIntegrationTestException("Failed building task instance url", e);
    }

  }

  public void externallyTerminateProcessInstance(String processInstanceId) {
    HttpDelete delete = new HttpDelete(getGetProcessInstanceUri(processInstanceId));
    try {
      CloseableHttpResponse response = HTTP_CLIENT.execute(delete);
      if (response.getStatusLine().getStatusCode() != 204) {
        log.error(
          "Could not cancel process definition for process instance [{}]. Reason: wrong response code [{}]",
          processInstanceId,
          response.getStatusLine().getStatusCode()
        );
      }
    } catch (Exception e) {
      log.error("Could not cancel process definition for process instance: " + processInstanceId, e);
    }
  }

  public void deleteVariableInstanceForProcessInstance(String variableName, String processInstanceId) {
    HttpDelete delete = new HttpDelete(getVariableDeleteUri(variableName, processInstanceId));
    try {
      CloseableHttpResponse response = HTTP_CLIENT.execute(delete);
      if (response.getStatusLine().getStatusCode() != 204) {
        log.error(
          "Could not delete variable [{}] for process instance [{}]. Reason: wrong response code [{}]",
          variableName,
          processInstanceId,
          response.getStatusLine().getStatusCode()
        );
      }
    } catch (Exception e) {
      log.error("Could not delete variable for process instance: " + processInstanceId, e);
    }
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
    DeploymentDto deploymentDto = deployProcess(modelInstance, tenantId);
    return getProcessDefinitionEngineDto(deploymentDto);
  }

  private DeploymentDto deployProcess(BpmnModelInstance bpmnModelInstance,
                                      String tenantId) {
    String process = Bpmn.convertToString(bpmnModelInstance);
    HttpPost deploymentRequest = createDeploymentRequest(process, "test.bpmn", tenantId);
    DeploymentDto deployment = new DeploymentDto();
    try {
      CloseableHttpResponse response = EngineIntegrationRule.HTTP_CLIENT.execute(deploymentRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during deployment, " +
                                     "could not create a deployment!");
      }
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      deployment = OBJECT_MAPPER.readValue(responseString, DeploymentDto.class);
      response.close();
    } catch (IOException e) {
      log.error("Error during deployment request! Could not deploy the given process model!", e);
    }
    return deployment;
  }

  public void startDecisionInstance(String decisionDefinitionId) {
    startDecisionInstance(decisionDefinitionId, new HashMap<String, Object>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
  }

  public void startDecisionInstance(String decisionDefinitionId,
                                    Map<String, Object> variables) {
    final HttpPost post = new HttpPost(getStartDecisionInstanceUri(decisionDefinitionId));
    post.addHeader("Content-Type", "application/json");
    final Map<String, Object> requestBodyAsMap = convertVariableMap(variables);

    final String requestBodyAsJson;
    try {
      requestBodyAsJson = OBJECT_MAPPER.writeValueAsString(requestBodyAsMap);
      post.setEntity(new StringEntity(requestBodyAsJson, ContentType.APPLICATION_JSON));
      try (final CloseableHttpResponse response = HTTP_CLIENT.execute(post)) {
        if (response.getStatusLine().getStatusCode() != 200) {
          String body = "";
          if (response.getEntity() != null) {
            body = EntityUtils.toString(response.getEntity());
          }
          throw new RuntimeException(
            "Could not start the decision definition. " +
              "Request: [" + post.toString() + "]. " +
              "Response: [" + body + "]"
          );
        }
      }
    } catch (IOException e) {
      final String message = "Could not start the given decision model!";
      log.error(message, e);
      throw new RuntimeException(message, e);
    }
  }

  private HttpPost createDeploymentRequest(String process, String fileName, String tenantId) {
    HttpPost post = new HttpPost(getDeploymentUri());
    final MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder
      .create()
      .addTextBody("deployment-name", "deployment")
      .addTextBody("enable-duplicate-filtering", "false")
      .addTextBody("deployment-source", "process application")
      .addBinaryBody("data", process.getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_OCTET_STREAM, fileName);

    if (tenantId != null) {
      multipartEntityBuilder.addTextBody("tenant-id", tenantId);
    }

    final HttpEntity entity = multipartEntityBuilder.build();
    post.setEntity(entity);
    return post;
  }

  private String getDeploymentUri() {
    return getEngineUrl() + "/deployment/create";
  }

  private String getStartProcessInstanceUri(String procDefId) {
    return getEngineUrl() + "/process-definition/" + procDefId + "/start";
  }

  private String getHistoricGetProcessInstanceUri(String processInstanceId) {
    return getEngineUrl() + "/history/process-instance/" + processInstanceId;
  }

  private String getHistoricGetActivityInstanceUri() {
    return getEngineUrl() + "/history/activity-instance/";
  }

  private String getHistoricTaskInstanceUri() {
    return getEngineUrl() + "/history/task";
  }

  private String getGetProcessInstanceUri(String processInstanceId) {
    return getEngineUrl() + "/process-instance/" + processInstanceId;
  }

  private String getVariableDeleteUri(String variableName, String processInstanceId) {
    return getEngineUrl() + "/process-instance/" + processInstanceId + "/variables/" + variableName;
  }

  private String getProcessDefinitionUri() {
    return getEngineUrl() + "/process-definition";
  }

  private String getProcessDefinitionXmlUri(String processDefinitionId) {
    return getProcessDefinitionUri() + "/" + processDefinitionId + "/xml";
  }

  private String getDecisionDefinitionUri() {
    return getEngineUrl() + "/decision-definition";
  }


  private String getStartDecisionInstanceUri(final String decisionDefinitionId) {
    return getEngineUrl() + "/decision-definition/" + decisionDefinitionId + "/evaluate";
  }

  private String getCountHistoryUri() {
    return getEngineUrl() + "/history/process-instance/count";
  }

  private String getEngineUrl() {
    return IntegrationTestConfigurationUtil.getEnginesRestEndpoint() + engineName;
  }

  private String getSecuredEngineUrl() {
    return getEngineUrl().replace("/engine-rest", "/engine-rest-secure");
  }

  private ProcessDefinitionEngineDto getProcessDefinitionEngineDto(final DeploymentDto deployment) {
    List<ProcessDefinitionEngineDto> processDefinitions = getAllProcessDefinitions(
      deployment, EngineIntegrationRule.HTTP_CLIENT
    );
    assertThat("Deployment should contain only one process definition!", processDefinitions.size(), is(1));
    return processDefinitions.get(0);
  }

  private List<ProcessDefinitionEngineDto> getAllProcessDefinitions(DeploymentDto deployment,
                                                                    CloseableHttpClient client) {
    HttpRequestBase get = new HttpGet(getProcessDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("deploymentId", deployment.getId())
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    CloseableHttpResponse response = null;
    try {
      response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      return OBJECT_MAPPER.readValue(
        responseString,
        new TypeReference<List<ProcessDefinitionEngineDto>>() {
        }
      );
    } catch (IOException e) {
      String message = "Could not retrieve all process definitions!";
      log.error(message, e);
      throw new RuntimeException(message, e);
    } finally {
      if (response != null) {
        try {
          response.close();
        } catch (IOException e) {
          String message = "Could not close response!";
          log.error(message, e);
        }
      }
    }
  }

  private DecisionDefinitionEngineDto getDecisionDefinitionByDeployment(DeploymentDto deployment) {
    HttpRequestBase get = new HttpGet(getDecisionDefinitionUri());
    try {
      URI uri = new URIBuilder(get.getURI()).addParameter("deploymentId", deployment.getId()).build();
      get.setURI(uri);
      try (CloseableHttpResponse response = HTTP_CLIENT.execute(get)) {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        final List<DecisionDefinitionEngineDto> decisionDefinitionEngineDtos = OBJECT_MAPPER.readValue(
          responseString, new TypeReference<List<DecisionDefinitionEngineDto>>() {
          }
        );
        return decisionDefinitionEngineDtos.get(0);
      }
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      String message = "Could not retrieve all process definitions!";
      log.error(message, e);
      throw new RuntimeException(message, e);
    }
  }

  public ProcessInstanceEngineDto startProcessInstance(String processDefinitionId) {
    return startProcessInstance(processDefinitionId, new HashMap<>());
  }

  public ProcessInstanceEngineDto startProcessInstance(String processDefinitionId, Map<String, Object> variables) {
    return startProcessInstance(processDefinitionId, variables, "aBusinessKey");
  }

  private ProcessInstanceEngineDto startProcessInstance(String procDefId,
                                                        Map<String, Object> variables,
                                                        String businessKey) {
    HttpPost post = new HttpPost(getStartProcessInstanceUri(procDefId));
    post.addHeader("Content-Type", "application/json");
    Map<String, Object> requestBodyAsMap = convertVariableMap(variables);
    requestBodyAsMap.put("businessKey", businessKey);
    String requestBodyAsJson;
    try {
      requestBodyAsJson = OBJECT_MAPPER.writeValueAsString(requestBodyAsMap);
      post.setEntity(new StringEntity(requestBodyAsJson, ContentType.APPLICATION_JSON));
      try (CloseableHttpResponse response = EngineIntegrationRule.HTTP_CLIENT.execute(post)) {
        if (response.getStatusLine().getStatusCode() != 200) {
          String body = "";
          if (response.getEntity() != null) {
            body = EntityUtils.toString(response.getEntity());
          }
          throw new RuntimeException(
            "Could not start the process definition. " +
              "Request: [" + post.toString() + "]. " +
              "Response: [" + body + "]"
          );
        }
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        ProcessInstanceEngineDto processInstanceEngineDto = OBJECT_MAPPER.readValue(
          responseString, ProcessInstanceEngineDto.class
        );
        return processInstanceEngineDto;
      }
    } catch (IOException e) {
      String message = "Could not start the given process model!";
      log.error(message, e);
      throw new RuntimeException(message, e);
    }
  }

  private Map<String, Object> convertVariableMap(Map<String, Object> plainVariables) {
    Map<String, Object> variables = new HashMap<>();
    for (Map.Entry<String, Object> nameToValue : plainVariables.entrySet()) {
      Object value = nameToValue.getValue();
      if (value instanceof ComplexVariableDto) {
        variables.put(nameToValue.getKey(), value);
      } else {
        Map<String, Object> fields = new HashMap<>();
        fields.put("value", nameToValue.getValue());
        fields.put("type", getSimpleName(nameToValue));
        variables.put(nameToValue.getKey(), fields);
      }
    }
    Map<String, Object> variableWrapper = new HashMap<>();
    variableWrapper.put("variables", variables);
    return variableWrapper;
  }

  private String getSimpleName(Map.Entry<String, Object> nameToValue) {

    String simpleName = nameToValue.getValue().getClass().getSimpleName();
    if (nameToValue.getValue().getClass().equals(OffsetDateTime.class)) {
      simpleName = Date.class.getSimpleName();
    }
    return simpleName;
  }

  public void waitForAllProcessesToFinish() throws Exception {

    boolean done = false;
    HttpRequestBase get = new HttpGet(getCountHistoryUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("unfinished", "true")
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    int iterations = 0;
    Thread.sleep(100);
    while (!done && iterations < MAX_WAIT) {
      CloseableHttpResponse response = HTTP_CLIENT.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      HashMap<String, Object> parsed = OBJECT_MAPPER.readValue(
        responseString,
        new TypeReference<HashMap<String, Object>>() {
        }
      );
      if (!parsed.containsKey(COUNT)) {
        throw new RuntimeException("Engine could not count PIs");
      }
      if (Integer.valueOf(parsed.get(COUNT).toString()) != 0) {
        Thread.sleep(100);
        iterations = iterations + 1;
      } else {
        done = true;
      }
      response.close();
    }
  }

  public void createTenant(final String id) {
    createTenant(id, id);
  }

  public void createTenant(final String id, final String name) {
    final TenantEngineDto tenantDto = new TenantEngineDto();
    tenantDto.setId(id);
    tenantDto.setName(name);

    try {
      HttpPost httpPost = new HttpPost(getEngineUrl() + "/tenant/create");
      httpPost.addHeader("Content-Type", "application/json");
      httpPost.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(tenantDto), ContentType.APPLICATION_JSON));
      try (CloseableHttpResponse response = HTTP_CLIENT.execute(httpPost)) {
        if (response.getStatusLine().getStatusCode() != 204) {
          throw new OptimizeIntegrationTestException("Wrong status code when trying to create tenant!");
        }
      }
    } catch (Exception e) {
      throw new OptimizeIntegrationTestException("error creating tenant", e);
    }
  }

  public void updateTenant(final String id, final String name) {
    final TenantEngineDto tenantDto = new TenantEngineDto();
    tenantDto.setId(id);
    tenantDto.setName(name);

    try {
      HttpPut httpPut = new HttpPut(getEngineUrl() + "/tenant/" + id);
      httpPut.addHeader("Content-Type", "application/json");
      httpPut.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(tenantDto), ContentType.APPLICATION_JSON));
      try (CloseableHttpResponse response = HTTP_CLIENT.execute(httpPut)) {
        if (response.getStatusLine().getStatusCode() != 204) {
          throw new OptimizeIntegrationTestException("Wrong status code when trying to update tenant!");
        }
      }
    } catch (Exception e) {
      throw new OptimizeIntegrationTestException("error updating tenant", e);
    }
  }

  public void addUser(String username, String password) {
    UserDto userDto = constructDemoUserDto(username, password);
    try {
      HttpPost httpPost = new HttpPost(getEngineUrl() + "/user/create");
      httpPost.addHeader("Content-Type", "application/json");

      httpPost.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(userDto), ContentType.APPLICATION_JSON));
      CloseableHttpResponse response = HTTP_CLIENT.execute(httpPost);
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new OptimizeIntegrationTestException("Wrong status code when trying to add user!");
      }
      response.close();
    } catch (Exception e) {
      log.error("error creating user", e);
    }
  }

  @SneakyThrows
  public void unlockUser(String username) {
    final HttpUriRequest request;
    if (IntegrationTestConfigurationUtil.getEngineVersion().matches("7\\.11\\..*")) {
      request = new HttpPost(getEngineUrl() + "/user/" + username + "/unlock");
    } else {
      request = new HttpGet(getEngineUrl() + "/user/" + username + "/unlock");
    }
    request.addHeader("Content-Type", "application/json");
    try (final CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new OptimizeIntegrationTestException("Wrong status code when trying to unlock user!");
      }
    }
  }

  public void createAuthorization(AuthorizationDto authorizationDto) {
    try {
      HttpPost httpPost = new HttpPost(getEngineUrl() + "/authorization/create");
      httpPost.addHeader("Content-Type", "application/json");

      httpPost.setEntity(
        new StringEntity(OBJECT_MAPPER.writeValueAsString(authorizationDto), ContentType.APPLICATION_JSON)
      );
      CloseableHttpResponse response = HTTP_CLIENT.execute(httpPost);
      assertThat(response.getStatusLine().getStatusCode(), is(200));
      response.close();
    } catch (IOException e) {
      log.error("Could not create authorization", e);
    }
  }

  public void grantUserOptimizeAccess(String user) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(user);
    createAuthorization(authorizationDto);
  }

  public void grantAllAuthorizations(String username) {
    for (int i = 0; i < 15; i++) {
      HashMap<String, Object> values = new HashMap<>();
      values.put("type", 1);
      values.put("permissions", new String[]{"ALL"});
      values.put("userId", username);
      values.put("groupId", null);
      values.put("resourceType", i);
      values.put("resourceId", "*");

      try {

        HttpPost httpPost = new HttpPost(getEngineUrl() + "/authorization/create");
        httpPost.addHeader("Content-Type", "application/json");

        httpPost.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(values), ContentType.APPLICATION_JSON));
        CloseableHttpResponse response = HTTP_CLIENT.execute(httpPost);
        assertThat(response.getStatusLine().getStatusCode(), is(200));
        response.close();
      } catch (Exception e) {
        log.error("error creating authorization", e);
      }
    }

  }

  private UserDto constructDemoUserDto(String username, String password) {
    UserProfileDto profile = new UserProfileDto();
    profile.setEmail("foo@camunda.org");
    profile.setId(username);
    UserCredentialsDto credentials = new UserCredentialsDto();
    credentials.setPassword(password);
    UserDto userDto = new UserDto();
    userDto.setProfile(profile);
    userDto.setCredentials(credentials);
    return userDto;
  }

  public void createGroup(String id) {
    GroupDto groupDto = new GroupDto();
    groupDto.setId(id);
    groupDto.setName("anyGroupName");
    groupDto.setType("anyGroupType");

    try {
      HttpPost httpPost = new HttpPost(getEngineUrl() + "/group/create");
      httpPost.addHeader("Content-Type", "application/json");

      httpPost.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(groupDto), ContentType.APPLICATION_JSON));
      CloseableHttpResponse response = HTTP_CLIENT.execute(httpPost);
      assertThat(response.getStatusLine().getStatusCode(), is(204));
      response.close();
    } catch (Exception e) {
      log.error("error creating group", e);
    }
  }

  public void addUserToGroup(String userId, String groupId) {
    try {
      HttpPut put = new HttpPut(getEngineUrl() + "/group/" + groupId + "/members/" + userId);
      put.addHeader("Content-Type", "application/json");

      put.setEntity(new StringEntity("", ContentType.APPLICATION_JSON));
      CloseableHttpResponse response = HTTP_CLIENT.execute(put);
      assertThat(response.getStatusLine().getStatusCode(), is(204));
      response.close();
    } catch (Exception e) {
      log.error("error creating group members", e);
    }
  }


  public DecisionDefinitionEngineDto deployAndStartDecisionDefinition() {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDecisionDefinition(
      DEFAULT_DMN_DEFINITION_PATH
    );
    startDecisionInstance(
      decisionDefinitionEngineDto.getId(),
      new HashMap<String, Object>() {{
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
    startDecisionInstance(decisionDefinitionEngineDto.getId());
    return decisionDefinitionEngineDto;
  }

  public DecisionDefinitionEngineDto deployDecisionDefinitionWithTenant(String tenantId) {
    return deployDecisionDefinition(DEFAULT_DMN_DEFINITION_PATH, tenantId);
  }

  public DecisionDefinitionEngineDto deployDecisionDefinition() {
    return deployDecisionDefinition(DEFAULT_DMN_DEFINITION_PATH);
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

    DeploymentDto deploymentDto = deployDefinition(dmnModelInstance, tenantId);
    return getDecisionDefinitionByDeployment(deploymentDto);
  }

  private DeploymentDto deployDefinition(DmnModelInstance dmnModelInstance, String tenantId) {
    String decisionDefinition = Dmn.convertToString(dmnModelInstance);
    HttpPost deploymentRequest = createDeploymentRequest(decisionDefinition, "test.dmn", tenantId);
    DeploymentDto deployment = new DeploymentDto();
    try (CloseableHttpResponse response = EngineIntegrationRule.HTTP_CLIENT.execute(deploymentRequest)) {
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during deployment, could not create a deployment!");
      }
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      deployment = OBJECT_MAPPER.readValue(responseString, DeploymentDto.class);
    } catch (IOException e) {
      log.error("Error during deployment request! Could not deploy the given decisionDefinition model!", e);
    }
    return deployment;
  }

  private CloseableHttpClient createClientWithDefaultBasicAuth() {
    return createClientWithBasicAuth(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private CloseableHttpClient createClientWithBasicAuth(final String user, final String password) {
    return HttpClientBuilder.create()
      .setDefaultCredentialsProvider(getBasicCredentialsProvider(user, password)).build();
  }

  private BasicCredentialsProvider getBasicCredentialsProvider(final String user, final String password) {
    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
    return credentialsProvider;
  }
}
