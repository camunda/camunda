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
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import org.assertj.core.api.Condition;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ZeebeImportMultipleProcessesIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;
  @Autowired private ProcessStore processStore;
  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void shouldImportBpmnWithMultipleProcesses() {
    final String bpmnProcessId1 = "Process_0diikxu";
    final String bpmnProcessId2 = "Process_18z2cdf";
    final String formId1 = "UserTaskForm_3ad3t51";
    final String formId2 = "UserTaskForm_1unph8k";

    tester.deployProcess("two_processes.bpmn").waitUntil().processIsDeployed();

    assertThat(mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1)))
        .hasOkHttpStatus()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .hasSize(2)
        .extracting("bpmnProcessId", "name")
        .containsExactlyInAnyOrder(
            Tuple.tuple(bpmnProcessId1, "Business Operation A"),
            Tuple.tuple(bpmnProcessId2, "Business Operation B"));

    final ProcessEntity processEntity1 = processStore.getProcessByBpmnProcessId(bpmnProcessId1);
    assertEquals(1, processEntity1.getFlowNodes().size());
    assertEquals("Do task A", processEntity1.getFlowNodes().get(0).getName());

    final ProcessEntity processEntity2 = processStore.getProcessByBpmnProcessId(bpmnProcessId2);
    assertEquals(1, processEntity2.getFlowNodes().size());
    assertEquals("Do task B", processEntity2.getFlowNodes().get(0).getName());

    assertThat(
            mockMvcHelper.doRequest(
                get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId1)
                    .param("processDefinitionKey", processEntity1.getId())))
        .hasOkHttpStatus()
        .extractingContent(objectMapper, FormResponse.class)
        .extracting("schema")
        .has(new Condition<>(t -> ((String) t).contains("Text area 1"), "Form 1"));

    assertThat(
            mockMvcHelper.doRequest(
                get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId2)
                    .param("processDefinitionKey", processEntity2.getId())))
        .hasOkHttpStatus()
        .extractingContent(objectMapper, FormResponse.class)
        .extracting("schema")
        .has(new Condition<>(t -> ((String) t).contains("Text area 2"), "Form 2"));
  }
}
