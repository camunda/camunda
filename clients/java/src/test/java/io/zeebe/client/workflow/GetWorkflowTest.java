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

import com.google.common.base.Charsets;
import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.client.api.commands.WorkflowResourceRequestStep1;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowRequest;
import io.zeebe.util.StreamUtil;
import java.io.IOException;
import org.junit.Test;

public class GetWorkflowTest extends ClientTest {

  @Test
  public void shouldGetWorkflow() throws IOException {
    // given
    final long workflowKey = 123L;
    final String bpmnProcessId = "testProcess";
    final int version = 12;
    final String resourceName = "process.bpmn";
    final String bpmnXml = "<?xml?>";

    gatewayService.onGetWorkflowRequest(workflowKey, bpmnProcessId, version, resourceName, bpmnXml);

    // when
    final WorkflowResource response =
        client.newResourceRequest().workflowKey(workflowKey).send().join();

    // then
    assertThat(response.getWorkflowKey()).isEqualTo(workflowKey);
    assertThat(response.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(response.getVersion()).isEqualTo(version);
    assertThat(response.getResourceName()).isEqualTo(resourceName);
    assertThat(response.getBpmnXml()).isEqualTo(bpmnXml);
    assertThat(StreamUtil.read(response.getBpmnXmlAsStream()))
        .isEqualTo(bpmnXml.getBytes(Charsets.UTF_8));

    final GetWorkflowRequest request = gatewayService.getLastRequest();
    assertThat(request.getWorkflowKey()).isEqualTo(workflowKey);
  }

  @Test
  public void shouldGetWorkflowByProcessIdAndVersion() {
    // given
    final String bpmnProcessId = "testProcess";
    final int version = 435;

    // when
    client.newResourceRequest().bpmnProcessId(bpmnProcessId).version(version).send().join();

    // then
    final GetWorkflowRequest request = gatewayService.getLastRequest();
    assertThat(request.getWorkflowKey()).isEqualTo(0);
    assertThat(request.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(request.getVersion()).isEqualTo(version);
  }

  @Test
  public void shouldGetWorkflowByProcessIdAndLatestVersion() {
    // given
    final String bpmnProcessId = "testProcess";

    // when
    client.newResourceRequest().bpmnProcessId(bpmnProcessId).latestVersion().send().join();

    // then
    final GetWorkflowRequest request = gatewayService.getLastRequest();
    assertThat(request.getWorkflowKey()).isEqualTo(0);
    assertThat(request.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(request.getVersion()).isEqualTo(WorkflowResourceRequestStep1.LATEST_VERSION);
  }
}
