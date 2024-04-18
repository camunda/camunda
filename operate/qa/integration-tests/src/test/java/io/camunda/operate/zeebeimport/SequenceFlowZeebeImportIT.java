/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.rest.dto.SequenceFlowDto;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class SequenceFlowZeebeImportIT extends OperateZeebeAbstractIT {

  protected String getSequenceFlowURL(final Long processInstanceKey) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/sequence-flows", processInstanceKey);
  }

  @Test
  public void testSequenceFlowsAreLoaded() throws Exception {
    // having
    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .sequenceFlowId("sf1")
            .serviceTask("task1")
            .zeebeJobType("task1")
            .sequenceFlowId("sf2")
            .serviceTask("task2")
            .zeebeJobType("task2")
            .endEvent()
            .done();
    deployProcess(process, processId + ".bpmn");

    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(
            zeebeClient, processId, "{\"var1\": \"initialValue\", \"otherVar\": 123}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task1");
    ZeebeTestUtil.completeTask(zeebeClient, "task1", getWorkerName(), null);
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task2");

    // when
    final List<SequenceFlowDto> sequenceFlows = getSequenceFlows(processInstanceKey);

    assertThat(sequenceFlows)
        .extracting(SequenceFlowTemplate.ACTIVITY_ID)
        .containsExactlyInAnyOrder("sf1", "sf2");
  }

  private List<SequenceFlowDto> getSequenceFlows(final Long processInstanceKey) throws Exception {
    final MvcResult mvcResult =
        mockMvc
            .perform(get(getSequenceFlowURL(processInstanceKey)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, SequenceFlowDto.class);
  }
}
