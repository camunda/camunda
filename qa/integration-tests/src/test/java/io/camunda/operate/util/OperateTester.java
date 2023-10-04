/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.archiver.ArchiveBatch;
import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.*;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.apache.commons.lang3.Validate;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static io.camunda.operate.util.CollectionUtil.filter;
import static io.camunda.operate.webapp.rest.FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Component
@Scope(SCOPE_PROTOTYPE)
public class OperateTester {

  protected static final Logger logger = LoggerFactory.getLogger(OperateTester.class);

  @Autowired
  private BeanFactory beanFactory;

  private ZeebeClient zeebeClient;
  private MockMvcTestRule mockMvcTestRule;
  private SearchTestRule searchTestRule;

  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long jobKey;

  @Autowired
  @Qualifier("processIsDeployedCheck")
  private Predicate<Object[]> processIsDeployedCheck;

  @Autowired
  @Qualifier("decisionsAreDeployedCheck")
  private Predicate<Object[]> decisionsAreDeployedCheck;

  @Autowired
  @Qualifier("decisionInstancesAreCreated")
  private Predicate<Object[]> decisionInstancesAreCreated;

  @Autowired
  @Qualifier("processInstancesAreStartedCheck")
  private Predicate<Object[]> processInstancesAreStartedCheck;

  @Autowired
  @Qualifier("processInstanceExistsCheck")
  private Predicate<Object[]> processInstanceExistsCheck;

  @Autowired
  @Qualifier("processInstancesAreFinishedCheck")
  private Predicate<Object[]> processInstancesAreFinishedCheck;

  @Autowired
  @Qualifier("processInstanceIsCompletedCheck")
  private Predicate<Object[]> processInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("processInstanceIsCanceledCheck")
  private Predicate<Object[]> processInstanceIsCanceledCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("incidentsInAnyInstanceAreActiveCheck")
  private Predicate<Object[]> incidentsInAnyInstanceAreActiveCheck;

  @Autowired
  @Qualifier("flowNodeIsActiveCheck")
  private Predicate<Object[]> flowNodeIsActiveCheck;

  @Autowired
  @Qualifier("eventIsImportedCheck")
  private Predicate<Object[]> eventIsImportedCheck;

  @Autowired
  @Qualifier("flowNodesAreActiveCheck")
  private Predicate<Object[]> flowNodesAreActiveCheck;

  @Autowired
  @Qualifier("flowNodesExistCheck")
  private Predicate<Object[]> flowNodesExistCheck;

  @Autowired
  @Qualifier("flowNodesInAnyInstanceAreActiveCheck")
  private Predicate<Object[]> flowNodesInAnyInstanceAreActiveCheck;

  @Autowired
  @Qualifier("flowNodeIsCompletedCheck")
  private Predicate<Object[]> flowNodeIsCompletedCheck;

  @Autowired
  @Qualifier("flowNodesAreCompletedCheck")
  private Predicate<Object[]> flowNodesAreCompletedCheck;
  @Autowired
  @Qualifier("flowNodeIsTerminatedCheck")
  private Predicate<Object[]> flowNodeIsTerminatedCheck;
  @Autowired
  @Qualifier("flowNodesAreTerminatedCheck")
  private Predicate<Object[]> flowNodesAreTerminatedCheck;

  @Autowired
  @Qualifier("operationsByProcessInstanceAreCompletedCheck")
  private Predicate<Object[]> operationsByProcessInstanceAreCompletedCheck;

  @Autowired
  @Qualifier("operationsByProcessInstanceAreFailedCheck")
  private Predicate<Object[]> operationsByProcessInstanceAreFailedCheck;

  @Autowired
  @Qualifier("variableExistsCheck")
  private Predicate<Object[]> variableExistsCheck;

  @Autowired
  @Qualifier("variableExistsInCheck")
  private Predicate<Object[]> variableExistsInCheck;

  @Autowired
  @Qualifier("variableHasValue")
  private Predicate<Object[]> variableHasValue;

  @Autowired
  protected OperationExecutor operationExecutor;

  @Autowired
  protected io.camunda.operate.webapp.reader.VariableReader variableReader;

  @Autowired
  protected IncidentReader incidentReader;

