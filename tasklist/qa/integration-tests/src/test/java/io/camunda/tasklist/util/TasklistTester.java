/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TestCheck.*;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
  @Qualifier(FORM_EXISTS_CHECK)
  private TestCheck formExistsCheck;

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

  public TasklistTester deployProcess(final String... classpathResources) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(camundaClient, classpathResources);
    return this;
  }

  public TasklistTester deployProcess(
      final BpmnModelInstance processModel, final String resourceName) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(camundaClient, processModel, resourceName);
    return this;
  }

  public TasklistTester processIsDeployed() {
    databaseTestExtension.waitFor(processIsDeployedCheck, processDefinitionKey);
    return this;
  }

  public TasklistTester startProcessInstance(final String bpmnProcessId) {
    return startProcessInstance(bpmnProcessId, null);
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
    databaseTestExtension.waitFor(taskIsCreatedCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
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
}
