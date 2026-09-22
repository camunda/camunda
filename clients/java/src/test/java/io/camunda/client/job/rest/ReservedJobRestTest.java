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
package io.camunda.client.job.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.protocol.rest.JobCompletionRequest;
import io.camunda.client.protocol.rest.JobErrorRequest;
import io.camunda.client.protocol.rest.JobFailRequest;
import io.camunda.client.protocol.rest.JobReleaseRequest;
import io.camunda.client.protocol.rest.JobUpdateRequest;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class ReservedJobRestTest extends ClientRestTest {

  private static final long JOB_KEY = 12;
  private static final String TOKEN = "ts-8f2c";

  @Test
  public void shouldCompleteReservedJob() {
    // when
    client.newCompleteCommand(JOB_KEY).withJobReservationToken(TOKEN).send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getJobReservationToken()).isEqualTo(TOKEN);
  }

  @Test
  public void shouldFailReservedJob() {
    // when
    client.newFailCommand(JOB_KEY).retries(0).withJobReservationToken(TOKEN).send().join();

    // then
    final JobFailRequest request = gatewayService.getLastRequest(JobFailRequest.class);
    assertThat(request.getJobReservationToken()).isEqualTo(TOKEN);
  }

  @Test
  public void shouldThrowErrorOnReservedJob() {
    // when
    client
        .newThrowErrorCommand(JOB_KEY)
        .errorCode("PAYMENT_DECLINED")
        .withJobReservationToken(TOKEN)
        .send()
        .join();

    // then
    final JobErrorRequest request = gatewayService.getLastRequest(JobErrorRequest.class);
    assertThat(request.getJobReservationToken()).isEqualTo(TOKEN);
  }

  @Test
  public void shouldUpdateReservedJob() {
    // when
    client
        .newUpdateJobCommand(JOB_KEY)
        .updateRetries(3)
        .withJobReservationToken(TOKEN)
        .send()
        .join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getJobReservationToken()).isEqualTo(TOKEN);
  }

  @Test
  public void shouldReleaseReservedJob() {
    // when
    client.newReleaseJobCommand(JOB_KEY).withJobReservationToken(TOKEN).send().join();

    // then
    final JobReleaseRequest request = gatewayService.getLastRequest(JobReleaseRequest.class);
    assertThat(request.getJobReservationToken()).isEqualTo(TOKEN);
  }

  @Test
  public void shouldStandInForCalledProcess() {
    // when
    client
        .newCompleteCommand(JOB_KEY)
        .variable("approved", true)
        .withJobReservationToken(TOKEN)
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getVariables()).containsEntry("approved", true);
    assertThat(request.getResult()).isNull();
  }

  @Test
  public void shouldRunCalledProcess() {
    // when
    client
        .newCompleteCommand(JOB_KEY)
        .withResult(r -> r.forCallActivity().runCalledProcess())
        .withJobReservationToken(TOKEN)
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getResult().getType()).isEqualTo("callActivity");
    assertThat(request.getResult().getRunCalledProcess()).isTrue();
    assertThat(request.getJobReservationToken()).isEqualTo(TOKEN);
  }

  @Test
  public void shouldRejectReservationTokenOverGrpc() {
    // when / then
    assertThatThrownBy(
            () ->
                client.newCompleteCommand(JOB_KEY).withJobReservationToken(TOKEN).useGrpc().send())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("withJobReservationToken");
  }

  @Test
  public void shouldRejectCallActivityResultOverGrpc() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCompleteCommand(JOB_KEY)
                    .withResult(r -> r.forCallActivity().runCalledProcess())
                    .useGrpc()
                    .send())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("forCallActivity");
  }
}
