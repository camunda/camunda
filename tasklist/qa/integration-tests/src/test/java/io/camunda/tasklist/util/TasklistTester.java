/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TestCheck.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskCompleteRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.oauth.IdentityJwt2AuthenticationTokenConverter;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.Protocol;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import javax.annotation.PostConstruct;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Component
@Scope(SCOPE_PROTOTYPE)
public class TasklistTester {

  private static final String REST_SEARCH_ENDPOINT = TasklistURIs.TASKS_URL_V1.concat("/search");
  private static final String REST_GET_TASK = TasklistURIs.TASKS_URL_V1.concat("/{taskId}");
  private static final String REST_SEARCH_TASK_VARIABLES =
      TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search");
  private static final String REST_ASSIGN_ENDPOINT =
      TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign");
  private static final String REST_UNASSIGN_ENDPOINT =
      TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign");
  private static final String REST_COMPLETE_ENDPOINT =
      TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete");

  private static final String VARIABLE_INPUT_PATTERN = "{name: \"%s\", value: \"%s\"}";

  private final CamundaClient camundaClient;
  private final DatabaseTestExtension databaseTestExtension;
  private JwtDecoder jwtDecoder;
  //
  private String processDefinitionKey;
  private String processInstanceId;
  private String taskId;

  @Autowired
  @Qualifier(PROCESS_IS_DEPLOYED_CHECK)
  private TestCheck processIsDeployedCheck;

  @Autowired
  @Qualifier(PROCESS_IS_DELETED_CHECK)
  private TestCheck processIsDeletedCheck;

