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

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import java.time.Duration;
import org.junit.Test;
import org.mockito.Mockito;

public final class JobUpdateRetriesTest extends ClientTest {

  @Test
  public void shouldUpdateRetriesByKey() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client.newUpdateRetriesCommand(jobKey).retries(newRetries).send().join();

    // then
    final UpdateJobRetriesRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getRetries()).isEqualTo(newRetries);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldUpdateRetries() {
    // given
    final int newRetries = 23;
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newUpdateRetriesCommand(job).retries(newRetries).send().join();

    // then
    final UpdateJobRetriesRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getRetries()).isEqualTo(newRetries);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newUpdateRetriesCommand(123).retries(3).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }
}
