/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.OperationReader;
import org.camunda.operate.es.reader.VariableReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.rest.dto.OperationDto;
import org.camunda.operate.rest.dto.VariableDto;
import org.camunda.operate.rest.dto.incidents.IncidentDto;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.rest.dto.operation.OperationRequestDto;
import org.camunda.operate.rest.dto.operation.OperationResponseDto;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebe.operation.CancelWorkflowInstanceHandler;
import org.camunda.operate.zeebe.operation.ResolveIncidentHandler;
import org.camunda.operate.zeebe.operation.UpdateVariableHandler;
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

public class OperationIT extends OperateZeebeIntegrationTest {

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
  public void testOperationPersisted() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();

    //when
    final MvcResult mvcResult = postOperationWithOKResponse(workflowInstanceKey, new OperationRequestDto(OperationType.CANCEL_WORKFLOW_INSTANCE));

    //then
    final OperationResponseDto operationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<OperationResponseDto>() {});
    assertThat(operationResponse.getCount()).isEqualTo(1);
    final ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);
    assertThat(workflowInstance.isHasActiveOperation()).isTrue();
    assertThat(workflowInstance.getOperations()).hasSize(1);
    assertThat(workflowInstance.getOperations().get(0).getType()).isEqualTo(OperationType.CANCEL_WORKFLOW_INSTANCE);
    assertThat(workflowInstance.getOperations().get(0).getState()).isEqualTo(OperationState.SCHEDULED);
    assertThat(workflowInstance.getOperations().get(0).getId()).isNotNull();
    assertThat(workflowInstance.getOperations().get(0).getStartDate()).isNotNull();
    assertThat(workflowInstance.getOperations().get(0).getEndDate()).isNull();

  }

  @Test
  public void testSeveralOperationsPersistedForSeveralIncidents() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstanceWithIncidents();
    elasticsearchTestRule.processAllRecordsAndWait(incidentsAreActiveCheck, workflowInstanceKey, 2);
    final List<IncidentEntity> incidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);

    //when
    final MvcResult mvcResult = postOperationWithOKResponse(workflowInstanceKey, new OperationRequestDto(OperationType.RESOLVE_INCIDENT));

    //then
    final OperationResponseDto operationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<OperationResponseDto>() {});
    assertThat(operationResponse.getCount()).isEqualTo(2);
    final ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);
    assertThat(workflowInstance.isHasActiveOperation()).isTrue();
    assertThat(workflowInstance.getOperations()).hasSize(2);

    final List<OperationEntity> operations = operationReader.getOperationsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(operations).hasSize(2);
    assertThat(operations).extracting(OperationTemplate.TYPE).containsOnly(OperationType.RESOLVE_INCIDENT);
    assertThat(operations).extracting(OperationTemplate.INCIDENT_KEY).containsExactlyInAnyOrder(Long.valueOf(incidents.get(0).getId()), Long.valueOf(incidents.get(1).getId()));
    assertThat(operations).extracting(OperationTemplate.STATE).containsOnly(OperationState.SCHEDULED);
    assertThat(operations).extracting(OperationTemplate.START_DATE).doesNotContainNull();
    assertThat(operations).extracting(OperationTemplate.END_DATE).containsOnlyNulls();

  }

  @Test
  public void testNoOperationsPersistedForNoIncidents() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();

    //when
    final MvcResult mvcResult = postOperationWithOKResponse(workflowInstanceKey, new OperationRequestDto(OperationType.RESOLVE_INCIDENT));

    //then
    final OperationResponseDto operationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<OperationResponseDto>() {});
    assertThat(operationResponse.getCount()).isEqualTo(0);
    assertThat(operationResponse.getReason()).isEqualTo("No incidents found.");
    final ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);
    assertThat(workflowInstance.isHasActiveOperation()).isFalse();
    assertThat(workflowInstance.getOperations()).hasSize(0);

    final List<OperationEntity> operations = operationReader.getOperationsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(operations).hasSize(0);

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
  public void testResolveIncidentExecutedOnOneInstance() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA", workflowInstanceKey, "Some error");

    //when
    //we call RESOLVE_INCIDENT operation on instance
    postOperationWithOKResponse(workflowInstanceKey, new OperationRequestDto(OperationType.RESOLVE_INCIDENT));

    //and execute the operation
    executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);

    assertThat(workflowInstance.isHasActiveOperation()).isEqualTo(true);
    assertThat(workflowInstance.getOperations()).hasSize(1);
    OperationDto operation = workflowInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNull();

    //check incidents
    final List<IncidentDto> incidents = incidentReader.getIncidentsByWorkflowInstanceKey(workflowInstanceKey).getIncidents();
    assertThat(incidents).hasSize(1);
    assertThat(incidents.get(0).isHasActiveOperation()).isEqualTo(true);
    final OperationDto lastOperation = incidents.get(0).getLastOperation();
    assertThat(lastOperation).isNotNull();
    assertThat(lastOperation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(lastOperation.getState()).isEqualTo(OperationState.SENT);
    assertThat(lastOperation.getId()).isNotNull();
    assertThat(lastOperation.getStartDate()).isNotNull();
    assertThat(lastOperation.getEndDate()).isNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    //elasticsearchTestRule.processAllEvents(8);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, workflowInstanceKey);
    workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);
    assertThat(workflowInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(workflowInstance.getOperations()).hasSize(1);
    operation = workflowInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getId()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();
    //assert that incident is resolved
    assertThat(workflowInstance.getState()).isEqualTo(WorkflowInstanceStateDto.ACTIVE);
  }

  @Test
  public void testUpdateVariableOnWorkflowInstance() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();

    //when
    //we call UPDATE_VARIABLE operation on instance
    final String varName = "a";
    final String newVarValue = "\"newValue\"";
    postUpdateVariableOperation(workflowInstanceKey, varName, newVarValue);

    //then variable with new value is returned
    List<VariableDto> variables = variableReader.getVariables(workflowInstanceKey, workflowInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, varName, newVarValue, true);

    //when execute the operation
    executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);

    assertThat(workflowInstance.isHasActiveOperation()).isEqualTo(true);
    assertThat(workflowInstance.getOperations()).hasSize(1);
    OperationDto operation = workflowInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    //elasticsearchTestRule.processAllEvents(2);
    elasticsearchTestRule.processAllRecordsAndWait(operationsByWorkflowInstanceAreCompleted, workflowInstanceKey);
    workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);
    assertThat(workflowInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(workflowInstance.getOperations()).hasSize(1);
    operation = workflowInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();

    //check variables
    variables = variableReader.getVariables(workflowInstanceKey, workflowInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, varName, newVarValue, false);
  }

  @Test
  public void testAddVariableOnWorkflowInstance() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();

    //TC1 we call UPDATE_VARIABLE operation on instance
    final String newVar1Name = "newVar1";
    final String newVar1Value = "\"newValue1\"";
    postUpdateVariableOperation(workflowInstanceKey, newVar1Name, newVar1Value);
    final String newVar2Name = "newVar2";
    final String newVar2Value = "\"newValue2\"";
    postUpdateVariableOperation(workflowInstanceKey, newVar2Name, newVar2Value);

    //then new variables are returned
    List<VariableDto> variables = variableReader.getVariables(workflowInstanceKey, workflowInstanceKey);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, true);
    assertVariable(variables, newVar2Name, newVar2Value, true);

    //TC2 execute the operations
    executeOneBatch();

    //then - before we process messages from Zeebe, the state of the operation must be SENT - variables has still hasActiveOperation = true
    //then new variables are returned
    variables = variableReader.getVariables(workflowInstanceKey, workflowInstanceKey);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, true);
    assertVariable(variables, newVar2Name, newVar2Value, true);

    //TC3 after we process messages from Zeebe, variables must have hasActiveOperation = false
    //elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    elasticsearchTestRule.processAllRecordsAndWait(operationsByWorkflowInstanceAreCompleted, workflowInstanceKey);
    
    variables = variableReader.getVariables(workflowInstanceKey, workflowInstanceKey);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, false);
    assertVariable(variables, newVar2Name, newVar2Value, false);
  }

  @Test
  public void testAddVariableOnTask() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();
    final Long taskAId = getActivityInstanceId(workflowInstanceKey, "taskA");

    //TC1 we call UPDATE_VARIABLE operation on instance
    final String newVar1Name = "newVar1";
    final String newVar1Value = "\"newValue1\"";
    postUpdateVariableOperation(workflowInstanceKey, taskAId, newVar1Name, newVar1Value);
    final String newVar2Name = "newVar2";
    final String newVar2Value = "\"newValue2\"";
    postUpdateVariableOperation(workflowInstanceKey, taskAId, newVar2Name, newVar2Value);

    //then new variables are returned
    List<VariableDto> variables = variableReader.getVariables(workflowInstanceKey, taskAId);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, true);
    assertVariable(variables, newVar2Name, newVar2Value, true);

    //TC2 execute the operations
    executeOneBatch();

    //then - before we process messages from Zeebe, the state of the operation must be SENT - variables has still hasActiveOperation = true
    //then new variables are returned
    variables = variableReader.getVariables(workflowInstanceKey, taskAId);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, true);
    assertVariable(variables, newVar2Name, newVar2Value, true);

    //TC3 after we process messages from Zeebe, variables must have hasActiveOperation = false
    //elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    //elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, workflowInstanceKey, workflowInstanceKey, newVar2Name);
    elasticsearchTestRule.processAllRecordsAndWait(operationsByWorkflowInstanceAreCompleted, workflowInstanceKey);
    
    variables = variableReader.getVariables(workflowInstanceKey, taskAId);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, false);
    assertVariable(variables, newVar2Name, newVar2Value, false);
  }

  private void assertVariable(List<VariableDto> variables, String name, String value, Boolean hasActiveOperation) {
    final List<VariableDto> collect = variables.stream().filter(v -> v.getName().equals(name)).collect(Collectors.toList());
    assertThat(collect).hasSize(1);
    final VariableDto variable = collect.get(0);
    assertThat(variable.getValue()).isEqualTo(value);
    if (hasActiveOperation != null) {
      assertThat(variable.isHasActiveOperation()).isEqualTo(hasActiveOperation);
    }
  }

  @Test
  public void testUpdateVariableOnTask() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();

    //when
    //we call UPDATE_VARIABLE operation on task level
    final Long taskAId = getActivityInstanceId(workflowInstanceKey, "taskA");
    final String varName = "foo";
    final String varValue = "\"newFooValue\"";
    postUpdateVariableOperation(workflowInstanceKey, taskAId, varName, varValue);

    //and execute the operation
    executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);

    assertThat(workflowInstance.isHasActiveOperation()).isEqualTo(true);
    assertThat(workflowInstance.getOperations()).hasSize(1);
    OperationDto operation = workflowInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    //elasticsearchTestRule.processAllEvents(2);
    elasticsearchTestRule.processAllRecordsAndWait(operationsByWorkflowInstanceAreCompleted, workflowInstanceKey);
    workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);
    assertThat(workflowInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(workflowInstance.getOperations()).hasSize(1);
    operation = workflowInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();

    //check variables
    final List<VariableDto> variables = variableReader.getVariables(workflowInstanceKey, taskAId);
    assertThat(variables).hasSize(1);
    assertThat(variables.get(0).getName()).isEqualTo(varName);
    assertThat(variables.get(0).getValue()).isEqualTo(varValue);
  }

  protected Long getActivityInstanceId(Long workflowInstanceKey, String activityId) {
    final List<ActivityInstanceEntity> allActivityInstances = activityInstanceReader.getAllActivityInstances(workflowInstanceKey);
    final Optional<ActivityInstanceEntity> first = allActivityInstances.stream().filter(ai -> ai.getActivityId().equals(activityId)).findFirst();
    assertThat(first.isPresent()).isTrue();
    return Long.valueOf(first.get().getId());
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
    elasticsearchTestRule.refreshIndexesInElasticsearch();
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
  public void testTwoOperationsOnOneInstance() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA", workflowInstanceKey, "Some error");

    //when we call RESOLVE_INCIDENT operation two times on one instance
    postOperationWithOKResponse(workflowInstanceKey, new OperationRequestDto(OperationType.RESOLVE_INCIDENT));  //#1
    postOperationWithOKResponse(workflowInstanceKey, new OperationRequestDto(OperationType.RESOLVE_INCIDENT));  //#2

    //and execute the operation
    executeOneBatch();

    //then
    //the state of one operation is COMPLETED and of the other - FAILED
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, workflowInstanceKey);
    //Thread.sleep(1000L);  //sometimes the JOB RETRIES_UPDATED event is not there yet -> wait a little
    //elasticsearchTestRule.processAllEvents(2);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    final ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);
    final List<OperationDto> operations = workflowInstance.getOperations();
    assertThat(operations).hasSize(2);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.COMPLETED)).hasSize(1);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.FAILED)).hasSize(1);

    //check incidents
    final List<IncidentDto> incidents = incidentReader.getIncidentsByWorkflowInstanceKey(workflowInstanceKey).getIncidents();
    assertThat(incidents).hasSize(0);

  }

  @Test
  public void testTwoDifferentOperationsOnOneInstance() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA", workflowInstanceKey, "Some error");

    //when we call CANCEL_WORKFLOW_INSTANCE and then RESOLVE_INCIDENT operation on one instance
    final ListViewQueryDto workflowInstanceQuery = ListViewQueryDto.createAll();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceKey.toString()));
    postOperationWithOKResponse(workflowInstanceKey, new OperationRequestDto(OperationType.CANCEL_WORKFLOW_INSTANCE));  //#1
    executeOneBatch();

    postOperationWithOKResponse(workflowInstanceKey, new OperationRequestDto(OperationType.RESOLVE_INCIDENT));  //#2
    executeOneBatch();

    //then
    //the state of 1st operation is COMPLETED and the 2nd - FAILED
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, workflowInstanceKey);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).isHasActiveOperation()).isEqualTo(false);
    final List<OperationDto> operations = workflowInstances.getWorkflowInstances().get(0).getOperations();
    assertThat(operations).hasSize(2);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.COMPLETED)).hasSize(1)
        .anyMatch(op -> op.getType().equals(OperationType.CANCEL_WORKFLOW_INSTANCE));
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.FAILED)).hasSize(1)
      .anyMatch(op -> op.getType().equals(OperationType.RESOLVE_INCIDENT));

  }

  @Test
  public void testFailResolveIncidentBecauseOfNoIncidents() throws Exception {
    // given
    final Long workflowInstanceKey = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA", workflowInstanceKey, "some error");
    //we call RESOLVE_INCIDENT operation on instance
    postOperationWithOKResponse(workflowInstanceKey, new OperationRequestDto(OperationType.RESOLVE_INCIDENT));
    //resolve the incident before the operation is executed
    final IncidentEntity incident = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey).get(0);
    ZeebeTestUtil.resolveIncident(zeebeClient, incident.getJobKey(), incident.getKey());

    //when
    //and execute the operation
    executeOneBatch();

    //then
    //the state of operation is FAILED, as there are no appropriate incidents
    final ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);
    assertThat(workflowInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(workflowInstance.getOperations()).hasSize(1);
    OperationDto operation = workflowInstance.getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).contains("no such incident was found");
    assertThat(operation.getId()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();

    //check incidents
    final List<IncidentDto> incidents = incidentReader.getIncidentsByWorkflowInstanceKey(workflowInstanceKey).getIncidents();
    assertThat(incidents).hasSize(1);
    assertThat(incidents.get(0).isHasActiveOperation()).isEqualTo(false);
    final OperationDto lastOperation = incidents.get(0).getLastOperation();
    assertThat(lastOperation).isNotNull();
    assertThat(lastOperation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(lastOperation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(lastOperation.getErrorMessage()).contains("no such incident was found");
    assertThat(lastOperation.getStartDate()).isNotNull();
    assertThat(lastOperation.getEndDate()).isNotNull();
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
    elasticsearchTestRule.refreshIndexesInElasticsearch();

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
    final MvcResult mvcResult = postBatchOperation(ListViewQueryDto.createAllRunning(), OperationType.RESOLVE_INCIDENT, HttpStatus.SC_BAD_REQUEST);

    final String expectedErrorMsg = String
      .format("Too many workflow instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize());
    assertThat(mvcResult.getResolvedException().getMessage()).contains(expectedErrorMsg);
  }

  private long startDemoWorkflowInstanceWithIncidents() {
    final long workflowInstanceKey = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA", workflowInstanceKey, "some error");
    failTaskWithNoRetriesLeft("taskD", workflowInstanceKey, "some error");
    return workflowInstanceKey;
  }

  private ListViewResponseDto getWorkflowInstances(ListViewQueryDto query) throws Exception {
    ListViewRequestDto request = new ListViewRequestDto();
    request.getQueries().add(query);
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

}
