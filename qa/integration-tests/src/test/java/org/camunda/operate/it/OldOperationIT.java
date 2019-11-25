/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.util.Collections;
import org.apache.http.HttpStatus;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.webapp.es.reader.ActivityInstanceReader;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.es.reader.OperationReader;
import org.camunda.operate.webapp.es.reader.VariableReader;
import org.camunda.operate.webapp.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.rest.dto.OperationDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.webapp.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.webapp.rest.dto.oldoperation.BatchOperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.OperationResponseDto;
import org.camunda.operate.webapp.zeebe.operation.CancelWorkflowInstanceHandler;
import org.camunda.operate.webapp.zeebe.operation.ResolveIncidentHandler;
import org.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.operate.webapp.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Deprecated // OPE-786
public class OldOperationIT extends OperateZeebeIntegrationTest {

  private static final String QUERY_INSTANCES_URL = WORKFLOW_INSTANCE_URL;

  @Autowired
  private CancelWorkflowInstanceHandler cancelWorkflowInstanceHandler;

  @Autowired
  private ResolveIncidentHandler updateRetriesHandler;

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ActivityInstanceReader activityInstanceReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private VariableReader variableReader;

  @Autowired
  private OperationReader operationReader;

  private Long initialBatchOperationMaxSize;

  @Before
  public void before() {
    super.before();

    try {
      FieldSetter.setField(cancelWorkflowInstanceHandler, CancelWorkflowInstanceHandler.class.getDeclaredField("zeebeClient"), super.getClient());
      FieldSetter.setField(updateRetriesHandler, ResolveIncidentHandler.class.getDeclaredField("zeebeClient"), super.getClient());
      FieldSetter.setField(updateVariableHandler, UpdateVariableHandler.class.getDeclaredField("zeebeClient"), super.getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }

    mockMvc = mockMvcTestRule.getMockMvc();
    initialBatchOperationMaxSize = operateProperties.getBatchOperationMaxSize();
    deployWorkflow("demoProcess_v_2.bpmn");
  }

  @After
  public void after() {    
    operateProperties.setBatchOperationMaxSize(initialBatchOperationMaxSize);
    
    //super.after();
  }

  @Test
  public void testBatchOperationsPersisted() throws Exception {
    // given
    final int instanceCount = 10;
    for (int i = 0; i<instanceCount; i++) {
      startDemoWorkflowInstance();
    }

    //when
    final ListViewQueryDto allRunningQuery = ListViewQueryDto.createAllRunning();
    final MvcResult mvcResult = postBatchOperationWithOKResponse(allRunningQuery, OperationType.CANCEL_WORKFLOW_INSTANCE);

    //then
    final OperationResponseDto operationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<OperationResponseDto>() {});
    assertThat(operationResponse.getCount()).isEqualTo(10);

    ListViewResponseDto response = getWorkflowInstances(allRunningQuery);
    assertThat(response.getWorkflowInstances()).hasSize(instanceCount);
    assertThat(response.getWorkflowInstances()).allMatch(wi -> wi.isHasActiveOperation() == true);
    assertThat(response.getWorkflowInstances()).flatExtracting("operations").extracting(OperationTemplate.TYPE).containsOnly(OperationType.CANCEL_WORKFLOW_INSTANCE);
    assertThat(response.getWorkflowInstances()).flatExtracting("operations").extracting(OperationTemplate.STATE).containsOnly(
      OperationState.SCHEDULED);
    assertThat(response.getWorkflowInstances()).flatExtracting("operations").extracting(OperationTemplate.START_DATE).doesNotContainNull();
    assertThat(response.getWorkflowInstances()).flatExtracting("operations").extracting(OperationTemplate.END_DATE).containsOnlyNulls();
  }

