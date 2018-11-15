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
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import org.junit.Test;

public class FailJobTest extends ClientTest {

  @Test
  public void shouldFailJob() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client.jobClient().newFailCommand(jobKey).retries(newRetries).send().join();

    // then
    final FailJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getRetries()).isEqualTo(newRetries);
  }

  @Test
  public void shouldFailJobWithMessage() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client
        .jobClient()
        .newFailCommand(jobKey)
        .retries(newRetries)
        .errorMessage("failed message")
        .send()
        .join();

    // then
    final FailJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getRetries()).isEqualTo(newRetries);
    assertThat(request.getErrorMessage()).isEqualTo("failed message");
  }
}
