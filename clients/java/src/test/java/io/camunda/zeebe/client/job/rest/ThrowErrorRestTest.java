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
package io.camunda.zeebe.client.job.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.protocol.rest.JobErrorRequest;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.JsonUtil;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ThrowErrorRestTest extends ClientRestTest {

  @Test
  public void shouldThrowErrorByJobKey() {
    // given
    final long jobKey = 12;
    final String errorCode = "errorCode";

    // when
    client.newThrowErrorCommand(jobKey).errorCode(errorCode).send().join();

    // then
    final JobErrorRequest request = gatewayService.getLastRequest(JobErrorRequest.class);
    assertThat(request.getErrorCode()).isEqualTo(errorCode);
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
    final JobErrorRequest request = gatewayService.getLastRequest(JobErrorRequest.class);
    assertThat(request.getErrorCode()).isEqualTo(errorCode);
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
    final JobErrorRequest request = gatewayService.getLastRequest(JobErrorRequest.class);
    assertThat(request.getErrorCode()).isEqualTo(errorCode);
    assertThat(request.getErrorMessage()).isEqualTo(errorMsg);
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
    final JobErrorRequest request = gatewayService.getLastRequest(JobErrorRequest.class);
    assertThat(request.getErrorCode()).isEqualTo(errorCode);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), json);
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
    final JobErrorRequest request = gatewayService.getLastRequest(JobErrorRequest.class);
    assertThat(request.getErrorCode()).isEqualTo(errorCode);
    assertThat(request.getVariables()).containsOnly(entry(key, value));
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
