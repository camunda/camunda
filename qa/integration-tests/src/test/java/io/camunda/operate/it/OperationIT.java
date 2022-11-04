/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static io.camunda.operate.webapp.rest.OperationRestService.OPERATION_URL;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.es.reader.BatchOperationReader;
import io.camunda.operate.webapp.es.reader.IncidentReader;
import io.camunda.operate.webapp.es.reader.ListViewReader;
import io.camunda.operate.webapp.es.reader.OperationReader;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.es.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.operate.webapp.zeebe.operation.ResolveIncidentHandler;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.net.HttpURLConnection;
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

public class OperationIT extends OperateZeebeIntegrationTest {

  private static final String QUERY_INSTANCES_URL = PROCESS_INSTANCE_URL;

  @Autowired
  private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Autowired
  private ResolveIncidentHandler updateRetriesHandler;

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private VariableReader variableReader;

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private BatchOperationReader batchOperationReader;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private ObjectMapper objectMapper;

  private Long initialBatchOperationMaxSize;

  @Before
  public void before() {
    super.before();
    cancelProcessInstanceHandler.setZeebeClient(super.getClient());
    updateRetriesHandler.setZeebeClient(super.getClient());
    updateVariableHandler.setZeebeClient(super.getClient());

    mockMvc = mockMvcTestRule.getMockMvc();
    initialBatchOperationMaxSize = operateProperties.getBatchOperationMaxSize();
    tester.deployProcess("demoProcess_v_2.bpmn");
  }

  @After
  public void after() {
    operateProperties.setBatchOperationMaxSize(initialBatchOperationMaxSize);

    super.after();
  }

  @Test
  public void testBatchOperationsPersisted() throws Exception {
    // given
    final int instanceCount = 10;
    for (int i = 0; i<instanceCount; i++) {
      startDemoProcessInstance();
    }

    //when
    final String batchOperationName = "operationName";
    final ListViewQueryDto allRunningQuery = TestUtil.createGetAllRunningQuery();
    final MvcResult mvcResult = postBatchOperationWithOKResponse(allRunningQuery, OperationType.CANCEL_PROCESS_INSTANCE, batchOperationName);

    //then
    //TODO replace this with REST API call - OPE-790
    List<BatchOperationEntity> batchOperations = operationReader.getBatchOperations(10);
    assertThat(batchOperations).hasSize(1);

    BatchOperationEntity batchOperationEntity = batchOperations.get(0);
    assertThat(batchOperationEntity.getType()).isEqualTo(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(batchOperationEntity.getName()).isEqualTo(batchOperationName);
    assertThat(batchOperationEntity.getInstancesCount()).isEqualTo(10);
    assertThat(batchOperationEntity.getOperationsTotalCount()).isEqualTo(10);
    assertThat(batchOperationEntity.getOperationsFinishedCount()).isEqualTo(0);
    assertThat(batchOperationEntity.getUsername()).isEqualTo(DEFAULT_USER);
    assertThat(batchOperationEntity.getStartDate()).isNotNull();
    assertThat(batchOperationEntity.getEndDate()).isNull();

    final BatchOperationEntity batchOperationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(batchOperationResponse).isEqualTo(batchOperationEntity);

    ListViewResponseDto response = getProcessInstances(allRunningQuery);
    assertThat(response.getProcessInstances()).hasSize(instanceCount);
    assertThat(response.getProcessInstances()).allMatch(pi -> pi.isHasActiveOperation() == true);
    assertThat(response.getProcessInstances()).flatExtracting("operations").extracting(OperationTemplate.TYPE).containsOnly(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(response.getProcessInstances()).flatExtracting("operations").extracting(OperationTemplate.STATE).containsOnly(
      OperationState.SCHEDULED);
  }

  @Test
  public void testOperationPersisted() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    //when
    final MvcResult mvcResult = postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE));

    //then

    //TODO replace this with REST API call - OPE-790
    List<BatchOperationEntity> batchOperations = operationReader.getBatchOperations(10);
    assertThat(batchOperations).hasSize(1);

