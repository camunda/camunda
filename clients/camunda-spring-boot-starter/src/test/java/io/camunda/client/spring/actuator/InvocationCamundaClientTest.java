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
package io.camunda.client.spring.actuator;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.protocol.rest.JobCompletionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InvocationCamundaClientTest {
  @Test
  void shouldCaptureCompleteCommand() throws Exception {
    final JobHandler handler =
        (client, job) -> client.newCompleteCommand(job).variables("{\"foo\": 123}").execute();
    final ActivatedJob activatedJob = mock(ActivatedJob.class);
    when(activatedJob.getKey()).thenReturn(123L);
    final InvocationCamundaClient camundaClient =
        new InvocationCamundaClient(new CamundaObjectMapper());
    handler.handle(camundaClient, activatedJob);
    final List<CapturedCommand> capturedCommands = camundaClient.getCapturedCommands();
    assertThat(capturedCommands).hasSize(1);
    final CapturedCommand completeCommand = capturedCommands.get(0);
    assertThat(completeCommand.pathParams()).contains(entry("jobKey", "123"));
    assertThat(completeCommand.body()).isInstanceOf(JobCompletionRequest.class);
    final JobCompletionRequest body = (JobCompletionRequest) completeCommand.body();
    assertThat(body.getVariables()).contains(entry("foo", 123));
  }
}