  @Autowired
  @Qualifier(TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCreatedCheck;

  @Autowired
  @Qualifier(TASK_HAS_CANDIDATE_USERS)
  private TestCheck taskHasCandidateUsers;

  @Autowired
  @Qualifier(TASKS_ARE_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck tasksAreCreatedCheck;

  @Autowired
  @Qualifier(TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCanceledCheck;

  @Autowired
  @Qualifier(TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCompletedCheck;

  @Autowired
  @Qualifier(TASK_IS_ASSIGNED_CHECK)
  private TestCheck taskIsAssignedCheck;

  @Autowired
  @Qualifier(TASK_VARIABLE_EXISTS_CHECK)
  private TestCheck taskVariableExists;

  @Autowired
  @Qualifier(VARIABLES_EXIST_CHECK)
  private TestCheck variablesExist;

  @Autowired private NoSqlHelper noSqlHelper;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WebApplicationContext context;

  @Autowired(required = false)
  private IdentityJwt2AuthenticationTokenConverter jwtAuthenticationConverter;

  private MockMvcHelper mockMvcHelper;

  public TasklistTester(
      final CamundaClient camundaClient, final DatabaseTestExtension elasticsearchTestRule) {
    this.camundaClient = camundaClient;
    databaseTestExtension = elasticsearchTestRule;
  }

  public TasklistTester(
      final CamundaClient camundaClient,
      final DatabaseTestExtension elasticsearchTestRule,
      final JwtDecoder jwtDecoder) {
    this(camundaClient, elasticsearchTestRule);
    this.jwtDecoder = jwtDecoder;
  }

  @PostConstruct
  public void init() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @SafeVarargs
  public final TasklistTester createAndDeploySimpleProcess(
      final String processId,
      final String flowNodeBpmnId,
      final Consumer<UserTaskBuilder>... taskModifiers) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(
                flowNodeBpmnId,
                task -> Arrays.stream(taskModifiers).forEach(modifier -> modifier.accept(task)))
            .endEvent()
            .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(camundaClient, process, processId + ".bpmn");
    return this;
  }

  public TasklistTester createAndDeployProcess(final BpmnModelInstance process) {
    processDefinitionKey =
        ZeebeTestUtil.deployProcess(
            camundaClient, process, process.getDefinitions().getId() + ".bpmn");
    return this;
  }

  public TasklistTester migrateProcessInstance(
      final String oldTaskMapping, final String newTaskMapping) {
    camundaClient
        .newMigrateProcessInstanceCommand(Long.valueOf(processInstanceId))
        .migrationPlan(
            new MigrationPlanBuilderImpl()
                .withTargetProcessDefinitionKey(Long.valueOf(processDefinitionKey))
                .addMappingInstruction(oldTaskMapping, newTaskMapping)
                .build())
        .send()
        .join();
    return this;
  }

  public TasklistTester createAndDeploySimpleProcess(
      final String processId, final String flowNodeBpmnId, final String tenantId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .endEvent()
            .done();
    processDefinitionKey =
        ZeebeTestUtil.deployProcess(camundaClient, process, processId + ".bpmn", tenantId);
    return this;
  }

  public TasklistTester createCreatedAndCompletedTasks(
      final String processId, final String flowNodeBpmnId, final int created, final int completed) {
    createAndDeploySimpleProcess(processId, flowNodeBpmnId).waitUntil().processIsDeployed();
    // complete tasks
    for (int i = 0; i < completed; i++) {
      startProcessInstance(processId)
          .waitUntil()
          .taskIsCreated(flowNodeBpmnId)
          .and()
          .claimAndCompleteHumanTask(flowNodeBpmnId);
    }
    // start more process instances
    for (int i = 0; i < created; i++) {
      startProcessInstance(processId).waitUntil().taskIsCreated(flowNodeBpmnId);
    }
    return this;
  }

  public TasklistTester createFailedTasks(
      final String processId,
      final String flowNodeBpmnId,
      final int numberOfTasks,
      final int numberOfRetries)
      throws IOException {
    createAndDeploySimpleProcess(processId, flowNodeBpmnId).waitUntil().processIsDeployed();

    for (int i = 0; i < numberOfTasks; i++) {
      startProcessInstance(processId).waitUntil().taskIsCreated(flowNodeBpmnId);
    }

    ZeebeTestUtil.failTaskWithRetries(
        camundaClient,
        Protocol.USER_TASK_JOB_TYPE,
        TestUtil.createRandomString(10),
        numberOfTasks,
        numberOfRetries,
        null);

    return this;
  }

  public List<TaskSearchResponse> getCreatedTasks() throws IOException {
    final var searchRequest = new TaskSearchRequest().setState(TaskState.CREATED);

    final var result = mockMvcHelper.doRequest(post(REST_SEARCH_ENDPOINT), searchRequest);

    return objectMapper.readValue(
        result.getContentAsString(), new TypeReference<List<TaskSearchResponse>>() {});
  }

  public List<TaskSearchResponse> getCompletedTasks() throws IOException {
    final var searchRequest = new TaskSearchRequest().setState(TaskState.COMPLETED);

    final var result = mockMvcHelper.doRequest(post(REST_SEARCH_ENDPOINT), searchRequest);

    return objectMapper.readValue(
        result.getContentAsString(), new TypeReference<List<TaskSearchResponse>>() {});
  }

  public List<TaskSearchResponse> getAllTasks() throws IOException {
    final var searchRequest = new TaskSearchRequest();

    final var result = mockMvcHelper.doRequest(post(REST_SEARCH_ENDPOINT), searchRequest);

    return objectMapper.readValue(
        result.getContentAsString(), new TypeReference<List<TaskSearchResponse>>() {});
  }

  public TaskResponse getTaskById(final String taskId) throws IOException {
    final var result = mockMvcHelper.doRequest(get(REST_GET_TASK, taskId));
    return objectMapper.readValue(result.getContentAsString(), TaskResponse.class);
  }

  public List<VariableSearchResponse> getTaskVariables() throws IOException {
    assertThat(taskId).isNotNull();

    return getTaskVariablesByTaskId(taskId);
  }

  public List<VariableSearchResponse> getTaskVariablesByTaskId(final String taskId)
      throws IOException {
    final var result = mockMvcHelper.doRequest(post(REST_SEARCH_TASK_VARIABLES, taskId));
    return objectMapper.readValue(
        result.getContentAsString(), new TypeReference<List<VariableSearchResponse>>() {});
  }

  public TasklistTester assignTask(final String taskKey) {
    mockMvcHelper.doRequest(patch(REST_ASSIGN_ENDPOINT, taskKey));
    return this;
  }

  public TasklistTester unassignTask(final String taskKey) {
    mockMvcHelper.doRequest(patch(REST_UNASSIGN_ENDPOINT, taskKey));
    return this;
  }

  public TasklistTester completeTask(final String taskKey, final List<VariableInputDTO> variables) {
    final TaskCompleteRequest completeRequest = new TaskCompleteRequest();
    completeRequest.setVariables(variables);
    final var result =
        mockMvcHelper.doRequest(patch(REST_COMPLETE_ENDPOINT, taskId), completeRequest);
    return this;
  }

  public TasklistTester deployProcess(final String... classpathResources) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(camundaClient, classpathResources);
    return this;
  }

  public TasklistTester deployProcessForTenant(
      final String tenantId, final String... classpathResources) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(tenantId, camundaClient, classpathResources);
    return this;
  }

  public TasklistTester deployProcess(
      final BpmnModelInstance processModel, final String resourceName) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(camundaClient, processModel, resourceName);
    return this;
  }

