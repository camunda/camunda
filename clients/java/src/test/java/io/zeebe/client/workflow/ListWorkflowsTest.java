/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.commands.Workflows;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowMetadata;
import org.junit.Test;

public class ListWorkflowsTest extends ClientTest {

  @Test
  public void shouldListWorkflows() {
    // given
    final WorkflowMetadata[] workflows =
        new WorkflowMetadata[] {
          WorkflowMetadata.newBuilder()
              .setWorkflowKey(23L)
              .setBpmnProcessId("testProcess")
              .setVersion(12)
              .setResourceName("testProcess.bpmn")
              .build(),
          WorkflowMetadata.newBuilder()
              .setWorkflowKey(450L)
              .setBpmnProcessId("fooBar")
              .setVersion(1332)
              .setResourceName("foo.bpmn")
              .build(),
          WorkflowMetadata.newBuilder()
              .setWorkflowKey(233L)
              .setBpmnProcessId("yamlProcess")
              .setVersion(99)
              .setResourceName("process.yaml")
              .build()
        };

    gatewayService.onListWorkflowsRequest(workflows);

    // when
    final Workflows response = client.newWorkflowRequest().send().join();

    // then
    assertThat(response.getWorkflows()).hasSize(workflows.length);
    for (int i = 0; i < workflows.length; i++) {
      final Workflow actual = response.getWorkflows().get(i);
      final WorkflowMetadata expected = workflows[i];
      assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    final ListWorkflowsRequest request = gatewayService.getLastRequest();
    assertThat(request.getBpmnProcessId()).isEmpty();
  }

  @Test
  public void shouldListWorkflowsByBpmnProcessId() {
    // given
    final String bpmnProcessId = "testProcess";

    // when
    client.newWorkflowRequest().bpmnProcessId(bpmnProcessId).send().join();

    // then
    final ListWorkflowsRequest request = gatewayService.getLastRequest();
    assertThat(request.getBpmnProcessId()).isEqualTo(bpmnProcessId);
  }
}