  @Autowired
  protected ListViewReader listViewReader;

  @Autowired
  protected FlowNodeInstanceReader flowNodeInstanceReader;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private ObjectMapper objectMapper;

  private boolean operationExecutorEnabled = true;
  private BatchOperationDto operation;
  private List<String> processDefinitions;

  public OperateTester(ZeebeClient zeebeClient, MockMvcTestRule mockMvcTestRule, SearchTestRule searchTestRule) {
    this.zeebeClient = zeebeClient;
    this.mockMvcTestRule = mockMvcTestRule;
    this.searchTestRule = searchTestRule;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public BatchOperationDto getOperation() {
    return operation;
  }

  public List<OperationDto> getOperations() {
    return operationReader.getOperationsByBatchOperationId(operation.getId());
  }

  public OperateTester createAndDeploySimpleProcess(String processId,String activityId) {
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask(activityId).zeebeJobType(activityId)
        .endEvent()
        .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, null, process, processId+".bpmn");
    return this;
  }

  public OperateTester deployProcess(String... classpathResources) {
    Validate.notNull(zeebeClient, "ZeebeClient should be set.");
    logger.debug("Deploy process(es) {}", List.of(classpathResources));
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, null, classpathResources);
    return this;
  }

  public OperateTester deployDecision(String... classpathResources) {
    Validate.notNull(zeebeClient, "ZeebeClient should be set.");
    ZeebeTestUtil.deployDecision(zeebeClient, null, classpathResources);
    return this;
  }

  public OperateTester deployProcess(BpmnModelInstance processModel, String resourceName) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, null, processModel, resourceName);
    return this;
  }

  public OperateTester processIsDeployed() {
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    logger.debug("Process is deployed with key: {}", processDefinitionKey);
    return this;
  }

  public OperateTester decisionsAreDeployed(int count) {
    searchTestRule.processAllRecordsAndWait(decisionsAreDeployedCheck, count);
    return this;
  }

  public OperateTester decisionInstancesAreCreated(int count) {
    searchTestRule.processAllRecordsAndWait(decisionInstancesAreCreated, count);
    return this;
  }


  public OperateTester startProcessInstance(String bpmnProcessId) {
    logger.debug("Start process instance '{}'",bpmnProcessId);
    return startProcessInstance(bpmnProcessId, null);
  }

  public OperateTester startProcessInstance(String bpmnProcessId, String payload) {
    logger.debug("Start process instance '{}' with payload '{}'", bpmnProcessId, payload);
    processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, payload);
    return this;
  }

  public OperateTester startProcessInstanceWithVariables(String bpmnProcessId, Map<String,String> nameValuePairs) {
    try {
      processInstanceKey = ZeebeTestUtil.startProcessInstance(
          zeebeClient,
          bpmnProcessId,
          objectMapper.writeValueAsString(nameValuePairs));
    } catch (JsonProcessingException e) {
      throw new OperateRuntimeException(e);
    }
    return this;
  }

  public OperateTester processInstanceIsStarted() {
    searchTestRule.processAllRecordsAndWait(processInstancesAreStartedCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceExists() {
    searchTestRule.processAllRecordsAndWait(processInstanceExistsCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceIsFinished() {
    searchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceIsCompleted() {
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey);
    return this;
  }

  public OperateTester processInstanceIsCanceled() {
    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    return this;
  }

  public OperateTester failTask(String taskName, String errorMessage) {
    jobKey = ZeebeTestUtil.failTask(zeebeClient, taskName, UUID.randomUUID().toString(), 3,errorMessage);
    return this;
  }

  public OperateTester throwError(String taskName,String errorCode,String errorMessage) {
    ZeebeTestUtil.throwErrorInTask(zeebeClient, taskName, UUID.randomUUID().toString(), 1, errorCode, errorMessage);
    return this;
  }

  public OperateTester resolveIncident() {
    zeebeClient.newUpdateRetriesCommand(jobKey).retries(3).send().join();
    zeebeClient.newResolveIncidentCommand(Long.valueOf(
        getIncidents().stream().filter(i -> i.getJobId().equals(String.valueOf(jobKey))).findFirst()
            .get().getId())).send().join();
    return this;
  }

  public OperateTester resolveIncident(long jobKey, long incidentKey) {
    zeebeClient.newUpdateRetriesCommand(jobKey).retries(3).send().join();
    zeebeClient.newResolveIncidentCommand(incidentKey).send().join();
    return this;
  }

  public OperateTester incidentIsActive() {
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    return this;
  }

  public OperateTester incidentsInAnyInstanceAreActive(long count) {
    searchTestRule.processAllRecordsAndWait(incidentsInAnyInstanceAreActiveCheck, count);
    return this;
  }

  public OperateTester flowNodeIsActive(String activityId) {
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, activityId);
    logger.debug("FlowNode {} is active.", activityId);
    return this;
  }

  public OperateTester eventIsImported(String jobType) {
    searchTestRule.processAllRecordsAndWait(eventIsImportedCheck, processInstanceKey, jobType);
    return this;
  }

  public OperateTester flowNodesAreActive(String activityId, int count) {
    searchTestRule.processAllRecordsAndWait(flowNodesAreActiveCheck, processInstanceKey, activityId, count);
    logger.debug("{} FlowNodes {} are active.", count, activityId);
    return this;
  }

  public OperateTester flowNodesExist(String activityId, int count) {
    searchTestRule.processAllRecordsAndWait(flowNodesExistCheck, processInstanceKey, activityId, count);
    logger.debug("{} FlowNodes {} exist.", count, activityId);
    return this;
  }

  public OperateTester flowNodesInAnyInstanceAreActive(String activityId, int count) {
    searchTestRule.processAllRecordsAndWait(flowNodesInAnyInstanceAreActiveCheck, activityId, count);
    return this;
  }

  public OperateTester flowNodeIsCompleted(String activityId) {
    searchTestRule.processAllRecordsAndWait(flowNodeIsCompletedCheck, processInstanceKey, activityId);
    logger.debug("FlowNode {} is completed.", activityId);
    return this;
  }

  public OperateTester flowNodesAreCompleted(String activityId, int count) {
    searchTestRule.processAllRecordsAndWait(flowNodesAreCompletedCheck, processInstanceKey, activityId, count);
    logger.debug("{} FlowNodes {} is completed.", count, activityId);
    return this;
  }


  public Long getFlowNodeInstanceKeyFor(final String flowNodeId) {
    return Long.parseLong(
        flowNodeInstanceReader.getFlowNodeMetadata(""+processInstanceKey,
        new FlowNodeMetadataRequestDto().setFlowNodeId(flowNodeId)).getFlowNodeInstanceId());
  }
  public Map<String, FlowNodeStateDto> getFlowNodeStates(){
    return flowNodeInstanceReader.getFlowNodeStates("" + processInstanceKey);
  }

  public FlowNodeStateDto getFlowNodeStateFor(String flowNodeId){
    return getFlowNodeStates().get(flowNodeId);
  }

  public OperateTester flowNodeIsTerminated(final String activityId) {
    searchTestRule.processAllRecordsAndWait(flowNodeIsTerminatedCheck, processInstanceKey, activityId);
    logger.debug("FlowNode {} is terminated.", activityId);
    return this;
  }

  public OperateTester flowNodeIsCanceled(final String activityId) {
    return flowNodeIsTerminated(activityId);
  }

  public OperateTester flowNodesAreTerminated(final String activityId, final int count) {
    searchTestRule.processAllRecordsAndWait(flowNodesAreTerminatedCheck, processInstanceKey, activityId, count);
    logger.debug("{} FlowNodes {} are active.", count, activityId);
    return this;
  }
  public OperateTester flowNodesAreCanceled(final String activityId, final int count) {
    return flowNodesAreTerminated(activityId, count);
  }
  public OperateTester activateJob(String type){
    zeebeClient.newActivateJobsCommand()
        .jobType(type)
        .maxJobsToActivate(1)
        .send();
    return this;
  }

  public OperateTester completeTask(String activityId, String jobKey) {
    return completeTask(activityId, jobKey, null);
  }

  public OperateTester completeTask(String jobKey) {
    return completeTask(jobKey, jobKey, null);
  }

  public OperateTester completeTask(String activityId, String jobKey, String payload) {
    ZeebeTestUtil.completeTask(zeebeClient, jobKey, TestUtil.createRandomString(10), payload);
    return flowNodeIsCompleted(activityId);
  }

  public OperateTester and() {
    return this;
  }

  public OperateTester waitUntil() {
    return this;
  }

  public OperateTester updateVariableOperation(String varName, String varValue) throws Exception {
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setVariableName(varName);
    op.setVariableValue(varValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(processInstanceKey));
    postOperation(op);
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  public OperateTester modifyProcessInstanceOperation(List<ModifyProcessInstanceRequestDto.Modification> modifications)
      throws Exception {
    final ModifyProcessInstanceRequestDto op = new ModifyProcessInstanceRequestDto()
        .setProcessInstanceKey(processInstanceKey+"")
        .setModifications(modifications);

    postOperation(op);
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  private MvcResult postOperation(CreateOperationRequestDto operationRequest) throws Exception {
    MockHttpServletRequestBuilder postOperationRequest =
        post(String.format( "/api/process-instances/%s/operation", processInstanceKey))
            .content(mockMvcTestRule.json(operationRequest))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvcTestRule.getMockMvc().perform(postOperationRequest)
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn();
    operation = mockMvcTestRule.fromResponse(mvcResult, BatchOperationDto.class);
    return mvcResult;
  }

  private MvcResult postOperation(ModifyProcessInstanceRequestDto operationRequest) throws Exception {
    final MockHttpServletRequestBuilder ope =
        post(String.format(PROCESS_INSTANCE_URL+"/%s/modify", processInstanceKey))
            .content(mockMvcTestRule.json(operationRequest))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvcTestRule.getMockMvc().perform(ope)
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn();
    operation = mockMvcTestRule.fromResponse(mvcResult, BatchOperationDto.class);
    return mvcResult;
  }

  public OperateTester cancelProcessInstanceOperation() throws Exception {
    final ListViewQueryDto processInstanceQuery = createGetAllProcessInstancesQuery()
        .setIds(Collections.singletonList(processInstanceKey.toString()));

    CreateBatchOperationRequestDto batchOperationDto
        = new CreateBatchOperationRequestDto(processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE);

    postOperation(batchOperationDto);
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  public OperateTester deleteProcessInstance() throws Exception {
    postOperation(new CreateOperationRequestDto(OperationType.DELETE_PROCESS_INSTANCE));
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  public OperateTester activateFlowNode(String flowNodeId, final Long ancestorElementInstanceKey){
    zeebeClient.newModifyProcessInstanceCommand(processInstanceKey)
        .activateElement(flowNodeId, ancestorElementInstanceKey)
        .send().join();
    return this;
  }

  public OperateTester cancelAllFlowNodesFor(final String flowNodeId){
    getAllFlowNodeInstances(processInstanceKey).stream()
        .filter(flowNode -> flowNode.getFlowNodeId().equals(flowNodeId))
        .map(flowNode-> flowNode.getKey())
        .forEach(key ->
          zeebeClient
              .newModifyProcessInstanceCommand(processInstanceKey)
              .terminateElement(key)
              .send().join()
        );
    return this;
  }

  public OperateTester cancelFlowNodeInstance(Long flowNodeInstanceId){
    zeebeClient.newModifyProcessInstanceCommand(processInstanceKey)
        .terminateElement(flowNodeInstanceId)
        .send().join();
    return this;
  }

  public OperateTester operationIsCompleted() throws Exception {
    executeOneBatch();
    searchTestRule.processAllRecordsAndWait(operationsByProcessInstanceAreCompletedCheck, processInstanceKey);
    return this;
  }
  public OperateTester operationIsFailed() throws Exception {
    executeOneBatch();
    searchTestRule.processAllRecordsAndWait(operationsByProcessInstanceAreFailedCheck, processInstanceKey);
    return this;
  }

  private MvcResult postOperation(CreateBatchOperationRequestDto operationRequest) throws Exception {
    MockHttpServletRequestBuilder postOperationRequest =
      post(String.format( "/api/process-instances/%s/operation", processInstanceKey))
        .content(mockMvcTestRule.json(operationRequest))
        .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
      mockMvcTestRule.getMockMvc().perform(postOperationRequest)
        .andExpect(status().is(HttpStatus.SC_OK))
        .andReturn();
    operation = mockMvcTestRule.fromResponse(mvcResult, BatchOperationDto.class);
    return mvcResult;
  }

  private int executeOneBatch() throws Exception {
    if (!operationExecutorEnabled) {
      return 0;
    }
    List<Future<?>> futures = operationExecutor.executeOneBatch();
    //wait till all scheduled tasks are executed
    for (Future f : futures) {
      f.get();
    }
    return 0;//return futures.size()
  }

  public OperateTester then() {
    return this;
  }

  public OperateTester disableOperationExecutor() {
    operationExecutorEnabled = false;
    return this;
  }

  public OperateTester enableOperationExecutor() throws Exception {
    operationExecutorEnabled = true;
    return executeOperations();
  }

  public OperateTester archive() {
    try {
      ArchiveBatch finishedAtDateIds = new ArchiveBatch("_test_archived", Arrays.asList(processInstanceKey));
      io.camunda.operate.archiver.ProcessInstancesArchiverJob archiverJob = beanFactory.getBean(ProcessInstancesArchiverJob.class);
      archiverJob.archiveBatch(finishedAtDateIds).join();
    } catch (Exception e) {
      return this;
    }
    return this;
  }

  public OperateTester executeOperations() throws Exception {
     executeOneBatch();
     searchTestRule.refreshOperateSearchIndices();
     return this;
  }

  public OperateTester archiveIsDone() {
    searchTestRule.refreshOperateSearchIndices();
    return this;
  }

  public OperateTester variableExists(String name) {
    searchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, name);
    return this;
  }

  public OperateTester variableExistsIn(final String name, final Long scopeKey) {
    searchTestRule.processAllRecordsAndWait(variableExistsInCheck, processInstanceKey, name, scopeKey);
    return this;
  }

  public OperateTester variableHasValue(final String name, final Object value) {
    searchTestRule.processAllRecordsAndWait(variableHasValue, processInstanceKey, name, value, processInstanceKey);
    return this;
  }

  public OperateTester variableHasValue(final String name, final Object value,final Long scopeKey) {
    searchTestRule.processAllRecordsAndWait(variableHasValue, processInstanceKey, name, value, scopeKey);
    return this;
  }

  public OperateTester conditionIsMet(Predicate<Object[]> elsCheck, Object... arguments) {
    searchTestRule.processAllRecordsAndWait(elsCheck, arguments);
    return this;
  }

  public String getVariable(String name) {
   return getVariable(name,processInstanceKey);
  }

  public String getVariable(String name, Long scopeKey) {
    List<VariableDto> variables = getVariables(processInstanceKey, scopeKey);
    List<VariableDto> variablesWithGivenName =
        filter(variables, variable -> variable.getName().equals(name));
    if (variablesWithGivenName.isEmpty()) {
      return null;
    }
    return variablesWithGivenName.get(0).getValue();
  }

  private List<VariableDto> getVariables(final Long processInstanceKey, final Long scopeKey) {
    return variableReader.getVariables(
        String.valueOf(processInstanceKey),
        new VariableRequestDto().setScopeId(String.valueOf(scopeKey)));
  }

  public List<VariableDto> getVariablesForScope(final Long scopeKey){
    return getVariables(processInstanceKey, scopeKey);
  }

  public boolean hasVariable(String name, String value) {
    String variableValue = getVariable(name);
    return value==null? (variableValue == null): value.contains(variableValue);
  }

  public List<IncidentDto> getIncidents() {
    return getIncidentsFor(processInstanceKey);
  }

  public List<IncidentDto> getIncidentsFor(Long processInstanceKey) {
    return incidentReader.getIncidentsByProcessInstanceId(String.valueOf(processInstanceKey)).getIncidents();
  }

  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances(){
    return getAllFlowNodeInstances(processInstanceKey);
  }

  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances(Long processInstanceKey) {
    return flowNodeInstanceReader.getAllFlowNodeInstances(processInstanceKey);
  }

  public FlowNodeInstanceEntity getFlowNodeInstanceEntityFor(final Long flowNodeInstanceKey){
    return getAllFlowNodeInstances(processInstanceKey).stream()
        .filter( i -> i.getKey() == flowNodeInstanceKey)
        .findFirst().orElseThrow();
  }

  public boolean hasIncidentWithErrorMessage(String errorMessage) {
    return !filter(getIncidents(),incident -> incident.getErrorMessage().equals(errorMessage)).isEmpty();
  }

  public List<FlowNodeInstanceDto> getFlowNodeInstanceOneListFromRest(
      String processInstanceId) throws Exception {
    return getFlowNodeInstanceOneListFromRest(
        new FlowNodeInstanceQueryDto(processInstanceId, processInstanceId));
  }

  public List<FlowNodeInstanceDto> getFlowNodeInstanceOneListFromRest(
      FlowNodeInstanceQueryDto query) throws Exception {
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(query);
    MvcResult mvcResult = postRequest(FLOW_NODE_INSTANCE_URL, request);
    final Map<String, FlowNodeInstanceResponseDto> response = mockMvcTestRule
        .fromResponse(mvcResult, new TypeReference<>() {
        });
    assertThat(response).hasSize(1);
    return response.values().iterator().next().getChildren();
  }

  public FlowNodeMetadataDto getFlowNodeMetadataFromRest(String processInstanceId,
      String flowNodeId, FlowNodeType flowNodeType, String flowNodeInstanceId)
      throws Exception {
    final FlowNodeMetadataRequestDto request = new FlowNodeMetadataRequestDto()
        .setFlowNodeId(flowNodeId)
        .setFlowNodeType(flowNodeType)
        .setFlowNodeInstanceId(flowNodeInstanceId);
    MvcResult mvcResult = postRequest(
        String.format(PROCESS_INSTANCE_URL + "/%s/flow-node-metadata", processInstanceId),
        request);
    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });
  }

  public ListViewProcessInstanceDto getSingleProcessInstanceByBpmnProcessId(String processId) {
    final ListViewRequestDto request = createGetAllProcessInstancesRequest(q -> q.setProcessIds(
        Arrays.asList(processId)));
    request.setPageSize(100);
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getProcessInstances()).hasSize(1);
    return listViewResponse.getProcessInstances().get(0);
  }

  public void cancelFlowNodeByInstanceKey(final Long processInstanceKey, final Long flowNodeInstanceKey) {
    zeebeClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .terminateElement(flowNodeInstanceKey)
        .send()
        .join();
  }

  public OperateTester activateFlowNode(final String flowNodeId) {
    zeebeClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .activateElement(flowNodeId)
        .send()
        .join();
    return this;
  }

  public void moveFlowNodeFromTo(final Long sourceFlowNodeInstanceKey,final String targetFlowNodeId) {
    zeebeClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .activateElement(targetFlowNodeId)
        .and()
        .terminateElement(sourceFlowNodeInstanceKey)
        .send()
        .join();
  }

  public void moveFlowNodeFromTo(final Long sourceFlowNodeInstanceKey,final String targetFlowNodeId, final Long ancestorElementInstanceKey) {
    zeebeClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .activateElement(targetFlowNodeId, ancestorElementInstanceKey)
        .and()
        .terminateElement(sourceFlowNodeInstanceKey)
        .send()
        .join();
  }

  public OperateTester sendMessages(final String messageName,final String correlationKey, final String payload, final int count){
    ZeebeTestUtil.sendMessages(zeebeClient, messageName, payload, count, correlationKey);
    return this;
  }

  private MvcResult postRequest(String requestUrl, Object query) throws Exception {
    MockHttpServletRequestBuilder request = post(requestUrl)
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());

    return mockMvcTestRule.getMockMvc().perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(mockMvcTestRule.getContentType()))
        .andReturn();
  }

  public String getItemsPayloadFor(int size) {
    return "{\"items\": [" + IntStream.range(0, size).boxed().map(Object::toString).collect(Collectors.joining(","))
        + "]}";
  }

  public List<Long> getFlowNodeInstanceKeysFor(final String flowNodeId) {
    return flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(processInstanceKey, flowNodeId,
        List.of(FlowNodeState.ACTIVE, FlowNodeState.COMPLETED, FlowNodeState.TERMINATED));
  }
}
