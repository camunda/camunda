/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.it;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.apache.http.HttpStatus;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.schema.templates.WorkflowInstanceTemplate;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.OperationDto;
import org.camunda.operate.rest.dto.WorkflowInstanceBatchOperationDto;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebe.operation.CancelWorkflowInstanceHandler;
import org.camunda.operate.zeebe.operation.OperationExecutor;
import org.camunda.operate.zeebe.operation.UpdateRetriesHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class OperationIT extends OperateZeebeIntegrationTest {

  private static final String POST_BATCH_OPERATION_URL = WORKFLOW_INSTANCE_URL + "/operation";
  private static final String QUERY_INSTANCES_URL = WORKFLOW_INSTANCE_URL;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private OperationExecutor operationExecutor;

  @Autowired
  private CancelWorkflowInstanceHandler cancelWorkflowInstanceHandler;

  @Autowired
  private UpdateRetriesHandler updateRetriesHandler;

  @Autowired
  @Qualifier("activityIsActiveCheck")
  private Predicate<Object[]> activityIsActiveCheck;

  @Autowired
  @Qualifier("incidentIsResolvedCheck")
  private Predicate<Object[]> incidentIsResolvedCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCanceledCheck")
  private Predicate<Object[]> workflowInstanceIsCanceledCheck;

  private Long initialBatchOperationMaxSize;
  private String workflowId;

  @Before
  public void starting() {
    super.before();

    try {
      FieldSetter.setField(cancelWorkflowInstanceHandler, CancelWorkflowInstanceHandler.class.getDeclaredField("zeebeClient"), super.getClient());
      FieldSetter.setField(updateRetriesHandler, UpdateRetriesHandler.class.getDeclaredField("zeebeClient"), super.getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }

    this.mockMvc = mockMvcTestRule.getMockMvc();
    this.initialBatchOperationMaxSize = operateProperties.getBatchOperationMaxSize();
    workflowId = deployWorkflow("demoProcess_v_1.bpmn");
  }

  @After
  public void after() {
    operateProperties.setBatchOperationMaxSize(initialBatchOperationMaxSize);
  }

  @Test
  public void testOperationsPersisted() throws Exception {
    // given
    final int instanceCount = 10;
    for (int i = 0; i<instanceCount; i++) {
      startDemoWorkflowInstance();
    }

    //when
    final ListViewQueryDto allRunningQuery = createAllRunningQuery();
    postOperationWithOKResponse(allRunningQuery, OperationType.UPDATE_RETRIES);

    //then
    ListViewResponseDto response = getWorkflowInstances(allRunningQuery);

    assertThat(response.getWorkflowInstances()).hasSize(instanceCount);
    assertThat(response.getWorkflowInstances()).flatExtracting("operations").extracting(WorkflowInstanceTemplate.TYPE).containsOnly(OperationType.UPDATE_RETRIES);
    assertThat(response.getWorkflowInstances()).flatExtracting("operations").extracting(WorkflowInstanceTemplate.STATE).containsOnly(
      OperationState.SCHEDULED);
    assertThat(response.getWorkflowInstances()).flatExtracting("operations").extracting(WorkflowInstanceTemplate.START_DATE).doesNotContainNull();
    assertThat(response.getWorkflowInstances()).flatExtracting("operations").extracting(WorkflowInstanceTemplate.END_DATE).containsOnlyNulls();
  }

  @Test
  public void testUpdateRetriesExecutedOnOneInstance() throws Exception {
    // given
    final long workflowInstanceKey = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA", workflowInstanceKey, "Some error");

    //when
    //we call UPDATE_RETRIES operation on instance
    final ListViewQueryDto workflowInstanceQuery = createAllRunningQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(IdTestUtil.getId(workflowInstanceKey)));
    postOperationWithOKResponse(workflowInstanceQuery, OperationType.UPDATE_RETRIES);

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);

    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_RETRIES);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    elasticsearchTestRule.processAllEvents(8);
    workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_RETRIES);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();
    //assert that incident is resolved
    assertThat(workflowInstances.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceStateDto.ACTIVE);
  }

  @Test
  public void testCancelExecutedOnOneInstance() throws Exception {
    // given
    final long workflowInstanceKey = startDemoWorkflowInstance();

    //when
    //we call CANCEL operation on instance
    final ListViewQueryDto workflowInstanceQuery = createAllQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(IdTestUtil.getId(workflowInstanceKey)));
    postOperationWithOKResponse(workflowInstanceQuery, OperationType.CANCEL);

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);

    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.CANCEL);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.CANCEL);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();
    //assert that process is canceled
    assertThat(workflowInstances.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
  }

  @Test
  public void testTwoOperationsOnOneInstance() throws Exception {
    // given
    final long workflowInstanceId = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA", workflowInstanceId, "Some error");

    //when we call UPDATE_RETRIES operation two times on one instance
    final ListViewQueryDto workflowInstanceQuery = createAllRunningQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(IdTestUtil.getId(workflowInstanceId)));
    postOperationWithOKResponse(workflowInstanceQuery, OperationType.UPDATE_RETRIES);  //#1
    postOperationWithOKResponse(workflowInstanceQuery, OperationType.UPDATE_RETRIES);  //#2

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //the state of one operation is COMPLETED and of the other - FAILED
    elasticsearchTestRule.processAllEventsAndWait(incidentIsResolvedCheck, workflowInstanceId);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    final List<OperationDto> operations = workflowInstances.getWorkflowInstances().get(0).getOperations();
    assertThat(operations).hasSize(2);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.COMPLETED)).hasSize(1);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.FAILED)).hasSize(1);
  }

  @Test
  public void testTwoDifferentOperationsOnOneInstance() throws Exception {
    // given
    final long workflowInstanceKey = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA", workflowInstanceKey, "Some error");

    //when we call CANCEL and then UPDATE_RETRIES operation on one instance
    final ListViewQueryDto workflowInstanceQuery = createAllQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(IdTestUtil.getId(workflowInstanceKey)));
    postOperationWithOKResponse(workflowInstanceQuery, OperationType.CANCEL);  //#1
    postOperationWithOKResponse(workflowInstanceQuery, OperationType.UPDATE_RETRIES);  //#2

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //the state of 1st operation is COMPLETED and the 2nd - FAILED
    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    elasticsearchTestRule.processAllEventsAndWait(incidentIsResolvedCheck, workflowInstanceKey);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    final List<OperationDto> operations = workflowInstances.getWorkflowInstances().get(0).getOperations();
    assertThat(operations).hasSize(2);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.COMPLETED)).hasSize(1)
        .anyMatch(op -> op.getType().equals(OperationType.CANCEL));
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.FAILED)).hasSize(1)
      .anyMatch(op -> op.getType().equals(OperationType.UPDATE_RETRIES));
  }

  @Test
  public void testFailUpdateRetriesBecauseOfNoIncidents() throws Exception {
    // given
    final long workflowInstanceKey = startDemoWorkflowInstance();

    //when
    //we call UPDATE_RETRIES operation on instance
    final ListViewQueryDto workflowInstanceQuery = createAllRunningQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(IdTestUtil.getId(workflowInstanceKey)));
    postOperationWithOKResponse(workflowInstanceQuery, OperationType.UPDATE_RETRIES);

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //the state of operation is FAILED, as there are no appropriate incidents
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).isEqualTo("No appropriate incidents found.");
    assertThat(operation.getEndDate()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();
  }

  @Test
  public void testFailCancelOnCanceledInstance() throws Exception {
    // given
    final long workflowInstanceKey = startDemoWorkflowInstance();
    ZeebeTestUtil.cancelWorkflowInstance(super.getClient(), workflowInstanceKey);
    elasticsearchTestRule.processAllEvents(10);

    //when
    //we call CANCEL operation on instance
    final ListViewQueryDto workflowInstanceQuery = createAllQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(IdTestUtil.getId(workflowInstanceKey)));
    postOperationWithOKResponse(workflowInstanceQuery, OperationType.CANCEL);

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //the state of operation is FAILED, as there are no appropriate incidents
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).isEqualTo("Unable to cancel CANCELED workflow instance. Instance must be in ACTIVE state.");
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
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), bpmnProcessId, null);
    elasticsearchTestRule.processAllEvents(20);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //we call CANCEL operation on instance
    final ListViewQueryDto workflowInstanceQuery = createAllQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(IdTestUtil.getId(workflowInstanceKey)));
    postOperationWithOKResponse(workflowInstanceQuery, OperationType.CANCEL);

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //the state of operation is FAILED, as the instance is in wrong state
    ListViewResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).isEqualTo("Unable to cancel COMPLETED workflow instance. Instance must be in ACTIVE state.");
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
    final MvcResult mvcResult = postOperation(createAllRunningQuery(), OperationType.UPDATE_RETRIES, HttpStatus.SC_BAD_REQUEST);

    final String expectedErrorMsg = String
      .format("Too many workflow instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize());
    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo(expectedErrorMsg);
  }

  private MvcResult postOperationWithOKResponse(ListViewQueryDto query, OperationType operationType) throws Exception {
    return postOperation(query, operationType, HttpStatus.SC_OK);
  }

  private MvcResult postOperation(ListViewQueryDto query, OperationType operationType, int expectedStatus) throws Exception {
    WorkflowInstanceBatchOperationDto batchOperationDto = createBatchOperationDto(operationType, query);
    MockHttpServletRequestBuilder postOperationRequest =
      post(POST_BATCH_OPERATION_URL)
        .content(mockMvcTestRule.json(batchOperationDto))
        .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
      mockMvc.perform(postOperationRequest)
        .andExpect(status().is(expectedStatus))
        .andReturn();
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return mvcResult;
  }


  private WorkflowInstanceBatchOperationDto createBatchOperationDto(OperationType operationType, ListViewQueryDto query) {
    WorkflowInstanceBatchOperationDto batchOperationDto = new WorkflowInstanceBatchOperationDto();
    batchOperationDto.getQueries().add(query);
    batchOperationDto.setOperationType(operationType);
    return batchOperationDto;
  }

  private long startDemoWorkflowInstance() {
    String processId = "demoProcess";
    final long workflowInstanceId = ZeebeTestUtil.startWorkflowInstance(super.getClient(), processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceId, "taskA");
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return workflowInstanceId;
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

  private ListViewQueryDto createAllRunningQuery() {
    ListViewQueryDto query = new ListViewQueryDto();
    query.setRunning(true);
    query.setActive(true);
    query.setIncidents(true);
    return query;
  }

  private ListViewQueryDto createAllQuery() {
    ListViewQueryDto query = new ListViewQueryDto();
    query.setRunning(true);
    query.setActive(true);
    query.setIncidents(true);
    query.setFinished(true);
    query.setCanceled(true);
    query.setCompleted(true);
    return query;
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }

}
