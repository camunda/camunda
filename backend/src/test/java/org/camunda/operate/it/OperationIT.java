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

import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.WorkflowInstanceBatchOperationDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.rest.dto.WorkflowInstanceRequestDto;
import org.camunda.operate.rest.dto.WorkflowInstanceResponseDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.ZeebeTestRule;
import org.camunda.operate.util.ZeebeUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class OperationIT extends OperateIntegrationTest {

  private static final String POST_BATCH_OPERATION_URL = "/api/workflow-instances/operation";
  private static final String QUERY_INSTANCES_URL = WORKFLOW_INSTANCE_URL;

  @Rule
  public ZeebeTestRule zeebeTestRule = new ZeebeTestRule();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  public ZeebeUtil zeebeUtil;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Autowired
  private OperateProperties operateProperties;
  private Long initialBatchOperationMaxSize;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
    this.initialBatchOperationMaxSize = operateProperties.getBatchOperationMaxSize();
  }

  @After
  public void after() {
    operateProperties.setBatchOperationMaxSize(initialBatchOperationMaxSize);
  }

  @Test
  public void testOperationsPersisted() throws Exception {
    // given
    String topicName = zeebeTestRule.getTopicName();
    String processId = "demoProcess";
    int instanceCount = 10;
    zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");
    for (int i = 0; i<instanceCount; i++) {
      zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");
    }
    elasticsearchTestRule.processAllEvents(100);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    final WorkflowInstanceQueryDto allRunningQuery = createAllRunningQuery();
    WorkflowInstanceBatchOperationDto batchOperationDto = createBatchOperationDto(OperationType.UPDATE_RETRIES, allRunningQuery);
    MockHttpServletRequestBuilder postOperationRequest =
      post(POST_BATCH_OPERATION_URL)
        .content(mockMvcTestRule.json(batchOperationDto))
        .contentType(mockMvcTestRule.getContentType());

    mockMvc.perform(postOperationRequest)
      .andExpect(status().isOk())
      .andReturn();
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    WorkflowInstanceResponseDto response = getWorkflowInstances(allRunningQuery);

    assertThat(response.getWorkflowInstances()).hasSize(instanceCount);
    assertThat(response.getWorkflowInstances()).flatExtracting(WorkflowInstanceType.OPERATIONS).extracting(WorkflowInstanceType.TYPE).containsOnly(OperationType.UPDATE_RETRIES);
    assertThat(response.getWorkflowInstances()).flatExtracting(WorkflowInstanceType.OPERATIONS).extracting(WorkflowInstanceType.STATE).containsOnly(
      OperationState.SCHEDULED);
    assertThat(response.getWorkflowInstances()).flatExtracting(WorkflowInstanceType.OPERATIONS).extracting(WorkflowInstanceType.START_DATE).doesNotContainNull();
    assertThat(response.getWorkflowInstances()).flatExtracting(WorkflowInstanceType.OPERATIONS).extracting(WorkflowInstanceType.END_DATE).containsOnlyNulls();
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
  public void testOperationsToManyInstanceException() throws Exception {

    operateProperties.setBatchOperationMaxSize(5L);

    // given
    String topicName = zeebeTestRule.getTopicName();
    String processId = "demoProcess";
    int instanceCount = 10;
    zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");
    for (int i = 0; i<instanceCount; i++) {
      zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");
    }
    elasticsearchTestRule.processAllEvents(100);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    final WorkflowInstanceQueryDto allRunningQuery = createAllRunningQuery();
    WorkflowInstanceBatchOperationDto batchOperationDto = createBatchOperationDto(OperationType.UPDATE_RETRIES, allRunningQuery);
    MockHttpServletRequestBuilder postOperationRequest =
      post(POST_BATCH_OPERATION_URL)
        .content(mockMvcTestRule.json(batchOperationDto))
        .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
      mockMvc.perform(postOperationRequest)
        .andExpect(status().isBadRequest())
        .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Too many");

  }

  private WorkflowInstanceResponseDto getWorkflowInstances(WorkflowInstanceQueryDto allRunningQuery) throws Exception {
    WorkflowInstanceRequestDto request = createWorkflowInstanceRequestDto(allRunningQuery);
    MockHttpServletRequestBuilder getWorkflowInstancesRequest =
      post(query(0, 100)).content(mockMvcTestRule.json(request))
        .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult =
      mockMvc.perform(getWorkflowInstancesRequest)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() {
    });
  }

  private WorkflowInstanceRequestDto createWorkflowInstanceRequestDto(WorkflowInstanceQueryDto query) {
    WorkflowInstanceRequestDto request = new WorkflowInstanceRequestDto();
    request.getQueries().add(query);
    return request;
  }

  private WorkflowInstanceBatchOperationDto createBatchOperationDto(OperationType operationType, WorkflowInstanceQueryDto query) {
    WorkflowInstanceBatchOperationDto batchOperationDto = new WorkflowInstanceBatchOperationDto();
    batchOperationDto.getQueries().add(query);
    batchOperationDto.setOperationType(operationType);
    return batchOperationDto;
  }

  private WorkflowInstanceQueryDto createAllRunningQuery() {
    WorkflowInstanceQueryDto query = new WorkflowInstanceQueryDto();
    query.setRunning(true);
    query.setActive(true);
    query.setIncidents(true);
    return query;
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }

}
