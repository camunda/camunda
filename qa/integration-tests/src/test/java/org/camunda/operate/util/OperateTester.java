/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.CollectionUtil.filter;
import static org.camunda.operate.util.ElasticsearchUtil.scroll;
import static org.camunda.operate.webapp.rest.FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL;
import static org.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.apache.commons.lang3.Validate;
import org.apache.http.HttpStatus;
import org.camunda.operate.archiver.AbstractArchiverJob;
import org.camunda.operate.archiver.ProcessInstancesArchiverJob;
import org.camunda.operate.entities.FlowNodeInstanceEntity;
import org.camunda.operate.entities.FlowNodeType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.exceptions.ArchiverException;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.es.reader.VariableReader;
import org.camunda.operate.webapp.rest.dto.VariableDto;
import org.camunda.operate.webapp.rest.dto.VariableRequestDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import org.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import org.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import org.camunda.operate.zeebe.ImportValueType;
import org.camunda.operate.zeebeimport.RecordsReader;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Component
@Scope(SCOPE_PROTOTYPE)
public class OperateTester {

  protected static final Logger logger = LoggerFactory.getLogger(OperateTester.class);

  @Autowired
  private BeanFactory beanFactory;

  private ZeebeClient zeebeClient;
  private MockMvcTestRule mockMvcTestRule;
  private ElasticsearchTestRule elasticsearchTestRule;

  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long jobKey;

  @Autowired
  @Qualifier("processIsDeployedCheck")
  private Predicate<Object[]> processIsDeployedCheck;

  @Autowired
  @Qualifier("processInstancesAreStartedCheck")
  private Predicate<Object[]> processInstancesAreStartedCheck;

  @Autowired
  @Qualifier("processInstancesAreFinishedCheck")
  private Predicate<Object[]> processInstancesAreFinishedCheck;

  @Autowired
  @Qualifier("processInstanceIsCompletedCheck")
  private Predicate<Object[]> processInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("flowNodeIsActiveCheck")
  private Predicate<Object[]> flowNodeIsActiveCheck;

  @Autowired
  @Qualifier("flowNodeIsCompletedCheck")
  private Predicate<Object[]> flowNodeIsCompletedCheck;

  @Autowired
  @Qualifier("operationsByProcessInstanceAreCompletedCheck")
  private Predicate<Object[]> operationsByProcessInstanceAreCompletedCheck;

  @Autowired
  @Qualifier("variableExistsCheck")
  private Predicate<Object[]> variableExistsCheck;

  @Autowired
  private ZeebeImporter zeebeImporter;

  @Autowired
  protected OperationExecutor operationExecutor;

  @Autowired
  protected VariableReader variableReader;

  @Autowired
  protected IncidentReader incidentReader;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  private boolean operationExecutorEnabled = true;

  public OperateTester(ZeebeClient zeebeClient, MockMvcTestRule mockMvcTestRule, ElasticsearchTestRule elasticsearchTestRule) {
    this.zeebeClient = zeebeClient;
    this.mockMvcTestRule = mockMvcTestRule;
    this.elasticsearchTestRule = elasticsearchTestRule;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public OperateTester createAndDeploySimpleProcess(String processId,String activityId) {
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask(activityId).zeebeJobType(activityId)
        .endEvent()
        .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, process,processId+".bpmn");
    return this;
  }

  public OperateTester deployProcess(String... classpathResources) {
    Validate.notNull(zeebeClient, "ZeebeClient should be set.");
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, classpathResources);
    return this;
  }

  public OperateTester deployProcess(BpmnModelInstance processModel, String resourceName) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, processModel, resourceName);
    return this;
  }

  public OperateTester processIsDeployed() {
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    return this;
  }

  public OperateTester startProcessInstance(String bpmnProcessId) {
   return startProcessInstance(bpmnProcessId, null);
  }

  public OperateTester startProcessInstance(String bpmnProcessId, String payload) {
    processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, payload);
    return this;
  }

  public OperateTester processInstanceIsStarted() {
    elasticsearchTestRule.processAllRecordsAndWait(processInstancesAreStartedCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceIsFinished() {
    elasticsearchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceIsCompleted() {
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey);
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
    zeebeClient.newResolveIncidentCommand(getIncidents().get(0).getKey()).send().join();
    return this;
  }

  public OperateTester incidentIsActive() {
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    return this;
  }

  public OperateTester flowNodeIsActive(String activityId) {
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey,activityId);
    return this;
  }

  public OperateTester flowNodeIsCompleted(String activityId) {
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsCompletedCheck, processInstanceKey, activityId);
    return this;
  }

  public OperateTester activateJob(String type){
    zeebeClient.newActivateJobsCommand()
        .jobType(type)
        .maxJobsToActivate(1)
        .send();
    return this;
  }

  public OperateTester completeTask(String activityId) {
    return completeTask(activityId, null);
  }

  public OperateTester completeTask(String activityId, String payload) {
    ZeebeTestUtil.completeTask(zeebeClient, activityId, TestUtil.createRandomString(10), payload);
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
    elasticsearchTestRule.refreshIndexesInElasticsearch();
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
    return mvcResult;
  }

  public OperateTester cancelProcessInstanceOperation() throws Exception {
    final ListViewQueryDto processInstanceQuery = TestUtil.createGetAllProcessInstancesQuery()
        .setIds(Collections.singletonList(processInstanceKey.toString()));

    CreateBatchOperationRequestDto batchOperationDto
        = new CreateBatchOperationRequestDto(processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE);

    postOperation(batchOperationDto);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return this;
  }

  public OperateTester operationIsCompleted() throws Exception {
    executeOneBatch();
    elasticsearchTestRule.processAllRecordsAndWait(operationsByProcessInstanceAreCompletedCheck, processInstanceKey);
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

  public int importOneType(ImportValueType importValueType) throws IOException {
    List<RecordsReader> readers = elasticsearchTestRule.getRecordsReaders(importValueType);
    int count = 0;
    for (RecordsReader reader: readers) {
      count += zeebeImporter.importOneBatch(reader);
    }
    return count;
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
      ProcessInstancesArchiverJob.ArchiveBatch finishedAtDateIds = new AbstractArchiverJob.ArchiveBatch("_test_archived", Arrays.asList(processInstanceKey));
      ProcessInstancesArchiverJob archiverJob = beanFactory.getBean(ProcessInstancesArchiverJob.class);
      archiverJob.archiveBatch(finishedAtDateIds);
    } catch (ArchiverException e) {
      return this;
    }
    return this;
  }

  public OperateTester executeOperations() throws Exception {
     executeOneBatch();
     return this;
  }

  public OperateTester archiveIsDone() {
    elasticsearchTestRule.refreshOperateESIndices();
    return this;
  }

  public OperateTester variableExists(String name) {
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, name);
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

  public boolean hasVariable(String name, String value) {
    String variableValue = getVariable(name);
    return value==null? (variableValue == null): value.equals(variableValue);
  }

  public List<IncidentEntity> getIncidents() {
    return getIncidentsFor(processInstanceKey);
  }

  public List<IncidentEntity> getIncidentsFor(Long processInstanceKey) {
    return incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
  }

  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances(Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(processInstanceKeyQuery))
            .sort(FlowNodeInstanceTemplate.POSITION, SortOrder.ASC));
    try {
      return scroll(searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      throw new OperateRuntimeException(e);
    }
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

  private MvcResult postRequest(String requestUrl, Object query) throws Exception {
    MockHttpServletRequestBuilder request = post(requestUrl)
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());

    return mockMvcTestRule.getMockMvc().perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(mockMvcTestRule.getContentType()))
        .andReturn();
  }

}
