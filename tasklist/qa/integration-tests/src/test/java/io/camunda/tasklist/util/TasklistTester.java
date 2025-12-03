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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.security.oauth.IdentityJwt2AuthenticationTokenConverter;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.Protocol;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class TasklistTester {

  private static final String GRAPHQL_DEFAULT_VARIABLE_FRAGMENT =
      "graphql/variableIT/full-variable-fragment.graphql";
  private static final String TASK_RESULT_PATTERN = "{id name assignee taskState completionTime}";
  private static final String COMPLETE_TASK_MUTATION_PATTERN =
      "mutation {completeTask(taskId: \"%s\", variables: %s)" + TASK_RESULT_PATTERN + "}";
  private static final String CLAIM_TASK_MUTATION_PATTERN =
      "mutation {claimTask(taskId: \"%s\")" + TASK_RESULT_PATTERN + "}";
  private static final String VARIABLE_INPUT_PATTERN = "{name: \"%s\", value: \"%s\"}";

  private final ZeebeClient zeebeClient;
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
  @Qualifier(PROCESS_INSTANCE_IS_CANCELED_CHECK)
  private TestCheck processInstanceIsCanceledCheck;

  @Autowired
  @Qualifier(PROCESS_INSTANCE_IS_COMPLETED_CHECK)
  private TestCheck processInstanceIsCompletedCheck;

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

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private NoSqlHelper noSqlHelper;

  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired(required = false)
  private IdentityJwt2AuthenticationTokenConverter jwtAuthenticationConverter;

  private GraphQLResponse graphQLResponse;

  //
  //  private boolean operationExecutorEnabled = true;
  //
  //  private Long jobKey;
  //
  public TasklistTester(
      final ZeebeClient zeebeClient, final DatabaseTestExtension elasticsearchTestRule) {
    this.zeebeClient = zeebeClient;
    databaseTestExtension = elasticsearchTestRule;
  }

  public TasklistTester(
      final ZeebeClient zeebeClient,
      final DatabaseTestExtension elasticsearchTestRule,
      final JwtDecoder jwtDecoder) {
    this(zeebeClient, elasticsearchTestRule);
    this.jwtDecoder = jwtDecoder;
  }

  //
  //  public Long getProcessInstanceKey() {
  //    return processInstanceKey;
  //  }
  //
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
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn");
    return this;
  }

  public TasklistTester createAndDeployProcess(final BpmnModelInstance process) {
    processDefinitionKey =
        ZeebeTestUtil.deployProcess(
            zeebeClient, process, process.getDefinitions().getId() + ".bpmn");
    return this;
  }

  public TasklistTester migrateProcessInstance(
      final String oldTaskMapping, final String newTaskMapping) {
    zeebeClient
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
        ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn", tenantId);
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
        zeebeClient,
        Protocol.USER_TASK_JOB_TYPE,
        TestUtil.createRandomString(10),
        numberOfTasks,
        numberOfRetries,
        null);

    return this;
  }

  public GraphQLResponse getByQueryResource(final String resource) throws IOException {
    graphQLResponse = graphQLTestTemplate.postForResource(resource);
    return graphQLResponse;
  }

  public GraphQLResponse getByQuery(final String query) {
    graphQLResponse = graphQLTestTemplate.postMultipart(query, "{}");
    return graphQLResponse;
  }

  public GraphQLResponse getTaskByQuery(final String query) {
    graphQLResponse = graphQLTestTemplate.postMultipart(query, "{}");
    return graphQLResponse;
  }

  public List<TaskDTO> getTasksByQuery(final String query) {
    graphQLResponse = graphQLTestTemplate.postMultipart(query, "{}");
    return getTasksByPath("$.data.tasks");
  }

  public GraphQLResponse getGraphTasksByQuery(final String query) {
    return graphQLTestTemplate.postMultipart(query, "{}");
  }

  public GraphQLResponse getTaskById(final String taskId) throws IOException {
    return getTaskById(taskId, GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public GraphQLResponse getTaskById(final String taskId, final String fragmentResource)
      throws IOException {
    final ObjectNode taskIdQ = objectMapper.createObjectNode().put("taskId", taskId);
    return graphQLTestTemplate.perform(
        "graphql/taskIT/get-task.graphql", taskIdQ, Collections.singletonList(fragmentResource));
  }

  public List<TaskDTO> getCreatedTasks() throws IOException {
    graphQLResponse =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-created-tasks.graphql");
    return getTasksByPath("$.data.tasks");
  }

  public List<TaskDTO> getCompletedTasks() throws IOException {
    graphQLResponse =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-completed-tasks.graphql");
    return getTasksByPath("$.data.tasks");
  }

  public GraphQLResponse getAllTasks() throws IOException {
    return getAllTasks(GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public GraphQLResponse getAllTasks(final String fragmentResource) throws IOException {
    final ObjectNode query = objectMapper.createObjectNode();
    query.putObject("query");
    return getTasksByQueryAsVariable(query, fragmentResource);
  }

  public GraphQLResponse getAllProcesses(final String search) throws IOException {
    final ObjectNode query = objectMapper.createObjectNode().put("search", search);
    return getProcessesByQuery(query);
  }

  public GraphQLResponse getAllProcessesWithBearerAuth(final String search, final String token)
      throws IOException {
    final ObjectNode query = objectMapper.createObjectNode().put("search", search);
    return getProcessesByQueryWithBearerAuth(query, token);
  }

  public GraphQLResponse getProcessesByQuery(final ObjectNode variables) throws IOException {
    graphQLResponse = graphQLTestTemplate.perform("graphql/get-processes.graphql", variables);
    return graphQLResponse;
  }

  public GraphQLResponse getProcessesByQueryWithBearerAuth(
      final ObjectNode variables, final String token) throws IOException {
    graphQLResponse =
        graphQLTestTemplate
            .withBearerAuth(token)
            .perform("graphql/get-processes.graphql", variables);
    return graphQLResponse;
  }

  public GraphQLResponse getTasksByQueryAsVariable(final ObjectNode variables) throws IOException {
    return getTasksByQueryAsVariable(variables, GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public GraphQLResponse getTasksByQueryAsVariable(
      final ObjectNode variables, final String fragmentResource) throws IOException {
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/get-tasks-by-query.graphql",
            variables,
            Collections.singletonList(fragmentResource));
    return graphQLResponse;
  }

  public GraphQLResponse getVariablesByTaskIdAndNames(final ObjectNode variables)
      throws IOException {
    return getVariablesByTaskIdAndNames(variables, GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public List<VariableDTO> getTaskVariables() throws IOException {
    assertThat(taskId).isNotNull();
    return getTaskVariablesByTaskId(taskId).getList("$.data.variables", VariableDTO.class);
  }

  public GraphQLResponse getTaskVariablesByTaskId(final String taskId) throws IOException {
    final ObjectNode variablesQ = objectMapper.createObjectNode();
    variablesQ.put("taskId", taskId).putArray("variableNames");
    return getVariablesByTaskIdAndNames(variablesQ);
  }

  public GraphQLResponse getVariablesByTaskIdAndNames(
      final ObjectNode variables, final String fragmentResource) throws IOException {
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/variableIT/get-variables.graphql",
            variables,
            Collections.singletonList(fragmentResource));
    return graphQLResponse;
  }

  public GraphQLResponse getVariableById(final String variableId) throws IOException {
    final ObjectNode variableQ = objectMapper.createObjectNode().put("variableId", variableId);
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/variableIT/get-variable.graphql",
            variableQ,
            Collections.singletonList(GRAPHQL_DEFAULT_VARIABLE_FRAGMENT));
    return graphQLResponse;
  }

  public GraphQLResponse getVariableById(final String variableId, final String fragmentResource)
      throws IOException {
    final ObjectNode variableQ = objectMapper.createObjectNode().put("variableId", variableId);
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/variableIT/get-variable.graphql",
            variableQ,
            Collections.singletonList(fragmentResource));
    return graphQLResponse;
  }

  public List<TaskDTO> getTasksByPath(final String path) {
    return graphQLResponse.getList(path, TaskDTO.class);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getByPath(final String path) {
    return graphQLResponse.get(path, Map.class);
  }

  public String get(final String path) {
    return graphQLResponse.get(path);
  }

  public GraphQLResponse getForm(final String id) throws IOException {
    final ObjectNode args = objectMapper.createObjectNode();
    args.put("id", id).put("processDefinitionId", processDefinitionKey);
    graphQLResponse = graphQLTestTemplate.perform("graphql/formIT/get-form.graphql", args);
    return graphQLResponse;
  }

  public TasklistTester claimTask(final String claimRequest) {
    getByQuery(claimRequest);
    return this;
  }

  public TasklistTester unclaimTask(final String unclaimRequest) {
    getByQuery(unclaimRequest);
    return this;
  }

  public Boolean deleteProcessInstance(final String processInstanceId) {
    final String mutation =
        String.format(
            "mutation { deleteProcessInstance(processInstanceId: \"%s\") }", processInstanceId);
    graphQLResponse = graphQLTestTemplate.postMultipart(mutation, "{}");
    return graphQLResponse.get("$.data.deleteProcessInstance", Boolean.class);
  }

  public GraphQLResponse startProcess(final String processDefinitionId) {
    final String mutation =
        String.format(
            "mutation { startProcess (processDefinitionId: \"%s\"){id}} ", processDefinitionId);
    return graphQLTestTemplate.postMultipart(mutation, "{}");
  }

  public TasklistTester deployProcess(final String... classpathResources) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, classpathResources);
    return this;
  }

  public TasklistTester deployProcessForTenant(
      final String tenantId, final String... classpathResources) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(tenantId, zeebeClient, classpathResources);
    return this;
  }

  public TasklistTester deployProcess(
      final BpmnModelInstance processModel, final String resourceName) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, processModel, resourceName);
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

  public TasklistTester startProcessInstances(
      final String bpmnProcessId, final Integer numberOfInstances, final String payload) {
    IntStream.range(0, numberOfInstances)
        .forEach(i -> startProcessInstance(bpmnProcessId, payload));
    return this;
  }

  public TasklistTester startProcessInstance(final String bpmnProcessId, final String payload) {
    processInstanceId = ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, payload);
    return this;
  }

  public TasklistTester startProcessInstance(
      final String tenantId, final String bpmnProcessId, final String payload) {
    processInstanceId =
        ZeebeTestUtil.startProcessInstance(tenantId, zeebeClient, bpmnProcessId, payload);
    return this;
  }

  //  public TasklistTester failTask(String taskName, String errorMessage) {
  //    jobKey = ZeebeTestUtil.failTask(zeebeClient, taskName, UUID.randomUUID().toString(),
  // 3,errorMessage);
  //    return this;
  //  }
  //
  //  public TasklistTester throwError(String taskName,String errorCode,String errorMessage) {
  //    ZeebeTestUtil.throwErrorInTask(zeebeClient, taskName, UUID.randomUUID().toString(), 1,
  // errorCode, errorMessage);
  //    return this;
  //  }
  //
  //  public TasklistTester incidentIsActive() {
  //    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
  //    return this;
  //  }
  //

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

  public TasklistTester processInstanceIsCanceled() {
    databaseTestExtension.processAllRecordsAndWait(
        processInstanceIsCanceledCheck, processInstanceId);
    return this;
  }

  public TasklistTester processInstanceIsCompleted() {
    databaseTestExtension.processAllRecordsAndWait(
        processInstanceIsCompletedCheck, processInstanceId);
    return this;
  }

  private void resolveTaskId(final String flowNodeBpmnId, final TaskState state) {
    try {
      final List<TaskEntity> tasks = noSqlHelper.getTask(processInstanceId, flowNodeBpmnId);
      final Optional<TaskEntity> teOptional =
          tasks.stream().filter(te -> state.equals(te.getState())).findFirst();
      if (teOptional.isPresent()) {
        taskId = teOptional.get().getId();
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

  public TasklistTester variablesExist(final String... varNames) {
    databaseTestExtension.processAllRecordsAndWait(variablesExist, processInstanceId, varNames);
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
    claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

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

    final String variablesAsString =
        createVariablesList(variables).stream()
            .map(this::variableAsGraphqlInput)
            .collect(Collectors.joining(", ", "[", "]"));
    getByQuery(String.format(COMPLETE_TASK_MUTATION_PATTERN, taskId, variablesAsString));

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
    zeebeClient.newUserTaskCompleteCommand(Long.valueOf(taskId)).variables(variables).send();
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
        zeebeClient, Protocol.USER_TASK_JOB_TYPE, TestUtil.createRandomString(10), null);
    return this;
  }

  public TasklistTester cancelProcessInstance() {
    ZeebeTestUtil.cancelProcessInstance(zeebeClient, Long.parseLong(processInstanceId));
    return this;
  }

  public TasklistTester deleteResource(final String resourceKey) {
    ZeebeTestUtil.deleteResource(zeebeClient, Long.valueOf(resourceKey));
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
