/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_COMPLETED_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.TASK_IS_CREATED_CHECK;
import static io.zeebe.tasklist.util.ElasticsearchChecks.WORKFLOW_IS_DEPLOYED_CHECK;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.ElasticsearchChecks.TestCheck;
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

  @Autowired
  @Qualifier(WORKFLOW_IS_DEPLOYED_CHECK)
  private TestCheck workflowIsDeployedCheck;

  @Autowired
  @Qualifier(TASK_IS_CREATED_CHECK)
  private TestCheck taskIsCreatedCheck;

  @Autowired
  @Qualifier(TASK_IS_COMPLETED_CHECK)
  private TestCheck taskIsCompletedCheck;

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
    return this;
  }

  public TasklistTester taskIsCompleted(String flowNodeBpmnId) {
    elasticsearchTestRule.processAllRecordsAndWait(
        taskIsCompletedCheck, workflowInstanceId, flowNodeBpmnId);
    return this;
  }

  public TasklistTester completeHumanTask(String flowNodeBpmnId, String payload) {
    ZeebeTestUtil.completeTask(
        zeebeClient,
        tasklistProperties.getImporter().getJobType(),
        TestUtil.createRandomString(10),
        payload);
    return taskIsCompleted(flowNodeBpmnId);
  }

  public TasklistTester completeHumanTask(String flowNodeBpmnId) {
    ZeebeTestUtil.completeTask(
        zeebeClient,
        tasklistProperties.getImporter().getJobType(),
        TestUtil.createRandomString(10),
        null);
    return taskIsCompleted(flowNodeBpmnId);
  }

  public TasklistTester completeServiceTask(String jobType) {
    ZeebeTestUtil.completeTask(zeebeClient, jobType, TestUtil.createRandomString(10), null);
    return this;
  }

  public TasklistTester and() {
    return this;
  }

  public TasklistTester waitUntil() {
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
}
