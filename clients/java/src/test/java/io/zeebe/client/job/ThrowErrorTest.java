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
package io.zeebe.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import java.time.Duration;
import org.junit.Test;

public final class ThrowErrorTest extends ClientTest {

  @Test
  public void shouldThrowError() {
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
}
