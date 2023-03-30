/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ElasticsearchChecks.PROCESS_INSTANCE_IS_CANCELED_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.PROCESS_INSTANCE_IS_COMPLETED_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.PROCESS_IS_DEPLOYED_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.TASKS_ARE_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.TASK_IS_ASSIGNED_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.TASK_VARIABLE_EXISTS_CHECK;
import static io.camunda.tasklist.util.ElasticsearchChecks.VARIABLES_EXIST_CHECK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.ElasticsearchChecks.TestCheck;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class TasklistTester {

  private static final String GRAPHQL_DEFAULT_VARIABLE_FRAGMENT =
      "graphql/variableIT/full-variable-fragment.graphql";

  private ZeebeClient zeebeClient;
  private ElasticsearchTestRule elasticsearchTestRule;
  //
  private String processDefinitionKey;
  private String processInstanceId;
  private String taskId;

  @Autowired
  @Qualifier(PROCESS_IS_DEPLOYED_CHECK)
  private TestCheck processIsDeployedCheck;

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

  @Autowired private ElasticsearchHelper elasticsearchHelper;

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @Autowired private ObjectMapper objectMapper;

  private GraphQLResponse graphQLResponse;

  //
  //  private boolean operationExecutorEnabled = true;
  //
  //  private Long jobKey;
  //
  public TasklistTester(ZeebeClient zeebeClient, ElasticsearchTestRule elasticsearchTestRule) {
    this.zeebeClient = zeebeClient;
    this.elasticsearchTestRule = elasticsearchTestRule;
  }

  //
  //  public Long getProcessInstanceKey() {
  //    return processInstanceKey;
  //  }
  //
  public TasklistTester createAndDeploySimpleProcess(String processId, String flowNodeBpmnId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .endEvent()
            .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn");
    return this;
  }

  public TasklistTester createCreatedAndCompletedTasks(
      String processId, String flowNodeBpmnId, int created, int completed) {
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

  public GraphQLResponse getByQueryResource(String resource) throws IOException {
    graphQLResponse = graphQLTestTemplate.postForResource(resource);
    return graphQLResponse;
  }

  public GraphQLResponse getByQuery(String query) {
    graphQLResponse = graphQLTestTemplate.postMultipart(query, "{}");
    return graphQLResponse;
  }

  public GraphQLResponse getTaskByQuery(String query) {
    graphQLResponse = graphQLTestTemplate.postMultipart(query, "{}");
    return graphQLResponse;
  }

  public List<TaskDTO> getTasksByQuery(String query) {
    graphQLResponse = graphQLTestTemplate.postMultipart(query, "{}");
    return getTasksByPath("$.data.tasks");
  }

  public GraphQLResponse getGraphTasksByQuery(String query) {
    return graphQLTestTemplate.postMultipart(query, "{}");
  }

  public GraphQLResponse getTaskById(String taskId) throws IOException {
    return getTaskById(taskId, GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public GraphQLResponse getTaskById(String taskId, String fragmentResource) throws IOException {
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

  public GraphQLResponse getAllTasks(String fragmentResource) throws IOException {
    final ObjectNode query = objectMapper.createObjectNode();
    query.putObject("query");
    return this.getTasksByQueryAsVariable(query, fragmentResource);
  }

  public GraphQLResponse getAllProcesses(String search) throws IOException {
    final ObjectNode query = objectMapper.createObjectNode().put("search", search);
    return this.getProcessesByQuery(query);
  }

  public GraphQLResponse getProcessesByQuery(ObjectNode variables) throws IOException {
    graphQLResponse = graphQLTestTemplate.perform("graphql/get-processes.graphql", variables);
    return graphQLResponse;
  }

  public GraphQLResponse getTasksByQueryAsVariable(ObjectNode variables) throws IOException {
    return getTasksByQueryAsVariable(variables, GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public GraphQLResponse getTasksByQueryAsVariable(ObjectNode variables, String fragmentResource)
      throws IOException {
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/get-tasks-by-query.graphql",
            variables,
            Collections.singletonList(fragmentResource));
    return graphQLResponse;
  }

  public GraphQLResponse getVariablesByTaskIdAndNames(ObjectNode variables) throws IOException {
    return getVariablesByTaskIdAndNames(variables, GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public List<VariableDTO> getTaskVariables() throws IOException {
    assertThat(this.taskId).isNotNull();
    return getTaskVariablesByTaskId(this.taskId).getList("$.data.variables", VariableDTO.class);
  }

  public GraphQLResponse getTaskVariablesByTaskId(String taskId) throws IOException {
    final ObjectNode variablesQ = objectMapper.createObjectNode();
    variablesQ.put("taskId", taskId).putArray("variableNames");
    return getVariablesByTaskIdAndNames(variablesQ);
  }

  public GraphQLResponse getVariablesByTaskIdAndNames(ObjectNode variables, String fragmentResource)
      throws IOException {
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/variableIT/get-variables.graphql",
            variables,
            Collections.singletonList(fragmentResource));
    return graphQLResponse;
  }

  public GraphQLResponse getVariableById(String variableId) throws IOException {
    final ObjectNode variableQ = objectMapper.createObjectNode().put("variableId", variableId);
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/variableIT/get-variable.graphql",
            variableQ,
            Collections.singletonList(GRAPHQL_DEFAULT_VARIABLE_FRAGMENT));
    return graphQLResponse;
  }

  public GraphQLResponse getVariableById(String variableId, String fragmentResource)
      throws IOException {
    final ObjectNode variableQ = objectMapper.createObjectNode().put("variableId", variableId);
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/variableIT/get-variable.graphql",
            variableQ,
            Collections.singletonList(fragmentResource));
    return graphQLResponse;
  }

  public List<TaskDTO> getTasksByPath(String path) {
    return graphQLResponse.getList(path, TaskDTO.class);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getByPath(String path) {
    return graphQLResponse.get(path, Map.class);
  }

  public String get(String path) {
    return graphQLResponse.get(path);
  }

  public GraphQLResponse getForm(String id) throws IOException {
    final ObjectNode args = objectMapper.createObjectNode();
    args.put("id", id).put("processDefinitionId", processDefinitionKey);
    graphQLResponse = graphQLTestTemplate.perform("graphql/formIT/get-form.graphql", args);
    return graphQLResponse;
  }

  public TasklistTester claimTask(String claimRequest) {
    getByQuery(claimRequest);
    return this;
  }

  public TasklistTester unclaimTask(String unclaimRequest) {
    getByQuery(unclaimRequest);
    return this;
  }

  public Boolean deleteProcessInstance(String processInstanceId) {
    final String mutation =
        String.format(
            "mutation { deleteProcessInstance(processInstanceId: \"%s\") }", processInstanceId);
    graphQLResponse = graphQLTestTemplate.postMultipart(mutation, "{}");
    return graphQLResponse.get("$.data.deleteProcessInstance", Boolean.class);
  }

  public GraphQLResponse startProcess(String processDefinitionId) {
    final String mutation =
        String.format(
            "mutation { startProcess (processDefinitionId: \"%s\"){id}} ", processDefinitionId);
    return graphQLTestTemplate.postMultipart(mutation, "{}");
  }

  public TasklistTester deployProcess(String... classpathResources) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, classpathResources);
    return this;
  }

  public TasklistTester deployProcess(BpmnModelInstance processModel, String resourceName) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, processModel, resourceName);
    return this;
  }

  public TasklistTester processIsDeployed() {
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    return this;
  }

  public TasklistTester startProcessInstance(String bpmnProcessId) {
    return startProcessInstance(bpmnProcessId, null);
  }

  public TasklistTester startProcessInstances(String bpmnProcessId, Integer numberOfInstances) {
    IntStream.range(0, numberOfInstances).forEach(i -> startProcessInstance(bpmnProcessId));
    return this;
  }

  public TasklistTester startProcessInstance(String bpmnProcessId, String payload) {
    processInstanceId = ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, payload);
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

  public TasklistTester taskIsCreated(String flowNodeBpmnId) {
    elasticsearchTestRule.processAllRecordsAndWait(
        taskIsCreatedCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester tasksAreCreated(String flowNodeBpmnId, int taskCount) {
    elasticsearchTestRule.processAllRecordsAndWait(tasksAreCreatedCheck, flowNodeBpmnId, taskCount);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester taskIsCanceled(String flowNodeBpmnId) {
    elasticsearchTestRule.processAllRecordsAndWait(
        taskIsCanceledCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CANCELED);
    return this;
  }

  public TasklistTester processInstanceIsCanceled() {
    elasticsearchTestRule.processAllRecordsAndWait(
        processInstanceIsCanceledCheck, processInstanceId);
    return this;
  }

  public TasklistTester processInstanceIsCompleted() {
    elasticsearchTestRule.processAllRecordsAndWait(
        processInstanceIsCompletedCheck, processInstanceId);
    return this;
  }

  private void resolveTaskId(final String flowNodeBpmnId, final TaskState state) {
    try {
      final List<TaskEntity> tasks = elasticsearchHelper.getTask(processInstanceId, flowNodeBpmnId);
      final Optional<TaskEntity> teOptional =
          tasks.stream().filter(te -> state.equals(te.getState())).findFirst();
      if (teOptional.isPresent()) {
        taskId = teOptional.get().getId();
      } else {
        taskId = null;
      }
    } catch (Exception ex) {
      taskId = null;
    }
  }

  public TasklistTester taskIsCompleted(String flowNodeBpmnId) {
    elasticsearchTestRule.processAllRecordsAndWait(
        taskIsCompletedCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.COMPLETED);
    return this;
  }

  public TasklistTester taskIsAssigned(String taskId) {
    elasticsearchTestRule.processAllRecordsAndWait(taskIsAssignedCheck, taskId);
    return this;
  }

  public TasklistTester taskVariableExists(String varName) {
    elasticsearchTestRule.processAllRecordsAndWait(taskVariableExists, taskId, varName);
    return this;
  }

  public TasklistTester variablesExist(String[] varNames) {
    elasticsearchTestRule.processAllRecordsAndWait(variablesExist, varNames);
    return this;
  }

  public TasklistTester claimAndCompleteHumanTask(String flowNodeBpmnId, String... variables) {
    claimHumanTask(flowNodeBpmnId);

    return completeHumanTask(flowNodeBpmnId, variables);
  }

  public TasklistTester claimHumanTask(final String flowNodeBpmnId) {
    // resolve taskId, if not yet resolved
    if (taskId == null) {
      resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    }
    taskMutationResolver.claimTask(taskId, null, null);

    taskIsAssigned(taskId);

    return this;
  }

  public TasklistTester completeHumanTask(String flowNodeBpmnId, String... variables) {
    // resolve taskId, if not yet resolved
    if (taskId == null) {
      resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    }

    taskMutationResolver.completeTask(taskId, createVariablesList(variables));

    return taskIsCompleted(flowNodeBpmnId);
  }

  private List<VariableInputDTO> createVariablesList(String... variables) {
    assertThat(variables.length % 2).isEqualTo(0);
    final List<VariableInputDTO> result = new ArrayList<>();
    for (int i = 0; i < variables.length; i = i + 2) {
      result.add(new VariableInputDTO().setName(variables[i]).setValue(variables[i + 1]));
    }
    return result;
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

  public TasklistTester waitFor(long milliseconds) {
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