  public TasklistTester processIsDeployed() {
    databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    return this;
  }

  public TasklistTester processIsDeleted() {
    databaseTestExtension.processAllRecordsAndWait(processIsDeletedCheck, processDefinitionKey);
    return this;
  }

  public TasklistTester startProcessInstance(final String bpmnProcessId) {
    return startProcessInstance(bpmnProcessId, null);
  }

  public TasklistTester startProcessInstances(
      final String bpmnProcessId, final Integer numberOfInstances) {
    IntStream.range(0, numberOfInstances).forEach(i -> startProcessInstance(bpmnProcessId));
    return this;
  }

  public TasklistTester startProcessInstance(final String bpmnProcessId, final String payload) {
    processInstanceId = ZeebeTestUtil.startProcessInstance(camundaClient, bpmnProcessId, payload);
    return this;
  }

  public TasklistTester startProcessInstance(
      final String tenantId, final String bpmnProcessId, final String payload) {
    processInstanceId =
        ZeebeTestUtil.startProcessInstance(tenantId, camundaClient, bpmnProcessId, payload);
    return this;
  }

  public TasklistTester taskIsCreated(final String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskIsCreatedCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester taskHasCandidateUsers(final String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskHasCandidateUsers, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester tasksAreCreated(final String flowNodeBpmnId, final int taskCount) {
    databaseTestExtension.processAllRecordsAndWait(tasksAreCreatedCheck, flowNodeBpmnId, taskCount);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester taskIsCanceled(final String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskIsCanceledCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CANCELED);
    return this;
  }

  public TasklistTester taskIsFailed(final String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskIsCanceledCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.FAILED);
    return this;
  }

  private void resolveTaskId(final String flowNodeBpmnId, final TaskState state) {
    try {
      final List<TaskEntity> tasks = noSqlHelper.getTask(processInstanceId, flowNodeBpmnId);
      final Optional<TaskEntity> teOptional =
          tasks.stream().filter(te -> state.equals(te.getState())).findFirst();
      if (teOptional.isPresent()) {
        taskId = String.valueOf(teOptional.get().getKey());
      } else {
        taskId = null;
      }
    } catch (final Exception ex) {
      taskId = null;
    }
  }

  public TasklistTester taskIsCompleted(final String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskIsCompletedCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.COMPLETED);
    return this;
  }

  public TasklistTester taskIsAssigned(final String taskId) {
    databaseTestExtension.processAllRecordsAndWait(taskIsAssignedCheck, taskId);
    return this;
  }

  public TasklistTester taskVariableExists(final String varName) {
    databaseTestExtension.processAllRecordsAndWait(taskVariableExists, taskId, varName);
    return this;
  }

  public TasklistTester variablesExist(final String[] varNames) {
    databaseTestExtension.processAllRecordsAndWait(variablesExist, varNames);
    return this;
  }

