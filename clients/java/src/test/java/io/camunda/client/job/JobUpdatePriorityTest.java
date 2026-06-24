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
package io.camunda.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.UpdateJobPriorityCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.UpdateJobPriorityResponse;
import io.camunda.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobPriorityRequest;
import java.time.Duration;
import org.junit.Test;
import org.mockito.Mockito;

public final class JobUpdatePriorityTest extends ClientTest {

  @Test
  public void shouldUpdatePriorityByKey() {
    // given
    final long jobKey = 12;
    final int newPriority = 5;

    // when
    client.newUpdateJobPriorityCommand(jobKey).priority(newPriority).send().join();

    // then
    final UpdateJobPriorityRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getPriority()).isEqualTo(newPriority);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldUpdatePriority() {
    // given
    final int newPriority = 5;
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newUpdateJobPriorityCommand(job).priority(newPriority).send().join();

    // then
    final UpdateJobPriorityRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getPriority()).isEqualTo(newPriority);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newUpdateJobPriorityCommand(123)
        .priority(5)
        .requestTimeout(requestTimeout)
        .send()
        .join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldSetOperationReference() {
    // given
    final long operationReference = 456;

    // when
    client
        .newUpdateJobPriorityCommand(123)
        .priority(5)
        .operationReference(operationReference)
        .execute();

    // then
    final UpdateJobPriorityRequest request = gatewayService.getLastRequest();
    assertThat(request.getOperationReference()).isEqualTo(operationReference);
  }

  @Test
  public void shouldTransmitPriorityZero() {
    // given
    final long jobKey = 12;

    // when
    client.newUpdateJobPriorityCommand(jobKey).priority(0).send().join();

    // then
    final UpdateJobPriorityRequest request = gatewayService.getLastRequest();
    assertThat(request.getPriority()).isEqualTo(0);
    assertThat(request.hasPriority()).isTrue();
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final UpdateJobPriorityCommandStep1 command = client.newUpdateJobPriorityCommand(12);

    // when
    final UpdateJobPriorityResponse response = command.priority(5).send().join();

    // then
    assertThat(response).isNotNull();
  }
}