    BatchOperationEntity batchOperationEntity = batchOperations.get(0);
    assertThat(batchOperationEntity.getType()).isEqualTo(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(batchOperationEntity.getName()).isNull();
    assertThat(batchOperationEntity.getInstancesCount()).isEqualTo(1);
    assertThat(batchOperationEntity.getOperationsTotalCount()).isEqualTo(1);
    assertThat(batchOperationEntity.getOperationsFinishedCount()).isEqualTo(0);
    assertThat(batchOperationEntity.getUsername()).isEqualTo(DEFAULT_USER);
    assertThat(batchOperationEntity.getStartDate()).isNotNull();
    assertThat(batchOperationEntity.getEndDate()).isNull();

    final BatchOperationEntity batchOperationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(batchOperationResponse).isEqualTo(batchOperationEntity);

    final ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isTrue();
    assertThat(processInstance.getOperations()).hasSize(1);
    assertThat(processInstance.getOperations().get(0).getType()).isEqualTo(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(processInstance.getOperations().get(0).getState()).isEqualTo(OperationState.SCHEDULED);
    assertThat(processInstance.getOperations().get(0).getId()).isNotNull();
  }

  @Test
  public void testSeveralOperationsPersistedForSeveralIncidents() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstanceWithIncidents();
    elasticsearchTestRule.processAllRecordsAndWait(incidentsAreActiveCheck, processInstanceKey, 2);
    final List<IncidentEntity> incidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);

