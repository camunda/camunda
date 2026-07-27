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

import io.camunda.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.UpdateRetriesJobResponse;
import io.camunda.client.util.ClientTest;
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
  public void shouldUpdateRetriesWithLeaseToken() {
    // given
    final long jobKey = 12;
    final String leaseToken = "lease-token";

    // when
    client.newUpdateRetriesCommand(jobKey).retries(3).withLeaseToken(leaseToken).send().join();

    // then
    final UpdateJobRetriesRequest request = gatewayService.getLastRequest();
    assertThat(request.getLeaseToken()).isEqualTo(leaseToken);
  }

  @Test
  public void shouldCarryLeaseTokenFromActivatedJob() {
    // given
    final String leaseToken = "lease-token";
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);
    Mockito.when(job.getLeaseToken()).thenReturn(leaseToken);

    // when
    client.newUpdateRetriesCommand(job).retries(3).send().join();

    // then
    final UpdateJobRetriesRequest request = gatewayService.getLastRequest();
    assertThat(request.getLeaseToken())
        .describedAs("Expected the activated job's lease token to be carried automatically")
        .isEqualTo(leaseToken);
  }

  @Test
  public void shouldNotCarryLeaseTokenFromActivatedJobWithoutOne() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);
    Mockito.when(job.getLeaseToken()).thenReturn(null);

    // when
    client.newUpdateRetriesCommand(job).retries(3).send().join();

    // then
    final UpdateJobRetriesRequest request = gatewayService.getLastRequest();
    assertThat(request.getLeaseToken())
        .describedAs("Expected no lease token when the activated job carries none")
        .isEmpty();
  }

  @Test
  public void shouldNotCarryLeaseTokenByJobKey() {
    // given
    final long jobKey = 12;

    // when
    client.newUpdateRetriesCommand(jobKey).retries(3).send().join();

    // then
    final UpdateJobRetriesRequest request = gatewayService.getLastRequest();
    assertThat(request.getLeaseToken())
        .describedAs("Expected no lease token when the command is built from a job key")
        .isEmpty();
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

  @Test
  public void shouldSetOperationReference() {
    // given
    final long operationReference = 456;

    // when
    client.newUpdateRetriesCommand(123).retries(3).operationReference(operationReference).execute();

    // then
    final UpdateJobRetriesRequest request = gatewayService.getLastRequest();
    assertThat(request.getOperationReference()).isEqualTo(operationReference);
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final UpdateRetriesJobCommandStep1 command = client.newUpdateRetriesCommand(12);

    // when
    final UpdateRetriesJobResponse response = command.retries(0).send().join();

    // then
    assertThat(response).isNotNull();
  }
}