  @Test
  public void testNoOperationsPersistedForNoWorkflowInstances() throws Exception {
    // given
    //no workflow instances

    //when
    final MvcResult mvcResult = postBatchOperationWithOKResponse(ListViewQueryDto.createAllRunning(), OperationType.CANCEL_WORKFLOW_INSTANCE);

    //then
    final OperationResponseDto operationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<OperationResponseDto>() {});
    assertThat(operationResponse.getCount()).isEqualTo(0);
    assertThat(operationResponse.getReason()).isEqualTo("No operations were scheduled.");

  }

  @Test
  public void testCancelExecutedOnOneInstance() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();

    //when
    //we call CANCEL_WORKFLOW_INSTANCE operation on instance
    final ListViewQueryDto workflowInstanceQuery = ListViewQueryDto.createAll();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceKey.toString()));
    postBatchOperationWithOKResponse(workflowInstanceQuery, OperationType.CANCEL_WORKFLOW_INSTANCE);

    //and execute the operation
    executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);

    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).isHasActiveOperation()).isEqualTo(true);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.CANCEL_WORKFLOW_INSTANCE);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    //elasticsearchTestRule.refreshIndexesInElasticsearch();
    workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).isHasActiveOperation()).isEqualTo(false);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.CANCEL_WORKFLOW_INSTANCE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();
    //assert that process is canceled
    assertThat(workflowInstances.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
  }

  @Test
  public void testFailCancelOnCanceledInstance() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();
    ZeebeTestUtil.cancelWorkflowInstance(super.getClient(), workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);

    //when
    //we call CANCEL_WORKFLOW_INSTANCE operation on instance
    final ListViewQueryDto workflowInstanceQuery = ListViewQueryDto.createAll();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceKey.toString()));
    postBatchOperationWithOKResponse(workflowInstanceQuery, OperationType.CANCEL_WORKFLOW_INSTANCE);

    //and execute the operation
    executeOneBatch();

    //then
    //the state of operation is FAILED, as there are no appropriate incidents
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).isHasActiveOperation()).isEqualTo(false);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).isEqualTo("Unable to cancel CANCELED workflow instance. Instance must be in ACTIVE or INCIDENT state.");
    assertThat(operation.getId()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();
  }

  @Test
  public void testFailCancelOnCompletedInstance() throws Exception {
    // given
    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
      Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent()
        .endEvent()
        .done();
    deployWorkflow(startEndProcess, "startEndProcess.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), bpmnProcessId, null);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);
    //elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //we call CANCEL_WORKFLOW_INSTANCE operation on instance
    final ListViewQueryDto workflowInstanceQuery = ListViewQueryDto.createAll();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceKey.toString()));
    postBatchOperationWithOKResponse(workflowInstanceQuery, OperationType.CANCEL_WORKFLOW_INSTANCE);

    //and execute the operation
    executeOneBatch();

    //then
    //the state of operation is FAILED, as the instance is in wrong state
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).isHasActiveOperation()).isEqualTo(false);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).isEqualTo("Unable to cancel COMPLETED workflow instance. Instance must be in ACTIVE or INCIDENT state.");
    assertThat(operation.getId()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();
  }

  @Test
  public void testFailOperationAsTooManyInstances() throws Exception {
    // given
    operateProperties.setBatchOperationMaxSize(5L);

    final int instanceCount = 10;
    for (int i = 0; i<instanceCount; i++) {
      startDemoWorkflowInstance();
    }

    //when
    final MvcResult mvcResult = postBatchOperation(ListViewQueryDto.createAllRunning(), OperationType.RESOLVE_INCIDENT, null, HttpStatus.SC_BAD_REQUEST);

    final String expectedErrorMsg = String
      .format("Too many workflow instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize());
    assertThat(mvcResult.getResolvedException().getMessage()).contains(expectedErrorMsg);
  }

  private ListViewResponseDto getWorkflowInstances(ListViewQueryDto query) throws Exception {
    ListViewRequestDto request = new ListViewRequestDto();
    request.addQuery(query);
    MockHttpServletRequestBuilder getWorkflowInstancesRequest =
      post(query(0, 100)).content(mockMvcTestRule.json(request))
        .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult =
      mockMvc.perform(getWorkflowInstancesRequest)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {
    });
  }
  
  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }

  @Override
  protected MvcResult postBatchOperationWithOKResponse(ListViewQueryDto query, OperationType operationType) throws Exception {
    return postBatchOperation(query, operationType, null, HttpStatus.SC_OK);
  }

  @Override
  protected MvcResult postBatchOperation(ListViewQueryDto query, OperationType operationType, String name, int expectedStatus) throws Exception {
    BatchOperationRequestDto batchOperationDto = createOldBatchOperationDto(operationType, query);
    MockHttpServletRequestBuilder postOperationRequest =
        post(WORKFLOW_INSTANCE_URL + "/operation")
            .content(mockMvcTestRule.json(batchOperationDto))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvc.perform(postOperationRequest)
            .andExpect(status().is(expectedStatus))
            .andReturn();
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return mvcResult;
  }

  private BatchOperationRequestDto createOldBatchOperationDto(OperationType operationType, ListViewQueryDto query) {
    BatchOperationRequestDto batchOperationDto = new BatchOperationRequestDto();
    batchOperationDto.getQueries().add(query);
    batchOperationDto.setOperationType(operationType);
    return batchOperationDto;
  }

}