  public TasklistTester claimAndCompleteHumanTask(
      final String flowNodeBpmnId, final String... variables) {
    claimHumanTask(flowNodeBpmnId);

    return completeHumanTask(flowNodeBpmnId, variables);
  }

  public TasklistTester claimHumanTask(final String flowNodeBpmnId) {
    // resolve taskId, if not yet resolved
    if (taskId == null) {
      resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
      if (taskId == null) {
        fail(
            String.format(
                "Cannot resolveTaskId for flowNodeBpmnId=%s processDefinitionKey=%s state=%s",
                flowNodeBpmnId, processDefinitionKey, TaskState.CREATED));
      }
    }
    assignTask(taskId);

    taskIsAssigned(taskId);

    return this;
  }

  public TasklistTester completeHumanTask(final String flowNodeBpmnId, final String... variables) {
    // resolve taskId, if not yet resolved
    if (taskId == null) {
      resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
      if (taskId == null) {
        fail(
            String.format(
                "Cannot resolveTaskId for flowNodeBpmnId=%s processDefinitionKey=%s state=%s",
                flowNodeBpmnId, processDefinitionKey, TaskState.CREATED));
      }
    }

    final List<VariableInputDTO> variablesInput = createVariablesList(variables);
    completeTask(taskId, variablesInput);

    return taskIsCompleted(flowNodeBpmnId);
  }

  public TasklistTester completeZeebeUserTask(
      final String flowNodeBpmnId, final Map<String, Object> variables) {
    // resolve taskId, if not yet resolved
    if (taskId == null) {
      resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
      if (taskId == null) {
        fail(
            String.format(
                "Cannot resolveTaskId for flowNodeBpmnId=%s processDefinitionKey=%s state=%s",
                flowNodeBpmnId, processDefinitionKey, TaskState.CREATED));
      }
    }
    camundaClient.newUserTaskCompleteCommand(Long.valueOf(taskId)).variables(variables).send();
    return taskIsCompleted(flowNodeBpmnId);
  }

  private List<VariableInputDTO> createVariablesList(final String... variables) {
    assertThat(variables.length % 2).isEqualTo(0);
    final List<VariableInputDTO> result = new ArrayList<>();
    for (int i = 0; i < variables.length; i = i + 2) {
      result.add(new VariableInputDTO().setName(variables[i]).setValue(variables[i + 1]));
    }
    return result;
  }

  private String variableAsGraphqlInput(final VariableInputDTO variable) {
    return String.format(
        VARIABLE_INPUT_PATTERN,
        variable.getName(),
        StringEscapeUtils.escapeJson(variable.getValue()));
  }

  public TasklistTester completeUserTaskInZeebe() {
    ZeebeTestUtil.completeTask(
        camundaClient, Protocol.USER_TASK_JOB_TYPE, TestUtil.createRandomString(10), null);
    return this;
  }

  public TasklistTester cancelProcessInstance() {
    ZeebeTestUtil.cancelProcessInstance(camundaClient, Long.parseLong(processInstanceId));
    return this;
  }

  public TasklistTester deleteResource(final String resourceKey) {
    ZeebeTestUtil.deleteResource(camundaClient, Long.valueOf(resourceKey));
    return this;
  }

  public TasklistTester and() {
    return this;
  }

  public TasklistTester then() {
    return this;
  }

  public TasklistTester having() {
    return this;
  }

  public TasklistTester when() {
    return this;
  }

  public TasklistTester waitUntil() {
    return this;
  }

  public TasklistTester waitFor(final long milliseconds) {
    ThreadUtil.sleepFor(milliseconds);
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public String getTaskId() {
    return taskId;
  }

  public TasklistTester withAuthenticationToken(final String token) {
    final Jwt jwt;
    try {
      jwt = jwtDecoder.decode(token);
    } catch (final JwtException e) {
      throw new RuntimeException(e);
    }
    SecurityContextHolder.getContext().setAuthentication(jwtAuthenticationConverter.convert(jwt));
    return this;
  }

  public TasklistTester unsetAuthorization() {
    SecurityContextHolder.getContext().setAuthentication(null);
    return this;
  }
}