    //when
    final MvcResult mvcResult = postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));

    //then
    //TODO replace this with REST API call - OPE-790
    List<BatchOperationEntity> batchOperations = operationReader.getBatchOperations(10);
    assertThat(batchOperations).hasSize(1);

    BatchOperationEntity batchOperationEntity = batchOperations.get(0);
    assertThat(batchOperationEntity.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(batchOperationEntity.getName()).isNull();
    assertThat(batchOperationEntity.getInstancesCount()).isEqualTo(1);
    assertThat(batchOperationEntity.getOperationsTotalCount()).isEqualTo(2);
    assertThat(batchOperationEntity.getOperationsFinishedCount()).isEqualTo(0);
    assertThat(batchOperationEntity.getUsername()).isEqualTo(DEFAULT_USER);
    assertThat(batchOperationEntity.getStartDate()).isNotNull();
    assertThat(batchOperationEntity.getEndDate()).isNull();

    final BatchOperationEntity batchOperationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(batchOperationResponse).isEqualTo(batchOperationEntity);

    final List<OperationEntity> operations = operationReader.getOperationsByProcessInstanceKey(processInstanceKey);
    assertThat(operations).hasSize(2);
    assertThat(operations).extracting(OperationTemplate.TYPE).containsOnly(OperationType.RESOLVE_INCIDENT);
    assertThat(operations).extracting(OperationTemplate.INCIDENT_KEY).containsExactlyInAnyOrder(Long.valueOf(incidents.get(0).getId()), Long.valueOf(incidents.get(1).getId()));
    assertThat(operations).extracting(OperationTemplate.STATE).containsOnly(OperationState.SCHEDULED);

  }

  @Test
  public void testNoOperationsPersistedForNoIncidents() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    //when
    final MvcResult mvcResult = postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));

    final BatchOperationEntity batchOperationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(batchOperationResponse.getInstancesCount()).isEqualTo(1);
    assertThat(batchOperationResponse.getOperationsTotalCount()).isEqualTo(0);
    final ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isFalse();
    assertThat(processInstance.getOperations()).hasSize(0);

    final List<OperationEntity> operations = operationReader.getOperationsByProcessInstanceKey(processInstanceKey);
    assertThat(operations).hasSize(0);

    final List<BatchOperationEntity> batchOperations = operationReader.getBatchOperations(10);
    assertThat(batchOperations).hasSize(1);
    assertThat(batchOperations.get(0).getEndDate()).isNotNull();

  }

  @Test
  public void testNoOperationsPersistedForNoProcessInstances() throws Exception {
    // given
    //no process instances

    //when
    final MvcResult mvcResult = postBatchOperationWithOKResponse(TestUtil.createGetAllRunningQuery(), OperationType.CANCEL_PROCESS_INSTANCE);

    //then
    final BatchOperationEntity batchOperationResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
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

    //when
    //we call RESOLVE_INCIDENT operation on instance
    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));

    //and execute the operation
    executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);

    assertThat(processInstance.isHasActiveOperation()).isEqualTo(true);
    assertThat(processInstance.getOperations()).hasSize(1);
    OperationDto operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();

    //check incidents
    final List<IncidentDto> incidents = incidentReader.getIncidentsByProcessInstanceId(String.valueOf(processInstanceKey)).getIncidents();
    assertThat(incidents).hasSize(1);
    assertThat(incidents.get(0).isHasActiveOperation()).isEqualTo(true);
    final OperationDto lastOperation = incidents.get(0).getLastOperation();
    assertThat(lastOperation).isNotNull();
    assertThat(lastOperation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(lastOperation.getState()).isEqualTo(OperationState.SENT);
    assertThat(lastOperation.getId()).isNotNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    //elasticsearchTestRule.processAllEvents(8);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, processInstanceKey);
    processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstance.getOperations()).hasSize(1);
    operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getId()).isNotNull();
    //assert that incident is resolved
    assertThat(processInstance.getState()).isEqualTo(ProcessInstanceStateDto.ACTIVE);
  }

  @Test
  public void testUpdateVariableOnProcessInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    //when
    //we call UPDATE_VARIABLE operation on instance
    final String varName = "a";
    final String newVarValue = "\"newValue\"";
    postUpdateVariableOperation(processInstanceKey, varName, newVarValue);
    elasticsearchTestRule.refreshOperateESIndices();

    //then variable with new value is returned
    List<VariableDto> variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, varName, newVarValue, true);

    //when execute the operation
    executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);

    assertThat(processInstance.isHasActiveOperation()).isEqualTo(true);
    assertThat(processInstance.getOperations()).hasSize(1);
    OperationDto operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    //elasticsearchTestRule.processAllEvents(2);
    elasticsearchTestRule.processAllRecordsAndWait(operationsByProcessInstanceAreCompleted, processInstanceKey);
    processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstance.getOperations()).hasSize(1);
    operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);

    //check variables
    variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, varName, newVarValue, false);

    //check batch operation progress
    //TODO replace this with REST API call - OPE-790
    List<BatchOperationEntity> batchOperations = operationReader.getBatchOperations(10);
    assertThat(batchOperations).hasSize(1);

    BatchOperationEntity batchOperationEntity = batchOperations.get(0);
    assertThat(batchOperationEntity.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(batchOperationEntity.getOperationsFinishedCount()).isEqualTo(1);
    assertThat(batchOperationEntity.getEndDate()).isNotNull();
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

    //TC1 when we call UPDATE_VARIABLE operation on instance
    final String newVar1Name = "newVar1";
    final String newVar1Value = "\"newValue1\"";
    final String batchOperationId1 = postAddVariableOperation(processInstanceKey, newVar1Name, newVar1Value);
    final String newVar2Name = "newVar2";
    final String newVar2Value = "\"newValue2\"";
    final String batchOperationId2 = postAddVariableOperation(processInstanceKey, newVar2Name, newVar2Value);
    elasticsearchTestRule.refreshOperateESIndices();

    //then
    //new variables are not yet returned (OPE-1284)
    List<VariableDto> variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, "a", "\"b\"", false);
    //operations are in SCHEDULED state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.SCHEDULED);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.SCHEDULED);

    //TC2 execute the operations
    executeOneBatch();

    //then - before we process messages from Zeebe
    //variables are still not returned
    variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(1);
    assertVariable(variables, "a", "\"b\"", false);
    //operations are in SENT state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.SENT);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.SENT);

    //TC3 after we process messages from Zeebe, variables must have hasActiveOperation = false
    elasticsearchTestRule.processAllRecordsAndWait(operationsByProcessInstanceAreCompleted, processInstanceKey);

    //then
    //all three variables are returned
    variables = getVariables(processInstanceKey, processInstanceKey);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, false);
    assertVariable(variables, newVar2Name, newVar2Value, false);
    //operations are in COMPLETED state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.COMPLETED);
  }

  @Test
  public void testAddVariableOnTask() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    tester.waitUntil().variableExists("foo");
    final Long taskAId = getFlowNodeInstanceId(processInstanceKey, "taskA");

    //TC1 we call UPDATE_VARIABLE operation on instance
    final String newVar1Name = "newVar1";
    final String newVar1Value = "\"newValue1\"";
    final String batchOperationId1 = postAddVariableOperation(processInstanceKey, taskAId, newVar1Name, newVar1Value);
    final String newVar2Name = "newVar2";
    final String newVar2Value = "\"newValue2\"";
    final String batchOperationId2 = postAddVariableOperation(processInstanceKey, taskAId, newVar2Name, newVar2Value);
    elasticsearchTestRule.refreshOperateESIndices();

    //then
    //new variables are not yet returned (OPE-1284)
    List<VariableDto> variables = getVariables(processInstanceKey, taskAId);
    assertThat(variables).hasSize(1);
    assertVariable(variables, "foo", "\"b\"", false);
    //operations are in SCHEDULED state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.SCHEDULED);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.SCHEDULED);

    //TC2 execute the operations
    executeOneBatch();

    //then - before we process messages from Zeebe
    //new variables are not yet returned (OPE-1284)
    variables = getVariables(processInstanceKey, taskAId);
    assertThat(variables).hasSize(1);
    assertVariable(variables, "foo", "\"b\"", false);
    //operations are in SENT state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.SENT);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.SENT);

    //TC3 after we process messages from Zeebe, variables must have hasActiveOperation = false
    //elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    //elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, processInstanceKey, newVar2Name);
    elasticsearchTestRule.processAllRecordsAndWait(operationsByProcessInstanceAreCompleted, processInstanceKey);

    //then
    //all three variables are returned
    variables = getVariables(processInstanceKey, taskAId);
    assertThat(variables).hasSize(3);
    assertVariable(variables, newVar1Name, newVar1Value, false);
    assertVariable(variables, newVar2Name, newVar2Value, false);
    //operations are in COMPLETED state
    assertThat(getOperation(batchOperationId1).getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(getOperation(batchOperationId2).getState()).isEqualTo(OperationState.COMPLETED);
  }

  private OperationDto getOperation(final String batchOperationId) throws Exception {
    MockHttpServletRequestBuilder getOperationRequest =
        get(String.format(OPERATION_URL +  "?batchOperationId=%s", batchOperationId));

    final MvcResult mvcResult =
        mockMvc.perform(getOperationRequest)
            .andExpect(status().isOk())
            .andReturn();
    final OperationDto[] operations = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), OperationDto[].class);
    assertThat(operations.length).isEqualTo(1);
    return operations[0];
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
    final Long processInstanceKey = startDemoProcessInstance();

    //when
    //we call UPDATE_VARIABLE operation on task level
    final Long taskAId = getFlowNodeInstanceId(processInstanceKey, "taskA");
    final String varName = "foo";
    final String varValue = "\"newFooValue\"";
    postUpdateVariableOperation(processInstanceKey, taskAId, varName, varValue);

    //and execute the operation
    executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);

    assertThat(processInstance.isHasActiveOperation()).isEqualTo(true);
    assertThat(processInstance.getOperations()).hasSize(1);
    OperationDto operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    //elasticsearchTestRule.processAllEvents(2);
    elasticsearchTestRule.processAllRecordsAndWait(operationsByProcessInstanceAreCompleted, processInstanceKey);
    processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstance.getOperations()).hasSize(1);
    operation = processInstance.getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_VARIABLE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);

    //check variables
    final List<VariableDto> variables = getVariables(processInstanceKey, taskAId);
    assertThat(variables).hasSize(1);
    assertThat(variables.get(0).getName()).isEqualTo(varName);
    assertThat(variables.get(0).getValue()).isEqualTo(varValue);
  }

  protected Long getFlowNodeInstanceId(Long processInstanceKey, String activityId) {
    final List<FlowNodeInstanceEntity> allActivityInstances = tester.getAllFlowNodeInstances(processInstanceKey);
    final Optional<FlowNodeInstanceEntity> first = allActivityInstances.stream().filter(ai -> ai.getFlowNodeId().equals(activityId)).findFirst();
    assertThat(first.isPresent()).isTrue();
    return Long.valueOf(first.get().getId());
  }

  @Test
  public void testCancelExecutedOnOneInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    //when
    //we call CANCEL_PROCESS_INSTANCE operation on instance
    final ListViewQueryDto processInstanceQuery = TestUtil.createGetAllProcessInstancesQuery()
        .setIds(Collections.singletonList(processInstanceKey.toString()));
    postBatchOperationWithOKResponse(processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE);

    //and execute the operation
    executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);

    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation()).isEqualTo(true);
    assertThat(processInstances.getProcessInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = processInstances.getProcessInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getId()).isNotNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    //elasticsearchTestRule.refreshIndexesInElasticsearch();
    processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstances.getProcessInstances().get(0).getOperations()).hasSize(1);
    operation = processInstances.getProcessInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    //assert that process is canceled
    assertThat(processInstances.getProcessInstances().get(0).getState()).isEqualTo(ProcessInstanceStateDto.CANCELED);

    //check batch operation progress
    //TODO replace this with REST API call - OPE-790
    List<BatchOperationEntity> batchOperations = operationReader.getBatchOperations(10);
    assertThat(batchOperations).hasSize(1);

    BatchOperationEntity batchOperationEntity = batchOperations.get(0);
    assertThat(batchOperationEntity.getType()).isEqualTo(OperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(batchOperationEntity.getOperationsFinishedCount()).isEqualTo(1);
    assertThat(batchOperationEntity.getEndDate()).isNotNull();

    //check batch operation id stored in process instance
    List<ProcessInstanceForListViewEntity> processInstanceEntities = getProcessInstanceEntities(processInstanceQuery);
    assertThat(processInstanceEntities).hasSize(1);
    assertThat(processInstanceEntities.get(0).getBatchOperationIds()).containsExactly(batchOperationEntity.getId());
  }

  private List<ProcessInstanceForListViewEntity> getProcessInstanceEntities(
      ListViewQueryDto processInstanceQuery) {
    ListViewRequestDto request = new ListViewRequestDto(processInstanceQuery);
    return listViewReader.queryListView(request, new ListViewResponseDto());
  }

  @Test
  public void testTwoResolveIncidentOperationsOnOneInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    failTaskWithNoRetriesLeft("taskA", processInstanceKey, "Some error");

    //when we call RESOLVE_INCIDENT operation two times on one instance
    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));  //#1
    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));  //#2

    //and execute the operation
    executeOneBatch();

    //then
    //the state of one operation is COMPLETED and of the other - FAILED
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, processInstanceKey);

    final ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    final List<OperationDto> operations = processInstance.getOperations();
    assertThat(operations).hasSize(2);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.COMPLETED)).hasSize(1);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.FAILED)).hasSize(1);

    //check incidents
    final List<IncidentDto> incidents = incidentReader
        .getIncidentsByProcessInstanceId(String.valueOf(processInstanceKey)).getIncidents();
    assertThat(incidents).hasSize(0);

    //check batch operation progress
    //TODO replace this with REST API call - OPE-790
    List<BatchOperationEntity> batchOperations = operationReader.getBatchOperations(10);
    assertThat(batchOperations).hasSize(2);
    BatchOperationEntity batchOperationEntity = batchOperations.get(0);
    assertThat(batchOperationEntity.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(batchOperationEntity.getOperationsFinishedCount()).isEqualTo(1);
    assertThat(batchOperationEntity.getEndDate()).isNotNull();
    batchOperationEntity = batchOperations.get(1);
    assertThat(batchOperationEntity.getType()).isEqualTo(OperationType.RESOLVE_INCIDENT);
    assertThat(batchOperationEntity.getOperationsFinishedCount()).isEqualTo(1);
    assertThat(batchOperationEntity.getEndDate()).isNotNull();

  }

  @Test
  public void testSeveralCancelOperationsOnOneInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    //when we call CANCEL_PROCESS_INSTANCE operation three times on one instance
    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE));  //#1
    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE));  //#2
    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE));  //#3

    //and execute the operation
    executeOneBatch();

    //then
    //the state of one operation is COMPLETED and of the other - FAILED
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);

    final ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    final List<OperationDto> operations = processInstance.getOperations();
    assertThat(operations).hasSize(3);
    assertThat(operations).extracting("state").containsAnyOf(OperationState.COMPLETED, OperationState.FAILED);

  }

  @Test
  public void testTwoDifferentOperationsOnOneInstance() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    failTaskWithNoRetriesLeft("taskA", processInstanceKey, "Some error");

    //when we call CANCEL_PROCESS_INSTANCE and then RESOLVE_INCIDENT operation on one instance
    final ListViewQueryDto processInstanceQuery = TestUtil.createGetAllProcessInstancesQuery()
        .setIds(Collections.singletonList(processInstanceKey.toString()));
    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE));  //#1
    executeOneBatch();

    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));  //#2
    executeOneBatch();

    //then
    //the state of 1st operation is COMPLETED and the 2nd - FAILED
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, processInstanceKey);
    //elasticsearchTestRule.refreshIndexesInElasticsearch();
    ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation()).isEqualTo(false);
    final List<OperationDto> operations = processInstances.getProcessInstances().get(0).getOperations();
    assertThat(operations).hasSize(2);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.COMPLETED)).hasSize(1)
        .anyMatch(op -> op.getType().equals(OperationType.CANCEL_PROCESS_INSTANCE));
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.FAILED)).hasSize(1)
      .anyMatch(op -> op.getType().equals(OperationType.RESOLVE_INCIDENT));

  }

  @Test
  public void testRetryOperationOnZeebeNotAvailable() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    zeebeContainer.stop();

    //when we call CANCEL_PROCESS_INSTANCE and then RESOLVE_INCIDENT operation on one instance
    final ListViewQueryDto processInstanceQuery = TestUtil.createGetAllProcessInstancesQuery()
        .setIds(Collections.singletonList(processInstanceKey.toString()));
    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE));  //#1
    executeOneBatch();

    //then
    ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation()).isEqualTo(true);
    final List<OperationDto> operations = processInstances.getProcessInstances().get(0).getOperations();
    assertThat(operations).hasSize(1);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.LOCKED)).hasSize(1)
        .anyMatch(op -> op.getType().equals(OperationType.CANCEL_PROCESS_INSTANCE));
  }

  @Test
  public void testFailResolveIncidentBecauseOfNoIncidents() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();
    failTaskWithNoRetriesLeft("taskA", processInstanceKey, "some error");
    //we call RESOLVE_INCIDENT operation on instance
    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));
    //resolve the incident before the operation is executed
    final IncidentEntity incident = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey).get(0);
    ZeebeTestUtil.resolveIncident(zeebeClient, incident.getJobKey(), incident.getKey());

    //when
    //and execute the operation
    executeOneBatch();

    //then
    //the state of operation is FAILED, as there are no appropriate incidents
    final ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
    assertThat(processInstance.isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstance.getOperations()).hasSize(1);
    OperationDto operation = processInstance.getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).contains("no such incident was found");
    assertThat(operation.getId()).isNotNull();

    //check incidents
    final List<IncidentDto> incidents = incidentReader
        .getIncidentsByProcessInstanceId(String.valueOf(processInstanceKey)).getIncidents();
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
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);

    //when
    //we call CANCEL_PROCESS_INSTANCE operation on instance
    final ListViewQueryDto processInstanceQuery = TestUtil.createGetAllProcessInstancesQuery()
        .setIds(Collections.singletonList(processInstanceKey.toString()));
    postBatchOperationWithOKResponse(processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE);

    //and execute the operation
    executeOneBatch();

    //then
    //the state of operation is FAILED, as there are no appropriate incidents
    ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstances.getProcessInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = processInstances.getProcessInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).isEqualTo("Unable to cancel CANCELED process instance. Instance must be in ACTIVE or INCIDENT state.");
    assertThat(operation.getId()).isNotNull();
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
    deployProcess(startEndProcess, "startEndProcess.bpmn");
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(super.getClient(), bpmnProcessId, null);
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey);
    //elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //we call CANCEL_PROCESS_INSTANCE operation on instance
    final ListViewQueryDto processInstanceQuery = TestUtil.createGetAllProcessInstancesQuery()
        .setIds(Collections.singletonList(processInstanceKey.toString()));
    postBatchOperationWithOKResponse(processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE);

    //and execute the operation
    executeOneBatch();

    //then
    //the state of operation is FAILED, as the instance is in wrong state
    ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(1);
    assertThat(processInstances.getProcessInstances().get(0).isHasActiveOperation()).isEqualTo(false);
    assertThat(processInstances.getProcessInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = processInstances.getProcessInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).isEqualTo("Unable to cancel COMPLETED process instance. Instance must be in ACTIVE or INCIDENT state.");
    assertThat(operation.getId()).isNotNull();
  }

  @Test
  public void testFailAddVariableOperationAsVariableAlreadyExists() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    //when we call ADD_VARIABLE operation for the variable that already exists
    final String newVarName = "a";
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.ADD_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue("\"newValue\"");
    op.setVariableScopeId(ConversionUtils.toStringOrNull(processInstanceKey));
    final MvcResult mvcResult = postOperation(processInstanceKey, op,
        HttpURLConnection.HTTP_BAD_REQUEST);

    // then
    assertThat(mvcResult.getResolvedException().getMessage())
        .isEqualTo(String.format("Variable with the name \"%s\" already exists.", newVarName));
  }

  @Test
  public void testFailAddVariableOperationAsOperationAlreadyExists() throws Exception {
    // given
    final Long processInstanceKey = startDemoProcessInstance();

    //when we call ADD_VARIABLE operation for the first time
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
    for (int i = 0; i<instanceCount; i++) {
      startDemoProcessInstance();
    }

    //when
    final MvcResult mvcResult = postBatchOperation(TestUtil.createGetAllRunningQuery(), OperationType.RESOLVE_INCIDENT, null, HttpStatus.SC_BAD_REQUEST);

    final String expectedErrorMsg = String
      .format("Too many process instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize());
    assertThat(mvcResult.getResolvedException().getMessage()).contains(expectedErrorMsg);
  }
  private long startDemoProcessInstance() {
    String processId = "demoProcess";
    return tester.startProcessInstance(processId, "{\"a\": \"b\"}")
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

  private ListViewResponseDto getProcessInstances(ListViewQueryDto query) throws Exception {
    ListViewRequestDto request = new ListViewRequestDto(query);
    request.setPageSize(100);
    MockHttpServletRequestBuilder getProcessInstancesRequest =
      post(query()).content(mockMvcTestRule.json(request))
        .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult =
      mockMvc.perform(getProcessInstancesRequest)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });
  }

  private String query() {
    return QUERY_INSTANCES_URL;
  }

}
