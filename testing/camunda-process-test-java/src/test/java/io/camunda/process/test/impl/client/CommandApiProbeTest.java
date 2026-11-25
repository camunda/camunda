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
package io.camunda.process.test.impl.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.ProblemException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommandApiProbeTest {

  /** Partition id 1 in the upper bits, sequence number 1 in the lower bits. */
  private static final long DEPLOYMENT_PARTITION_JOB_KEY = (1L << 51) | 1;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Test
  void shouldProbeTheDeploymentPartition() {
    // given
    when(camundaClient.newCompleteCommand(anyLong()).useRest().send().join())
        .thenThrow(problem(404));

    // when
    CommandApiProbe.waitUntilCommandApiAcceptsCommands(camundaClient);

    // then
    verify(camundaClient).newCompleteCommand(DEPLOYMENT_PARTITION_JOB_KEY);
  }

  @Test
  void shouldRetryWhileBrokerIsNotReady() {
    // given
    when(camundaClient.newCompleteCommand(anyLong()).useRest().send().join())
        .thenThrow(problem(500), problem(503), problem(404));

    // when
    CommandApiProbe.waitUntilCommandApiAcceptsCommands(camundaClient);

    // then
    verify(camundaClient, times(3)).newCompleteCommand(DEPLOYMENT_PARTITION_JOB_KEY);
  }

  @Test
  void shouldFailOnUnexpectedClientError() {
    // given
    when(camundaClient.newCompleteCommand(anyLong()).useRest().send().join())
        .thenThrow(problem(400));

    // when / then
    assertThatThrownBy(() -> CommandApiProbe.waitUntilCommandApiAcceptsCommands(camundaClient))
        .isInstanceOf(ProblemException.class);
  }

  private static ProblemException problem(final int code) {
    return new ProblemException(code, "reason", new ProblemDetail());
  }
}
