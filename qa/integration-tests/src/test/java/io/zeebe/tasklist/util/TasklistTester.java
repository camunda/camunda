/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_ASSIGNED_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.WORKFLOW_IS_DEPLOYED_CHECK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class TasklistTester {

  //  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistTester.class);
  //
  //  @Autowired
  //  private BeanFactory beanFactory;
  //
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
  @Qualifier(TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCreatedCheck;

  @Autowired
  @Qualifier(TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCompletedCheck;

  @Autowired
  @Qualifier(TASK_IS_ASSIGNED_CHECK)
  private TestCheck taskIsAssignedCheck;

  //  @Autowired
  //  @Qualifier("operationsByWorkflowInstanceAreCompletedCheck")
  //  private Predicate<Object[]> operationsByWorkflowInstanceAreCompletedCheck;
  //
  //  @Autowired
  //  @Qualifier("variableExistsCheck")
  //  private Predicate<Object[]> variableExistsCheck;
  //
  //  @Autowired
  //  private ZeebeImporter zeebeImporter;
  //
  //  @Autowired
  //  protected OperationExecutor operationExecutor;
  //
  //  @Autowired
  //  protected VariableReader variableReader;
  //
  //  @Autowired
  //  protected IncidentReader incidentReader;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private ElasticsearchHelper elasticsearchHelper;

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

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

  public GraphQLResponse getCompletedTasks() throws IOException {
    graphQLResponse =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-completed-tasks.graphql");
    return graphQLResponse;
  }

  public GraphQLResponse getAllTasks() throws IOException {
    graphQLResponse = graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
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
  //
  //  public TasklistTester updateVariableOperation(String varName, String varValue) throws
  // Exception {
  //    final CreateOperationRequestDto op = new
  // CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
  //    op.setVariableName(varName);
  //    op.setVariableValue(varValue);
  //    op.setVariableScopeId(ConversionUtils.toStringOrNull(workflowInstanceKey));
  //    postOperation(op);
  //    elasticsearchTestRule.refreshIndexesInElasticsearch();
  //    return this;
  //  }
  //
  //  private MvcResult postOperation(CreateOperationRequestDto operationRequest) throws Exception {
  //    MockHttpServletRequestBuilder postOperationRequest =
  //        post(String.format( "/api/workflow-instances/%s/operation", workflowInstanceKey))
  //            .content(mockMvcTestRule.json(operationRequest))
  //            .contentType(mockMvcTestRule.getContentType());
  //
  //    final MvcResult mvcResult =
  //        mockMvcTestRule.getMockMvc().perform(postOperationRequest)
  //            .andExpect(status().is(HttpStatus.SC_OK))
  //            .andReturn();
  //    return mvcResult;
  //  }
  //
  //  public TasklistTester cancelWorkflowInstanceOperation() throws Exception {
  //    final ListViewRequestDto workflowInstanceQuery =
  // TestUtil.createGetAllWorkflowInstancesQuery()
  //        .setIds(Collections.singletonList(workflowInstanceKey.toString()));
  //
  //    CreateBatchOperationRequestDto batchOperationDto
  //        = new CreateBatchOperationRequestDto(workflowInstanceQuery,
  // OperationType.CANCEL_WORKFLOW_INSTANCE);
  //
  //    postOperation(batchOperationDto);
  //    elasticsearchTestRule.refreshIndexesInElasticsearch();
  //    return this;
  //  }
  //
  //  public TasklistTester operationIsCompleted() throws Exception {
  //    executeOneBatch();
  //
  // elasticsearchTestRule.processAllRecordsAndWait(operationsByWorkflowInstanceAreCompletedCheck,
  // workflowInstanceKey);
  //    return this;
  //  }
  //
  //  private MvcResult postOperation(CreateBatchOperationRequestDto operationRequest) throws
  // Exception {
  //    MockHttpServletRequestBuilder postOperationRequest =
  //      post(String.format( "/api/workflow-instances/%s/operation", workflowInstanceKey))
  //        .content(mockMvcTestRule.json(operationRequest))
  //        .contentType(mockMvcTestRule.getContentType());
  //
  //    final MvcResult mvcResult =
  //      mockMvcTestRule.getMockMvc().perform(postOperationRequest)
  //        .andExpect(status().is(HttpStatus.SC_OK))
  //        .andReturn();
  //    return mvcResult;
  //  }
  //
  //  private int executeOneBatch() throws Exception {
  //    if(!operationExecutorEnabled) return 0;
  //      List<Future<?>> futures = operationExecutor.executeOneBatch();
  //      //wait till all scheduled tasks are executed
  //      for(Future f: futures) { f.get();}
  //      return 0;//return futures.size()
  //  }
  //
  //  public int importOneType(ImportValueType importValueType) throws IOException {
  //    List<RecordsReader> readers = elasticsearchTestRule.getRecordsReaders(importValueType);
  //    int count = 0;
  //    for (RecordsReader reader: readers) {
  //      count += zeebeImporter.importOneBatch(reader);
  //    }
  //    return count;
  //  }
  //
  //  public TasklistTester then() {
  //    return this;
  //  }
  //
  //  public TasklistTester disableOperationExecutor() {
  //    operationExecutorEnabled = false;
  //    return this;
  //  }
  //
  //  public TasklistTester enableOperationExecutor() throws Exception {
  //    operationExecutorEnabled = true;
  //    return executeOperations();
  //  }
  //
  //  public TasklistTester archive() {
  //    try {
  //      WorkflowInstancesArchiverJob.ArchiveBatch finishedAtDateIds = new
  // AbstractArchiverJob.ArchiveBatch("_test_archived", Arrays.asList(workflowInstanceKey));
  //      WorkflowInstancesArchiverJob archiverJob =
  // beanFactory.getBean(WorkflowInstancesArchiverJob.class);
  //      archiverJob.archiveBatch(finishedAtDateIds);
  //    } catch (ArchiverException e) {
  //      return this;
  //    }
  //    return this;
  //  }
  //
  //  public TasklistTester executeOperations() throws Exception {
  //     executeOneBatch();
  //     return this;
  //  }
  //
  //  public TasklistTester archiveIsDone() {
  //    elasticsearchTestRule.refreshTasklistESIndices();
  //    return this;
  //  }
  //
  //  public TasklistTester variableExists(String name) {
  //    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, workflowInstanceKey,
  // workflowInstanceKey,name);
  //    return this;
  //  }
  //
  //  public String getVariable(String name) {
  //   return getVariable(name,workflowInstanceKey);
  //  }
  //
  //  public String getVariable(String name,Long scopeKey) {
  //    List<VariableDto> variables = variableReader.getVariables(workflowInstanceKey, scopeKey);
  //    List<VariableDto> variablesWithGivenName = filter(variables, variable ->
  // variable.getName().equals(name));
  //    if(variablesWithGivenName.isEmpty()) {
  //      return null;
  //    }
  //    return variablesWithGivenName.get(0).getValue();
  //  }
  //
  //  public boolean hasVariable(String name, String value) {
  //    String variableValue = getVariable(name);
  //    return value==null? (variableValue == null): value.equals(variableValue);
  //  }
  //
  //  public List<IncidentEntity> getIncidents() {
  //    return getIncidentsFor(workflowInstanceKey);
  //  }
  //
  //  public List<IncidentEntity> getIncidentsFor(Long workflowInstanceKey) {
  //    return incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
  //  }
  //
  //  public boolean hasIncidentWithErrorMessage(String errorMessage) {
  //    return !filter(getIncidents(),incident ->
  // incident.getErrorMessage().equals(errorMessage)).isEmpty();
  //  }

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
