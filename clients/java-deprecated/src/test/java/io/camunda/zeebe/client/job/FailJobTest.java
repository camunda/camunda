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

import static io.camunda.zeebe.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.FailJobResponse;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.client.util.JsonUtil;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import java.time.Duration;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.Mockito;

public final class FailJobTest extends ClientTest {

  @Test
  public void shouldFailJobByKey() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client.newFailCommand(jobKey).retries(newRetries).send().join();

    // then
    final FailJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getRetries()).isEqualTo(newRetries);
  }

  @Test
  public void shouldFailJob() {
    // given
    final int newRetries = 23;
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newFailCommand(job).retries(newRetries).send().join();

    // then
    final FailJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getRetries()).isEqualTo(newRetries);
  }

  @Test
  public void shouldFailJobWithMessage() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client.newFailCommand(jobKey).retries(newRetries).errorMessage("failed message").send().join();

    // then
    final FailJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getRetries()).isEqualTo(newRetries);
    assertThat(request.getErrorMessage()).isEqualTo("failed message");

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldFailJobWithBackoff() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    final Duration backoffTimeout = Duration.ofSeconds(1);
    client.newFailCommand(jobKey).retries(newRetries).retryBackoff(backoffTimeout).send().join();

    // then
    final FailJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getRetries()).isEqualTo(newRetries);
    assertThat(request.getRetryBackOff()).isEqualTo(backoffTimeout.toMillis());

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldFailJobWithBackoffAndMessage() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;
    final String message = "failed message";

    // when
    final Duration backoffTimeout = Duration.ofSeconds(1);
    client
        .newFailCommand(jobKey)
        .retries(newRetries)
        .retryBackoff(backoffTimeout)
        .errorMessage(message)
        .send()
        .join();

    // then
    final FailJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getRetries()).isEqualTo(newRetries);
    assertThat(request.getRetryBackOff()).isEqualTo(backoffTimeout.toMillis());
    assertThat(request.getErrorMessage()).isEqualTo(message);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newFailCommand(123).retries(3).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final FailJobCommandStep1 command = client.newFailCommand(12);

    // when
    final FailJobResponse response =
        command.retries(0).errorMessage("This failed oops.").send().join();

    // then
    assertThat(response).isNotNull();
  }

  @Test
  public void shouldFailJobWithJsonStringVariables() {
    // given
    final long jobKey = 12;
    final int newRetries = 0;

    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    final FailJobResponse response =
        client.newFailCommand(jobKey).retries(newRetries).variables(json).send().join();

    // then
    final FailJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), json);
    assertThat(response).isNotNull();
  }

  @Test
  public void shouldFailJobWithSingleVariable() {
    // given
    final long jobKey = 12;
    final int newRetries = 0;
    final String key = "key";
    final String value = "value";

    // when
    final FailJobResponse response =
        client.newFailCommand(jobKey).retries(newRetries).variable(key, value).send().join();

    // then
    final FailJobRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry(key, value));
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(response).isNotNull();
  }

  @Test
  public void shouldThrowErrorWhenTryToFailJobWithNullVariable() {
    // when
    final long jobKey = 12;
    final int newRetries = 0;
    Assertions.assertThatThrownBy(
            () ->
                client
                    .newFailCommand(jobKey)
                    .retries(newRetries)
                    .variable(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
