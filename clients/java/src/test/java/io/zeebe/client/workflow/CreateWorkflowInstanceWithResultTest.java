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

import static io.zeebe.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.client.api.response.WorkflowInstanceResult;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultRequest;
import java.time.Duration;
import org.junit.Test;

public final class CreateWorkflowInstanceWithResultTest extends ClientTest {

  @Test
  public void shouldCreateWorkflowInstanceByWorkflowInstanceKey() {
    // given
    final String variables = "{\"key\": \"val\"}";
    gatewayService.onCreateWorkflowInstanceWithResultRequest(123, "testProcess", 12, 32, variables);

    // when
    final WorkflowInstanceResult response =
        client
            .newCreateInstanceCommand()
            .workflowKey(123)
            .withResult()
            .requestTimeout(Duration.ofSeconds(123))
            .send()
            .join();

    // then
    assertThat(response.getWorkflowKey()).isEqualTo(123);
    assertThat(response.getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(response.getVersion()).isEqualTo(12);
    assertThat(response.getWorkflowInstanceKey()).isEqualTo(32);
    assertThat(response.getVariablesAsMap()).containsExactly(entry("key", "val"));
    assertThat(response.getVariables()).isEqualTo(variables);
    final VariablesPojo result = response.getVariablesAsType(VariablesPojo.class);
    assertThat(result.getKey()).isEqualTo("val");

    final CreateWorkflowInstanceWithResultRequest request = gatewayService.getLastRequest();
    assertThat(request.getRequest().getWorkflowKey()).isEqualTo(123);
    assertThat(request.getRequestTimeout()).isEqualTo(Duration.ofSeconds(123).toMillis());
    assertThat(request.getFetchVariablesList()).isEmpty();
  }

  @Test
  public void shouldBeAbleToSpecifyFetchVariables() {
    // given
    final String variables = "{\"key\": \"val\"}";
    gatewayService.onCreateWorkflowInstanceWithResultRequest(123, "testProcess", 12, 32, variables);

    // when
    final WorkflowInstanceResult response =
        client
            .newCreateInstanceCommand()
            .workflowKey(123)
            .withResult()
            .fetchVariables("x")
            .requestTimeout(Duration.ofSeconds(123))
            .send()
            .join();

    // then
    final CreateWorkflowInstanceWithResultRequest request = gatewayService.getLastRequest();
    assertThat(request.getRequest().getWorkflowKey()).isEqualTo(123);
    assertThat(request.getRequestTimeout()).isEqualTo(Duration.ofSeconds(123).toMillis());
    assertThat(request.getFetchVariablesList()).containsExactly("x");
  }

  @Test
  public void shouldCreateWorkflowInstanceByBpmnProcessIdAndVersion() {
    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("testProcess")
        .version(123)
        .withResult()
        .send()
        .join();

    // then
    final CreateWorkflowInstanceWithResultRequest request = gatewayService.getLastRequest();
    assertThat(request.getRequest().getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(request.getRequest().getVersion()).isEqualTo(123);
  }

  @Test
  public void shouldCreateWorkflowInstanceWithStringVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .workflowKey(123)
        .variables("{\"foo\": \"bar\"}")
        .withResult()
        .send()
        .join();

    // then
    final CreateWorkflowInstanceWithResultRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getRequest().getVariables()))
        .containsOnly(entry("foo", "bar"));
  }

  private static class VariablesPojo {
    String key;

    public String getKey() {
      return key;
    }

    public VariablesPojo setKey(final String key) {
      this.key = key;
      return this;
    }
  }
}
