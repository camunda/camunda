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

import static io.camunda.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.util.ClientTest;
import io.camunda.client.util.JsonUtil;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import java.time.Duration;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.Mockito;

public final class ThrowErrorTest extends ClientTest {

  @Test
  public void shouldThrowErrorByJobKey() {
    // given
    final long jobKey = 12;
    final String errorCode = "errorCode";

    // when
    client.newThrowErrorCommand(jobKey).errorCode(errorCode).send().join();

    // then
    final ThrowErrorRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getErrorCode()).isEqualTo(errorCode);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldThrowError() {
    // given
    final String errorCode = "errorCode";
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newThrowErrorCommand(job).errorCode(errorCode).send().join();

    // then
    final ThrowErrorRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getErrorCode()).isEqualTo(errorCode);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldThrowErrorWithMessage() {
    // given
    final long jobKey = 12;
    final String errorCode = "errorCode";
    final String errorMsg = "errorMsg";

    // when
    client.newThrowErrorCommand(jobKey).errorCode(errorCode).errorMessage(errorMsg).send().join();

    // then
    final ThrowErrorRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getErrorCode()).isEqualTo(errorCode);
    assertThat(request.getErrorMessage()).isEqualTo(errorMsg);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newThrowErrorCommand(123)
        .errorCode("errorCode")
        .requestTimeout(requestTimeout)
        .send()
        .join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldThrowErrorWithJsonStringVariables() {
    // given
    final long jobKey = 12;
    final String errorCode = "errorCode";
    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    client.newThrowErrorCommand(jobKey).errorCode(errorCode).variables(json).send().join();

    // then
    final ThrowErrorRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getErrorCode()).isEqualTo(errorCode);

    JsonUtil.assertEquality(request.getVariables(), json);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldThrowErrorWithSingleVariable() {
    // given
    final long jobKey = 12;
    final String errorCode = "errorCode";
    final String key = "key";
    final String value = "value";

    // when
    client.newThrowErrorCommand(jobKey).errorCode(errorCode).variable(key, value).send().join();

    // then
    final ThrowErrorRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getErrorCode()).isEqualTo(errorCode);
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry(key, value));

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldThrowErrorWhenTryToThrowErrorCommandWithNullVariable() {
    // given
    final long jobKey = 12;
    final String errorCode = "errorCode";
    // when
    Assertions.assertThatThrownBy(
            () ->
                client
                    .newThrowErrorCommand(jobKey)
                    .errorCode(errorCode)
                    .variable(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
