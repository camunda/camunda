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
package io.camunda.zeebe.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.UpdateTimeoutJobResponse;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest;
import org.junit.Test;
import org.mockito.Mockito;

public class JobUpdateTimeoutTest extends ClientTest {

  @Test
  public void shouldUpdateTimeoutByKey() {
    // given
    final long jobKey = 12;
    final long timeout = 100;

    // when
    client.newUpdateTimeoutCommand(jobKey).timeout(timeout).send().join();

    // then
    final UpdateJobTimeoutRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getTimeout()).isEqualTo(timeout);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldUpdateTimeout() {
    // given
    final long timeout = 100;
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newUpdateTimeoutCommand(job).timeout(timeout).send().join();

    // then
    final UpdateJobTimeoutRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getTimeout()).isEqualTo(timeout);

    //    rule.verifyRequestTimeout();
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final UpdateTimeoutJobCommandStep1 command = client.newUpdateTimeoutCommand(12);

    // when
    final UpdateTimeoutJobResponse response = command.timeout(10).send().join();

    // then
    assertThat(response).isNotNull();
  }
}
