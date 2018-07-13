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

import java.util.List;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.es.writer.ElasticsearchBulkProcessor;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.rest.dto.WorkflowGroupDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.ZeebeTestRule;
import org.camunda.operate.util.ZeebeUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class WorkflowIT extends OperateIntegrationTest {

  private static final String QUERY_WORKFLOWS_GROUPED_URL = "/api/workflows/grouped";
  @Rule
  public ZeebeTestRule zeebeTestRule = new ZeebeTestRule();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Autowired
  private ZeebeUtil zeebeUtil;

  @Autowired
  private WorkflowReader workflowReader;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Autowired
  private EntityStorage entityStorage;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  @Test
  public void testWorkflowCreated() {
    //given
    String topicName = zeebeTestRule.getTopicName();
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");

    //when
    elasticsearchTestRule.processAllEvents(1);

    //then
    final WorkflowEntity workflowEntity = workflowReader.getWorkflow(workflowId);
    assertThat(workflowEntity.getId()).isEqualTo(workflowId);
    assertThat(workflowEntity.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(workflowEntity.getVersion()).isEqualTo(1);
    assertThat(workflowEntity.getBpmnXml()).isNotEmpty();
    assertThat(workflowEntity.getName()).isEqualTo("Demo process");

  }

  @Test
  public void testWorkflowsGrouped() throws Exception {
    //given
    String topicName = zeebeTestRule.getTopicName();

    final String demoProcessId = "demoProcess";
    final String demoProcessName = "Demo process new name";
    final String orderProcessId = "orderProcess";
    final String orderProcessName = "Order process";
    final String loanProcessId = "loanProcess";
    final String demoProcessV1Id = createAndDeployProcess(topicName, demoProcessId, "Demo process");
    final String demoProcessV2Id = createAndDeployProcess(topicName, demoProcessId, demoProcessName);
    final String orderProcessV1Id = createAndDeployProcess(topicName, orderProcessId, orderProcessName);
    final String orderProcessV2Id = createAndDeployProcess(topicName, orderProcessId, orderProcessName);
    final String orderProcessV3Id = createAndDeployProcess(topicName, orderProcessId, orderProcessName);
    final String loanProcessV1Id = createAndDeployProcess(topicName, loanProcessId, null);

    //when
    elasticsearchTestRule.processAllEvents(30);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    workflowReader.getWorkflowsGrouped();

    MockHttpServletRequestBuilder request = get(QUERY_WORKFLOWS_GROUPED_URL);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowGroupDto> workflowGroupDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowGroupDto.class);
    assertThat(workflowGroupDtos).hasSize(3);
    assertThat(workflowGroupDtos).isSortedAccordingTo(new WorkflowGroupDto.WorkflowGroupComparator());

    assertThat(workflowGroupDtos).filteredOn(wg -> wg.getBpmnProcessId().equals(demoProcessId)).hasSize(1);
    final WorkflowGroupDto demoProcessWorkflowGroup =
      workflowGroupDtos.stream().filter(wg -> wg.getBpmnProcessId().equals(demoProcessId)).findFirst().get();
    assertThat(demoProcessWorkflowGroup.getWorkflows()).hasSize(2);
    assertThat(demoProcessWorkflowGroup.getName()).isEqualTo(demoProcessName);
    assertThat(demoProcessWorkflowGroup.getWorkflows()).isSortedAccordingTo((w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(demoProcessWorkflowGroup.getWorkflows()).extracting("id").containsExactlyInAnyOrder(demoProcessV1Id, demoProcessV2Id);

    assertThat(workflowGroupDtos).filteredOn(wg -> wg.getBpmnProcessId().equals(orderProcessId)).hasSize(1);
    final WorkflowGroupDto orderProcessWorkflowGroup =
      workflowGroupDtos.stream().filter(wg -> wg.getBpmnProcessId().equals(orderProcessId)).findFirst().get();
    assertThat(orderProcessWorkflowGroup.getWorkflows()).hasSize(3);
    assertThat(orderProcessWorkflowGroup.getName()).isEqualTo(orderProcessName);
    assertThat(orderProcessWorkflowGroup.getWorkflows()).isSortedAccordingTo((w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(orderProcessWorkflowGroup.getWorkflows()).extracting("id").containsExactlyInAnyOrder(orderProcessV1Id, orderProcessV2Id, orderProcessV3Id);


    assertThat(workflowGroupDtos).filteredOn(wg -> wg.getBpmnProcessId().equals(loanProcessId)).hasSize(1);
    final WorkflowGroupDto loanProcessWorkflowGroup =
      workflowGroupDtos.stream().filter(wg -> wg.getBpmnProcessId().equals(loanProcessId)).findFirst().get();
    assertThat(loanProcessWorkflowGroup.getName()).isNull();
    assertThat(loanProcessWorkflowGroup.getWorkflows()).hasSize(1);
    assertThat(loanProcessWorkflowGroup.getWorkflows().get(0).getId()).isEqualTo(loanProcessV1Id);
  }

  private String createAndDeployProcess(String topicName, String bpmnProcessId, String name) {
    final WorkflowDefinition demoProcess =
      Bpmn.createExecutableWorkflow(bpmnProcessId).startEvent().endEvent().done();
    if (name != null) {
      ((ProcessImpl) demoProcess.getWorkflows().iterator().next()).setName(name);
    }
    return zeebeUtil.deployWorkflowToTheTopic(topicName, demoProcess, "resource.bpmn");
  }

}
