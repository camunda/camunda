/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllRunningQuery;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.operate.webapp.rest.BatchOperationRestService.BATCH_OPERATIONS_URL;
import static io.camunda.operate.webapp.rest.OperationRestService.OPERATION_URL;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.util.*;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.*;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.OperationTypeDto;
import io.camunda.operate.webapp.zeebe.operation.*;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class OperationZeebeIT extends OperateZeebeAbstractIT {

  private static final String QUERY_INSTANCES_URL = PROCESS_INSTANCE_URL;
  private static final String QUERY_BATCH_OPERATIONS = BATCH_OPERATIONS_URL;

  @Autowired private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Autowired private ResolveIncidentHandler updateRetriesHandler;

  @Autowired private UpdateVariableHandler updateVariableHandler;

  @Autowired private DeleteProcessDefinitionHandler deleteProcessDefinitionHandler;

  @Autowired private DeleteDecisionDefinitionHandler deleteDecisionDefinitionHandler;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private IncidentReader incidentReader;

  @Autowired private VariableReader variableReader;

  @Autowired private OperationReader operationReader;

  @Autowired private BatchOperationReader batchOperationReader;

  @Autowired private ListViewReader listViewReader;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  private Long initialBatchOperationMaxSize;

  @Override
  @Before
  public void before() {
    super.before();
    cancelProcessInstanceHandler.setCamundaClient(super.getClient());
    updateRetriesHandler.setCamundaClient(super.getClient());
    updateVariableHandler.setCamundaClient(super.getClient());
    deleteProcessDefinitionHandler.setCamundaClient(super.getClient());
    deleteDecisionDefinitionHandler.setCamundaClient(super.getClient());

    mockMvc = mockMvcTestRule.getMockMvc();
    initialBatchOperationMaxSize = operateProperties.getBatchOperationMaxSize();
    tester.deployProcess("demoProcess_v_2.bpmn");
    tester.deployProcess("execution-listener.bpmn");
  }

  @Override
  @After
  public void after() {
    operateProperties.setBatchOperationMaxSize(initialBatchOperationMaxSize);

    super.after();
  }

  @Test
  public void testBatchOperationsPersisted() throws Exception {
    // given
    final int instanceCount = 10;
    for (int i = 0; i < instanceCount; i++) {
      startDemoProcessInstance();
    }

    // when
    final String batchOperationName = "operationName";
    final ListViewQueryDto allRunningQuery = createGetAllRunningQuery();
    final MvcResult mvcResult =
        postBatchOperationWithOKResponse(
            allRunningQuery, OperationType.CANCEL_PROCESS_INSTANCE, batchOperationName);

    // then
    final BatchOperationDto[] batchOperations =
        postBatchOperationsRequestViaRest(new BatchOperationRequestDto().setPageSize(10));
    assertThat(batchOperations).hasSize(1);

    final BatchOperationDto batchOperationDto = batchOperations[0];
    assertThat(batchOperationDto.getType()).isEqualTo(OperationTypeDto.CANCEL_PROCESS_INSTANCE);
    assertThat(batchOperationDto.getName()).isEqualTo(batchOperationName);
    assertThat(batchOperationDto.getInstancesCount()).isEqualTo(10);
    assertThat(batchOperationDto.getOperationsTotalCount()).isEqualTo(10);
    assertThat(batchOperationDto.getOperationsFinishedCount()).isEqualTo(0);
    assertThat(batchOperationDto.getStartDate()).isNotNull();
    assertThat(batchOperationDto.getEndDate()).isNull();

    final BatchOperationDto batchOperationResponse =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(batchOperationResponse)
        .usingRecursiveComparison()
        // ignore because the Dto is created from an Entity (which doesn't have SortValues)
        .ignoringFields("sortValues")
        .isEqualTo(batchOperationDto);

    final ListViewResponseDto response = getProcessInstances(allRunningQuery);
    assertThat(response.getProcessInstances()).hasSize(instanceCount);
    assertThat(response.getProcessInstances()).allMatch(pi -> pi.isHasActiveOperation());
    assertThat(response.getProcessInstances())
        .flatExtracting("operations")
        .extracting(OperationTemplate.TYPE)
        .containsOnly(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(response.getProcessInstances())
        .flatExtracting("operations")
        .extracting(OperationTemplate.STATE)
        .containsOnly(OperationState.SCHEDULED);
  }

  @Test
  public void testOperationPersisted() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    // when
    final MvcResult mvcResult =
        postOperationWithOKResponse(
            processInstanceKey,
            new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE));

    // then
    final BatchOperationDto[] batchOperations =
        postBatchOperationsRequestViaRest(new BatchOperationRequestDto().setPageSize(10));
    assertThat(batchOperations).hasSize(1);

    final BatchOperationDto batchOperationDto = batchOperations[0];
    assertThat(batchOperationDto.getType()).isEqualTo(OperationTypeDto.CANCEL_PROCESS_INSTANCE);
    assertThat(batchOperationDto.getName()).isNull();
    assertThat(batchOperationDto.getInstancesCount()).isEqualTo(1);
    assertThat(batchOperationDto.getOperationsTotalCount()).isEqualTo(1);
    assertThat(batchOperationDto.getOperationsFinishedCount()).isEqualTo(0);
    assertThat(batchOperationDto.getStartDate()).isNotNull();
    assertThat(batchOperationDto.getEndDate()).isNull();

    final BatchOperationDto batchOperationResponse =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(batchOperationResponse)
        .usingRecursiveComparison()
        // ignore because the Dto is created from an Entity (which doesn't have SortValues)
        .ignoringFields("sortValues")
        .isEqualTo(batchOperationDto);

    final ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isTrue();
    assertThat(processInstance.getOperations()).hasSize(1);
    assertThat(processInstance.getOperations().get(0).getType())
        .isEqualTo(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(processInstance.getOperations().get(0).getState())
        .isEqualTo(OperationState.SCHEDULED);
    assertThat(processInstance.getOperations().get(0).getId()).isNotNull();
  }

  @Test
  public void testSeveralOperationsPersistedForSeveralIncidents() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstanceWithIncidents();
    searchTestRule.processAllRecordsAndWait(incidentsAreActiveCheck, processInstanceKey, 2);
    final List<IncidentEntity> incidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);

    // when
    final MvcResult mvcResult =
        postOperationWithOKResponse(
            processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));

    // then
    final BatchOperationDto[] batchOperations =
        postBatchOperationsRequestViaRest(new BatchOperationRequestDto().setPageSize(10));
    assertThat(batchOperations).hasSize(1);

    final BatchOperationDto batchOperationDto = batchOperations[0];
    assertThat(batchOperationDto.getType()).isEqualTo(OperationTypeDto.RESOLVE_INCIDENT);
    assertThat(batchOperationDto.getName()).isNull();
    assertThat(batchOperationDto.getInstancesCount()).isEqualTo(1);
    assertThat(batchOperationDto.getOperationsTotalCount()).isEqualTo(2);
    assertThat(batchOperationDto.getOperationsFinishedCount()).isEqualTo(0);
    assertThat(batchOperationDto.getStartDate()).isNotNull();
    assertThat(batchOperationDto.getEndDate()).isNull();

    final BatchOperationDto batchOperationResponse =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(batchOperationResponse)
        .usingRecursiveComparison()
        // ignore because the Dto is created from an Entity (which doesn't have SortValues)
        .ignoringFields("sortValues")
        .isEqualTo(batchOperationDto);

    final List<OperationEntity> operations =
        operationReader.getOperationsByProcessInstanceKey(processInstanceKey);
    assertThat(operations).hasSize(2);
    assertThat(operations)
        .extracting(OperationTemplate.TYPE)
        .containsOnly(OperationType.RESOLVE_INCIDENT);
    assertThat(operations)
        .extracting(OperationTemplate.INCIDENT_KEY)
        .containsExactlyInAnyOrder(
            Long.valueOf(incidents.get(0).getId()), Long.valueOf(incidents.get(1).getId()));
    assertThat(operations)
        .extracting(OperationTemplate.STATE)
        .containsOnly(OperationState.SCHEDULED);
  }

  @Test
  public void testNoOperationsPersistedForNoIncidents() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    // when
    final MvcResult mvcResult =
        postOperationWithOKResponse(
            processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));

    final BatchOperationEntity batchOperationResponse =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(batchOperationResponse.getInstancesCount()).isEqualTo(1);
    assertThat(batchOperationResponse.getOperationsTotalCount()).isEqualTo(0);
    final ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isFalse();
    assertThat(processInstance.getOperations()).hasSize(0);

    final List<OperationEntity> operations =
        operationReader.getOperationsByProcessInstanceKey(processInstanceKey);
    assertThat(operations).hasSize(0);

    final List<BatchOperationEntity> batchOperations = operationReader.getBatchOperations(10);
    assertThat(batchOperations).hasSize(1);
    assertThat(batchOperations.get(0).getEndDate()).isNotNull();
  }

  @Test
  public void testNoOperationsPersistedForNoProcessInstances() throws Exception {
    // given
    // no process instances

    // when
    final MvcResult mvcResult =
        postBatchOperationWithOKResponse(
            createGetAllRunningQuery(), OperationType.CANCEL_PROCESS_INSTANCE);

    // then
    final BatchOperationEntity batchOperationResponse =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(batchOperationResponse.getInstancesCount()).isEqualTo(0);
    assertThat(batchOperationResponse.getOperationsTotalCount()).isEqualTo(0);

    final List<BatchOperationEntity> batchOperations = operationReader.getBatchOperations(10);
    assertThat(batchOperations).hasSize(1);
    assertThat(batchOperations.get(0).getEndDate()).isNotNull();
  }

  @Test
  public void testResolveIncidentExecutedOnOneInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    failTaskWithNoRetriesLeft("taskA", processInstanceKey, "Some error");

    // when
    // we call RESOLVE_INCIDENT operation on instance
    postOperationWithOKResponse(
        processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));

    // and execute the operation
    executeOneBatch();

    // then
    // before we process messages from Zeebe, the state of the operation must be SENT
    ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);

    assertThat(processInstance.isHasActiveOperation()).isEqualTo(true);
    assertThat(processInstance.getOperations()).hasSize(1);
    OperationDto operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();

    // check incidents
    final List<IncidentDto> incidents =
        incidentReader
            .getIncidentsByProcessInstanceId(String.valueOf(processInstanceKey))
            .getIncidents();
    assertThat(incidents).hasSize(1);
    assertThat(incidents.get(0).isHasActiveOperation()).isEqualTo(true);
    final OperationDto lastOperation = incidents.get(0).getLastOperation();
    assertThat(lastOperation).isNotNull();
    assertThat(lastOperation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(lastOperation.getState()).isEqualTo(OperationState.SENT);
    assertThat(lastOperation.getId()).isNotNull();

    // after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    // elasticsearchTestRule.processAllEvents(8);
    searchTestRule.processAllRecordsAndWait(noActivitiesHaveIncident, processInstanceKey);
    processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstance.getOperations()).hasSize(1);
    operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getId()).isNotNull();
    // assert that incident is resolved
    assertThat(processInstance.getState()).isEqualTo(ProcessInstanceStateDto.ACTIVE);
  }

  @Test
  public void testResolveIncidentForExecutionListener() throws Exception {

    // given
    final Long processInstanceKey =
        tester
            .startProcessInstance("execution-listener-process")
            .waitUntil()
            .flowNodeIsActive("script-task")
            .getProcessInstanceKey();
    failTaskWithNoRetriesLeft("listener1", processInstanceKey, "Some error");

    // we call RESOLVE_INCIDENT operation on instance
    postOperationWithOKResponse(
        processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));

    // when
    // we execute the operation
    executeOneBatch();

    // then
    // process all Zeebe records
    searchTestRule.processAllRecordsAndWait(noActivitiesHaveIncident, processInstanceKey);

    final List<IncidentDto> incidents =
        incidentReader
            .getIncidentsByProcessInstanceId(String.valueOf(processInstanceKey))
            .getIncidents();
    // the incident has been resolved
    assertThat(incidents).isEmpty();
  }

  @Test
  public void testUpdateVariableOnProcessInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    // when
    // we call UPDATE_VARIABLE operation on instance
    final String varName = "a";
    final String newVarValue = "\"newValue\"";
    postUpdateVariableOperation(processInstanceKey, varName, newVarValue);
    searchTestRule.refreshOperateSearchIndices();

    // then variable with new value is returned
    List<VariableDto> variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, varName, newVarValue, true);

    // when execute the operation
    executeOneBatch();

    // then
    // before we process messages from Zeebe, the state of the operation must be SENT
    ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);

    assertThat(processInstance.isHasActiveOperation()).isEqualTo(true);
    assertThat(processInstance.getOperations()).hasSize(1);
    OperationDto operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();

    // after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    // elasticsearchTestRule.processAllEvents(2);
    searchTestRule.processAllRecordsAndWait(
        operationsByProcessInstanceAreCompleted, processInstanceKey);
    processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstance.getOperations()).hasSize(1);
    operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);

    // check variables
    variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, varName, newVarValue, false);

    // check batch operation progress
    final BatchOperationDto[] batchOperations =
        postBatchOperationsRequestViaRest(new BatchOperationRequestDto().setPageSize(10));
    assertThat(batchOperations).hasSize(1);

    final BatchOperationDto batchOperationDto = batchOperations[0];
    assertThat(batchOperationDto.getType()).isEqualTo(OperationTypeDto.UPDATE_VARIABLE);
    assertThat(batchOperationDto.getOperationsFinishedCount()).isEqualTo(1);
    assertThat(batchOperationDto.getEndDate()).isNotNull();
  }

  private List<VariableDto> getVariables(final Long processInstanceKey, final Long scopeKey) {
    return variableReader.getVariables(
        String.valueOf(processInstanceKey),
        new VariableRequestDto().setScopeId(String.valueOf(scopeKey)));
  }

  @Test
  public void testAddVariableOnProcessInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    // TC1 when we call UPDATE_VARIABLE operation on instance
    final String newVar1Name = "newVar1";
    final String newVar1Value = "\"newValue1\"";
    final String batchOperationId1 =
        postAddVariableOperation(processInstanceKey, newVar1Name, newVar1Value);
    final String newVar2Name = "newVar2";
    final String newVar2Value = "\"newValue2\"";
    final String batchOperationId2 =
        postAddVariableOperation(processInstanceKey, newVar2Name, newVar2Value);
    searchTestRule.refreshOperateSearchIndices();

    // then
    // new variables are not yet returned (OPE-1284)
    List<VariableDto> variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, "a", "\"b\"", false);
    // operations are in SCHEDULED state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.SCHEDULED);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.SCHEDULED);

    // TC2 execute the operations
    executeOneBatch();

    // then - before we process messages from Zeebe
    // variables are still not returned
    variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, "a", "\"b\"", false);
    // operations are in SENT state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.SENT);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.SENT);

    // TC3 after we process messages from Zeebe, variables must have hasActiveOperation = false
    searchTestRule.processAllRecordsAndWait(
        operationsByProcessInstanceAreCompleted, processInstanceKey);

    // then
    // all three variables are returned
    variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, false);
    assertVariable(variables, newVar2Name, newVar2Value, false);
    // operations are in COMPLETED state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.COMPLETED);
  }

  @Test
  public void testAddVariableOnTask() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    tester.waitUntil().variableExists("foo");
    final Long taskAId = getFlowNodeInstanceId(processInstanceKey, "taskA");

    // TC1 we call UPDATE_VARIABLE operation on instance
    final String newVar1Name = "newVar1";
    final String newVar1Value = "\"newValue1\"";
    final String batchOperationId1 =
        postAddVariableOperation(processInstanceKey, taskAId, newVar1Name, newVar1Value);
    final String newVar2Name = "newVar2";
    final String newVar2Value = "\"newValue2\"";
    final String batchOperationId2 =
        postAddVariableOperation(processInstanceKey, taskAId, newVar2Name, newVar2Value);
    searchTestRule.refreshOperateSearchIndices();

    // then
    // new variables are not yet returned (OPE-1284)
    List<VariableDto> variables = getVariables(processInstanceKey, taskAId);
    assertThat(variables).hasSize(1);
    assertVariable(variables, "foo", "\"b\"", false);
    // operations are in SCHEDULED state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.SCHEDULED);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.SCHEDULED);

    // TC2 execute the operations
    executeOneBatch();

    // then - before we process messages from Zeebe
    // new variables are not yet returned (OPE-1284)
    variables = getVariables(processInstanceKey, taskAId);
    assertThat(variables).hasSize(1);
    assertVariable(variables, "foo", "\"b\"", false);
    // operations are in SENT state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.SENT);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.SENT);

    // TC3 after we process messages from Zeebe, variables must have hasActiveOperation = false
    // elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    // elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey,
    // processInstanceKey, newVar2Name);
    searchTestRule.processAllRecordsAndWait(
        operationsByProcessInstanceAreCompleted, processInstanceKey);

    // then
    // all three variables are returned
    variables = getVariables(processInstanceKey, taskAId);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, false);
    assertVariable(variables, newVar2Name, newVar2Value, false);
    // operations are in COMPLETED state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.COMPLETED);
  }

  private OperationDto getOperation(final String batchOperationId) throws Exception {
    final MockHttpServletRequestBuilder getOperationRequest =
        get(String.format(OPERATION_URL + "?batchOperationId=%s", batchOperationId));

    final MvcResult mvcResult =
        mockMvc.perform(getOperationRequest).andExpect(status().isOk()).andReturn();
    final OperationDto[] operations =
        objectMapper.readValue(mvcResult.getResponse().getContentAsString(), OperationDto[].class);
    assertThat(operations.length).isEqualTo(1);
    return operations[0];
  }

  private void assertVariable(
      final List<VariableDto> variables,
      final String name,
      final String value,
      final Boolean hasActiveOperation) {
    final List<VariableDto> collect =
        variables.stream().filter(v -> v.getName().equals(name)).collect(Collectors.toList());
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
    final Long processInstanceKey = startDemoProcessInstance();

    // when
    // we call UPDATE_VARIABLE operation on task level
    final Long taskAId = getFlowNodeInstanceId(processInstanceKey, "taskA");
    final String varName = "foo";
    final String varValue = "\"newFooValue\"";
    postUpdateVariableOperation(processInstanceKey, taskAId, varName, varValue);

    // and execute the operation
    executeOneBatch();

    // then
    // before we process messages from Zeebe, the state of the operation must be SENT
    ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);

    assertThat(processInstance.isHasActiveOperation()).isEqualTo(true);
    assertThat(processInstance.getOperations()).hasSize(1);
    OperationDto operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();

    // after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    // elasticsearchTestRule.processAllEvents(2);
    searchTestRule.processAllRecordsAndWait(
        operationsByProcessInstanceAreCompleted, processInstanceKey);
    processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstance.getOperations()).hasSize(1);
    operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);

    // check variables
    final List<VariableDto> variables = getVariables(processInstanceKey, taskAId);
    assertThat(variables).hasSize(1);
    assertThat(variables.get(0).getName()).isEqualTo(varName);
    assertThat(variables.get(0).getValue()).isEqualTo(varValue);
  }

  protected Long getFlowNodeInstanceId(final Long processInstanceKey, final String activityId) {
    final List<FlowNodeInstanceEntity> allActivityInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    final Optional<FlowNodeInstanceEntity> first =
        allActivityInstances.stream()
            .filter(ai -> ai.getFlowNodeId().equals(activityId))
            .findFirst();
    assertThat(first.isPresent()).isTrue();
    return Long.valueOf(first.get().getId());
  }

  @Test
  public void testCancelExecutedOnOneInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    // when
    // we call CANCEL_PROCESS_INSTANCE operation on instance
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(Collections.singletonList(processInstanceKey.toString()));
    postBatchOperationWithOKResponse(processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE);

    // and execute the operation
    executeOneBatch();

    // then
    // before we process messages from Zeebe, the state of the operation must be SENT
    searchTestRule.refreshSerchIndexes();
    ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);

    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation())
        .isEqualTo(true);
    assertThat(processInstances.getProcessInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = processInstances.getProcessInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();

    // after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    // elasticsearchTestRule.refreshIndexesInElasticsearch();
    processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation())
        .isEqualTo(false);
    assertThat(processInstances.getProcessInstances().get(0).getOperations()).hasSize(1);
    operation = processInstances.getProcessInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    // assert that process is canceled
    assertThat(processInstances.getProcessInstances().get(0).getState())
        .isEqualTo(ProcessInstanceStateDto.CANCELED);

    // check batch operation progress
    final BatchOperationDto[] batchOperations =
        postBatchOperationsRequestViaRest(new BatchOperationRequestDto().setPageSize(10));
    assertThat(batchOperations).hasSize(1);

    final BatchOperationDto batchOperationDto = batchOperations[0];
    assertThat(batchOperationDto.getType()).isEqualTo(OperationTypeDto.CANCEL_PROCESS_INSTANCE);
    assertThat(batchOperationDto.getOperationsFinishedCount()).isEqualTo(1);
    assertThat(batchOperationDto.getEndDate()).isNotNull();

    // check batch operation id stored in process instance
    final List<ProcessInstanceForListViewEntity> processInstanceEntities =
        getProcessInstanceEntities(processInstanceQuery);
    assertThat(processInstanceEntities).hasSize(1);
    assertThat(processInstanceEntities.get(0).getBatchOperationIds())
        .containsExactly(batchOperationDto.getId());
  }

  private List<ProcessInstanceForListViewEntity> getProcessInstanceEntities(
      final ListViewQueryDto processInstanceQuery) {
    final ListViewRequestDto request = new ListViewRequestDto(processInstanceQuery);
    return listViewReader.queryListView(request, new ListViewResponseDto());
  }

  @Test
  public void testTwoResolveIncidentOperationsOnOneInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    failTaskWithNoRetriesLeft("taskA", processInstanceKey, "Some error");

    // when we call RESOLVE_INCIDENT operation two times on one instance
    postOperationWithOKResponse(
        processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT)); // #1
    postOperationWithOKResponse(
        processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT)); // #2

    // and execute the operation
    executeOneBatch();

    // then
    // the state of one operation is COMPLETED and of the other - FAILED
    searchTestRule.processAllRecordsAndWait(noActivitiesHaveIncident, processInstanceKey);

    final ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    final List<OperationDto> operations = processInstance.getOperations();
    assertThat(operations).hasSize(2);
    assertThat(operations)
        .filteredOn(op -> op.getState().equals(OperationState.COMPLETED))
        .hasSize(1);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.FAILED)).hasSize(1);

    // check incidents
    final List<IncidentDto> incidents =
        incidentReader
            .getIncidentsByProcessInstanceId(String.valueOf(processInstanceKey))
            .getIncidents();
    assertThat(incidents).hasSize(0);

    // check batch operation progress
    final BatchOperationDto[] batchOperations =
        postBatchOperationsRequestViaRest(new BatchOperationRequestDto().setPageSize(10));
    assertThat(batchOperations).hasSize(2);
    BatchOperationDto batchOperationDto = batchOperations[0];
    assertThat(batchOperationDto.getType()).isEqualTo(OperationTypeDto.RESOLVE_INCIDENT);
    assertThat(batchOperationDto.getOperationsFinishedCount()).isEqualTo(1);
    assertThat(batchOperationDto.getEndDate()).isNotNull();
    batchOperationDto = batchOperations[1];
    assertThat(batchOperationDto.getType()).isEqualTo(OperationTypeDto.RESOLVE_INCIDENT);
    assertThat(batchOperationDto.getOperationsFinishedCount()).isEqualTo(1);
    assertThat(batchOperationDto.getEndDate()).isNotNull();
  }

  @Test
  public void testSeveralCancelOperationsOnOneInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    // when we call CANCEL_PROCESS_INSTANCE operation three times on one instance
    postOperationWithOKResponse(
        processInstanceKey,
        new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE)); // #1
    postOperationWithOKResponse(
        processInstanceKey,
        new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE)); // #2
    postOperationWithOKResponse(
        processInstanceKey,
        new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE)); // #3

    // and execute the operation
    executeOneBatch();

    // then
    // the state of one operation is COMPLETED and of the other - FAILED
    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);

    final ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    final List<OperationDto> operations = processInstance.getOperations();
    assertThat(operations).hasSize(3);
    assertThat(operations)
        .extracting("state")
        .containsAnyOf(OperationState.COMPLETED, OperationState.FAILED);
  }

  @Test
  public void testTwoDifferentOperationsOnOneInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    failTaskWithNoRetriesLeft("taskA", processInstanceKey, "Some error");

    // when we call CANCEL_PROCESS_INSTANCE and then RESOLVE_INCIDENT operation on one instance
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(Collections.singletonList(processInstanceKey.toString()));
    postOperationWithOKResponse(
        processInstanceKey,
        new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE)); // #1
    executeOneBatch();

    postOperationWithOKResponse(
        processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT)); // #2
    executeOneBatch();

    // then
    // the state of 1st operation is COMPLETED and the 2nd - FAILED
    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    searchTestRule.processAllRecordsAndWait(noActivitiesHaveIncident, processInstanceKey);
    // elasticsearchTestRule.refreshIndexesInElasticsearch();
    final ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation())
        .isEqualTo(false);
    final List<OperationDto> operations =
        processInstances.getProcessInstances().get(0).getOperations();
    assertThat(operations).hasSize(2);
    assertThat(operations)
        .filteredOn(op -> op.getState().equals(OperationState.COMPLETED))
        .hasSize(1)
        .anyMatch(op -> op.getType().equals(OperationType.CANCEL_PROCESS_INSTANCE));
    assertThat(operations)
        .filteredOn(op -> op.getState().equals(OperationState.FAILED))
        .hasSize(1)
        .anyMatch(op -> op.getType().equals(OperationType.RESOLVE_INCIDENT));
  }

  @Test
  public void testRetryOperationOnZeebeNotAvailable() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    zeebeRule.stop();

    // when we call CANCEL_PROCESS_INSTANCE and then RESOLVE_INCIDENT operation on one instance
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(Collections.singletonList(processInstanceKey.toString()));
    postOperationWithOKResponse(
        processInstanceKey,
        new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE)); // #1
    executeOneBatch();

    // then
    final ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation())
        .isEqualTo(true);
    final List<OperationDto> operations =
        processInstances.getProcessInstances().get(0).getOperations();
    assertThat(operations).hasSize(1);
    assertThat(operations)
        .filteredOn(op -> op.getState().equals(OperationState.LOCKED))
        .hasSize(1)
        .anyMatch(op -> op.getType().equals(OperationType.CANCEL_PROCESS_INSTANCE));
  }

  @Test
  public void testFailResolveIncidentBecauseOfNoIncidents() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    failTaskWithNoRetriesLeft("taskA", processInstanceKey, "some error");
    // we call RESOLVE_INCIDENT operation on instance
    postOperationWithOKResponse(
        processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));
    // resolve the incident before the operation is executed
    final IncidentEntity incident =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey).get(0);
    ZeebeTestUtil.resolveIncident(camundaClient, incident.getJobKey(), incident.getKey());

    // when
    // and execute the operation
    executeOneBatch();

    // then
    // the state of operation is FAILED, as there are no appropriate incidents
    final ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstance.getOperations()).hasSize(1);
    final OperationDto operation = processInstance.getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).contains("no such incident was found");
    assertThat(operation.getId()).isNotNull();

    // check incidents
    final List<IncidentDto> incidents =
        incidentReader
            .getIncidentsByProcessInstanceId(String.valueOf(processInstanceKey))
            .getIncidents();
    assertThat(incidents).hasSize(1);
    assertThat(incidents.get(0).isHasActiveOperation()).isEqualTo(false);
    final OperationDto lastOperation = incidents.get(0).getLastOperation();
    assertThat(lastOperation).isNotNull();
    assertThat(lastOperation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(lastOperation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(lastOperation.getErrorMessage()).contains("no such incident was found");
  }

  @Test
  public void testFailCancelOnCanceledInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    ZeebeTestUtil.cancelProcessInstance(super.getClient(), processInstanceKey);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);

    // when
    // we call CANCEL_PROCESS_INSTANCE operation on instance
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(Collections.singletonList(processInstanceKey.toString()));
    postBatchOperationWithOKResponse(processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE);

    // and execute the operation
    executeOneBatch();

    // then
    // the state of operation is FAILED, as there are no appropriate incidents
    final ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation())
        .isEqualTo(false);
    assertThat(processInstances.getProcessInstances().get(0).getOperations()).hasSize(1);
    final OperationDto operation =
        processInstances.getProcessInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .isEqualTo(
            "Unable to cancel CANCELED process instance. Instance must be in ACTIVE or INCIDENT state.");
    assertThat(operation.getId()).isNotNull();
  }

  @Test
  public void testFailCancelOnCompletedInstance() throws Exception {
    // given
    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId).startEvent().endEvent().done();
    deployProcess(startEndProcess, "startEndProcess.bpmn");
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, null);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey);
    // elasticsearchTestRule.refreshIndexesInElasticsearch();

    // when
    // we call CANCEL_PROCESS_INSTANCE operation on instance
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(Collections.singletonList(processInstanceKey.toString()));
    postBatchOperationWithOKResponse(processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE);

    // and execute the operation
    executeOneBatch();

    // then
    // the state of operation is FAILED, as the instance is in wrong state
    final ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation())
        .isEqualTo(false);
    assertThat(processInstances.getProcessInstances().get(0).getOperations()).hasSize(1);
    final OperationDto operation =
        processInstances.getProcessInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage())
        .isEqualTo(
            "Unable to cancel COMPLETED process instance. Instance must be in ACTIVE or INCIDENT state.");
    assertThat(operation.getId()).isNotNull();
  }

  @Test
  public void testFailAddVariableOperationAsVariableAlreadyExists() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    // when we call ADD_VARIABLE operation for the variable that already exists
    final String newVarName = "a";
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.ADD_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue("\"newValue\"");
    op.setVariableScopeId(ConversionUtils.toStringOrNull(processInstanceKey));
    final MvcResult mvcResult =
        postOperation(processInstanceKey, op, HttpURLConnection.HTTP_BAD_REQUEST);

    // then
    assertThat(mvcResult.getResolvedException().getMessage())
        .isEqualTo(String.format("Variable with the name \"%s\" already exists.", newVarName));
  }

  @Test
  public void testFailAddVariableOperationAsOperationAlreadyExists() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    // when we call ADD_VARIABLE operation for the first time
    final String newVarName = "newVar";
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.ADD_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue("\"newValue\"");
    op.setVariableScopeId(ConversionUtils.toStringOrNull(processInstanceKey));
    // then it succeeds
    postOperation(processInstanceKey, op, HttpURLConnection.HTTP_OK);

    // when we call the same operation for the second time, it fails
    final MvcResult mvcResult =
        postOperation(processInstanceKey, op, HttpURLConnection.HTTP_BAD_REQUEST);

    // then
    assertThat(mvcResult.getResolvedException().getMessage())
        .isEqualTo(String.format("Variable with the name \"%s\" already exists.", newVarName));
  }

  @Test
  public void testFailOperationAsTooManyInstances() throws Exception {
    // given
    operateProperties.setBatchOperationMaxSize(5L);

    final int instanceCount = 10;
    for (int i = 0; i < instanceCount; i++) {
      startDemoProcessInstance();
    }

    // when
    final MvcResult mvcResult =
        postBatchOperation(
            createGetAllRunningQuery(),
            OperationType.RESOLVE_INCIDENT,
            null,
            HttpStatus.SC_BAD_REQUEST);

    final String expectedErrorMsg =
        String.format(
            "Too many process instances are selected for batch operation. Maximum possible amount: %s",
            operateProperties.getBatchOperationMaxSize());
    assertThat(mvcResult.getResolvedException().getMessage()).contains(expectedErrorMsg);
  }

  @Test
  public void testDeleteProcessDefinitionDeletes() throws Exception {

    // given
    // process instances that complete execution
    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId).startEvent().endEvent().done();
    final Long processDefinitionKey = deployProcess(startEndProcess, "startEndProcess.bpmn");
    final Long processInstanceKey1 =
        ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, null);
    final Long processInstanceKey2 =
        ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, null);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey1);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey2);

    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(
                Arrays.asList(
                    String.valueOf(processInstanceKey1), String.valueOf(processInstanceKey2)));

    // when
    // we call DELETE_PROCESS_DEFINITION operation on process
    final BatchOperationEntity batchOperationEntity =
        deleteProcessWithOkResponse(String.valueOf(processDefinitionKey));

    // and execute the operation
    executeOneBatch();
    sleepFor(2000);

    // then
    // the state of operation is COMPLETED, and the instances are deleted
    final ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).isEmpty();
    final OperationDto operation = getOperation(batchOperationEntity.getId());
    assertThat(operation.getType()).isEqualTo(OperationType.DELETE_PROCESS_DEFINITION);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getErrorMessage()).isNull();
    assertThat(operation.getId()).isNotNull();
  }

  @Test
  public void testDeleteProcessDefinitionFailsWhenRunning() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey =
        tester
            .deployProcess("single-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processInstanceKey1 =
        tester
            .startProcessInstance("process", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final Long processInstanceKey2 =
        tester
            .startProcessInstance("process", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(
                Arrays.asList(
                    String.valueOf(processInstanceKey1), String.valueOf(processInstanceKey2)));

    // when
    // we call DELETE_PROCESS_DEFINITION operation on process
    final BatchOperationEntity batchOperationEntity =
        deleteProcessWithOkResponse(String.valueOf(processDefinitionKey));

    // and execute the operation
    executeOneBatch();
    sleepFor(2000);

    // then
    // the state of operation is FAILED, and the instances are not deleted
    final ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).size().isEqualTo(2);
    final OperationDto operation = getOperation(batchOperationEntity.getId());
    assertThat(operation.getType()).isEqualTo(OperationType.DELETE_PROCESS_DEFINITION);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).contains("Process instances still running.");
    assertThat(operation.getId()).isNotNull();
  }

  @Test
  public void testDeleteDecisionDefinitionDeletes() throws Exception {

    // given
    deployDecisionRequirements();
    deployProcessWithDecision();
    final String bpmnProcessId = "invoice_decision";
    final String payload = "{\"amount\": 1200, \"invoiceCategory\": \"Travel Expenses\"}";
    final Long processInstanceKey1 =
        ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, payload);
    final Long processInstanceKey2 =
        ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, payload);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey1);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey2);
    List<DecisionInstanceEntity> decisionInstanceEntities =
        searchAllDocuments(decisionInstanceTemplate.getAlias(), DecisionInstanceEntity.class);
    final String decisionDefinitionId = decisionInstanceEntities.get(0).getDecisionDefinitionId();

    // when
    // we call DELETE_DECISION_DEFINITION operation
    final BatchOperationEntity batchOperationEntity =
        deleteDecisionWithOkResponse(String.valueOf(decisionDefinitionId));

    // and execute the operation
    executeOneBatch();
    sleepFor(2000);

    // then
    // the state of operation is COMPLETED, and the instances are deleted
    decisionInstanceEntities =
        searchAllDocuments(decisionInstanceTemplate.getAlias(), DecisionInstanceEntity.class);
    assertThat(decisionInstanceEntities).isEmpty();
    final OperationDto operation = getOperation(batchOperationEntity.getId());
    assertThat(operation.getType()).isEqualTo(OperationType.DELETE_DECISION_DEFINITION);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getErrorMessage()).isNull();
    assertThat(operation.getId()).isNotNull();
  }

  protected void deployDecisionRequirements() {
    tester.deployDecision("invoiceBusinessDecisions_v_1.dmn").waitUntil().decisionsAreDeployed(2);
  }

  protected Long deployProcessWithDecision() {
    final Long procDefinitionKey =
        tester
            .deployProcess("invoice_decision.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    return procDefinitionKey;
  }

  protected Long startProcessWithDecision(final String payload) {
    final Long procInstanceKey =
        tester
            .startProcessInstance("invoice_decision", payload)
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    return procInstanceKey;
  }

  private long startDemoProcessInstance() {
    final String processId = "demoProcess";
    return tester
        .startProcessInstance(processId, "{\"a\": \"b\"}")
        .waitUntil()
        .flowNodeIsActive("taskA")
        .getProcessInstanceKey();
  }

  private long startDemoProcessInstanceWithIncidents() {
    final long processInstanceKey = startDemoProcessInstance();
    failTaskWithNoRetriesLeft("taskA", processInstanceKey, "some error");
    failTaskWithNoRetriesLeft("taskD", processInstanceKey, "some error");
    return processInstanceKey;
  }

  private ListViewResponseDto getProcessInstances(final ListViewQueryDto query) throws Exception {
    final ListViewRequestDto request = new ListViewRequestDto(query);
    request.setPageSize(100);
    final MockHttpServletRequestBuilder getProcessInstancesRequest =
        post(query())
            .content(mockMvcTestRule.json(request))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvc
            .perform(getProcessInstancesRequest)
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();

    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
  }

  private String query() {
    return QUERY_INSTANCES_URL;
  }

  private BatchOperationDto[] postBatchOperationsRequestViaRest(
      final BatchOperationRequestDto query) throws Exception {
    final MvcResult mvcResult = postRequest(QUERY_BATCH_OPERATIONS, query);
    return objectMapper.readValue(
        mvcResult.getResponse().getContentAsString(), BatchOperationDto[].class);
  }
}
