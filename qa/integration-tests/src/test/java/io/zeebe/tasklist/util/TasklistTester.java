/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static io.zeebe.tasklist.util.ElasticsearchChecks.TASKS_ARE_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_ASSIGNED_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.WORKFLOW_INSTANCE_IS_CANCELED_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.WORKFLOW_INSTANCE_IS_COMPLETED_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.WORKFLOW_IS_DEPLOYED_CHECK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.ElasticsearchChecks.TestCheck;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.VariableDTO;
import io.zeebe.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import java.io.IOException;
import java.util.ArrayList;
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

  private ZeebeClient zeebeClient;
  private ElasticsearchTestRule elasticsearchTestRule;
  //
  private String workflowId;
  private String workflowInstanceId;
  private String taskId;

  @Autowired
  @Qualifier(WORKFLOW_IS_DEPLOYED_CHECK)
  private TestCheck workflowIsDeployedCheck;

  @Autowired
  @Qualifier(WORKFLOW_INSTANCE_IS_CANCELED_CHECK)
  private TestCheck workflowInstanceIsCanceledCheck;

  @Autowired
  @Qualifier(WORKFLOW_INSTANCE_IS_COMPLETED_CHECK)
  private TestCheck workflowInstanceIsCompletedCheck;

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
  //  public Long getWorkflowInstanceKey() {
  //    return workflowInstanceKey;
  //  }
  //
  public TasklistTester createAndDeploySimpleWorkflow(String processId, String flowNodeBpmnId) {
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(flowNodeBpmnId)
            .zeebeJobType(tasklistProperties.getImporter().getJobType())
            .endEvent()
            .done();
    workflowId = ZeebeTestUtil.deployWorkflow(zeebeClient, workflow, processId + ".bpmn");
    return this;
  }

  public TasklistTester createCreatedAndCompletedTasks(
      String processId, String flowNodeBpmnId, int created, int completed) {
    createAndDeploySimpleWorkflow(processId, flowNodeBpmnId).waitUntil().workflowIsDeployed();
    // complete tasks
    for (int i = 0; i < completed; i++) {
      startWorkflowInstance(processId)
          .waitUntil()
          .taskIsCreated(flowNodeBpmnId)
          .and()
          .claimAndCompleteHumanTask(flowNodeBpmnId);
    }
    // start more workflow instances
    for (int i = 0; i < created; i++) {
      startWorkflowInstance(processId).waitUntil().taskIsCreated(flowNodeBpmnId);
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
    final ObjectNode query = objectMapper.createObjectNode();
    query.putObject("query");
    return this.getTasksByQueryAsVariable(query);
  }

  public GraphQLResponse getTasksByQueryAsVariable(ObjectNode variables) throws IOException {
    graphQLResponse = graphQLTestTemplate.perform("graphql/get-tasks-by-query.graphql", variables);
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

  public TasklistTester claimTask(String claimRequest) {
    getByQuery(claimRequest);
    return this;
  }

  public TasklistTester unclaimTask(String unclaimRequest) {
    getByQuery(unclaimRequest);
    return this;
  }

  public TasklistTester deployWorkflow(String... classpathResources) {
    workflowId = ZeebeTestUtil.deployWorkflow(zeebeClient, classpathResources);
    return this;
  }

  public TasklistTester deployWorkflow(BpmnModelInstance workflowModel, String resourceName) {
    workflowId = ZeebeTestUtil.deployWorkflow(zeebeClient, workflowModel, resourceName);
    return this;
  }

  public TasklistTester workflowIsDeployed() {
    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, workflowId);
    return this;
  }

  public TasklistTester startWorkflowInstance(String bpmnProcessId) {
    return startWorkflowInstance(bpmnProcessId, null);
  }

  public TasklistTester startWorkflowInstances(String bpmnProcessId, Integer numberOfInstances) {
    IntStream.range(0, numberOfInstances).forEach(i -> startWorkflowInstance(bpmnProcessId));
    return this;
  }

  public TasklistTester startWorkflowInstance(String bpmnProcessId, String payload) {
    workflowInstanceId = ZeebeTestUtil.startWorkflowInstance(zeebeClient, bpmnProcessId, payload);
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
  //    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);
  //    return this;
  //  }
  //

  public TasklistTester taskIsCreated(String flowNodeBpmnId) {
    elasticsearchTestRule.processAllRecordsAndWait(
        taskIsCreatedCheck, workflowInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester tasksAreCreated(String flowNodeBpmnId, int taskCount) {
    elasticsearchTestRule.processAllRecordsAndWait(
        tasksAreCreatedCheck, workflowInstanceId, flowNodeBpmnId, taskCount);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester taskIsCanceled(String flowNodeBpmnId) {
    elasticsearchTestRule.processAllRecordsAndWait(
        taskIsCanceledCheck, workflowInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CANCELED);
    return this;
  }

  public TasklistTester workflowInstanceIsCanceled() {
    elasticsearchTestRule.processAllRecordsAndWait(
        workflowInstanceIsCanceledCheck, workflowInstanceId);
    return this;
  }

  public TasklistTester workflowInstanceIsCompleted() {
    elasticsearchTestRule.processAllRecordsAndWait(
        workflowInstanceIsCompletedCheck, workflowInstanceId);
    return this;
  }

  private void resolveTaskId(final String flowNodeBpmnId, final TaskState state) {
    try {
      final List<TaskEntity> tasks =
          elasticsearchHelper.getTask(workflowInstanceId, flowNodeBpmnId);
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
        taskIsCompletedCheck, workflowInstanceId, flowNodeBpmnId);
    return this;
  }

  public TasklistTester taskIsAssigned(String taskId) {
    elasticsearchTestRule.processAllRecordsAndWait(taskIsAssignedCheck, taskId);
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
    taskMutationResolver.claimTask(taskId);

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

  private List<VariableDTO> createVariablesList(String... variables) {
    assertThat(variables.length % 2).isEqualTo(0);
    final List<VariableDTO> result = new ArrayList<>();
    for (int i = 0; i < variables.length; i = i + 2) {
      result.add(new VariableDTO().setName(variables[i]).setValue(variables[i + 1]));
    }
    return result;
  }

  public TasklistTester completeServiceTask(String jobType) {
    ZeebeTestUtil.completeTask(zeebeClient, jobType, TestUtil.createRandomString(10), null);
    return this;
  }

  public TasklistTester cancelWorkflowInstance() {
    ZeebeTestUtil.cancelWorkflowInstance(zeebeClient, Long.parseLong(workflowInstanceId));
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

  public String getWorkflowId() {
    return workflowId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public String getTaskId() {
    return taskId;
  }
}
