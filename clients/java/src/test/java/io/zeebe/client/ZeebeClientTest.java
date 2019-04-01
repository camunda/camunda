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
package io.zeebe.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.util.ClientTest;
import java.time.Duration;
import org.junit.Test;

public class ZeebeClientTest extends ClientTest {

  @Test
  public void shouldNotFailIfClosedTwice() {
    client.close();
    client.close();
  }

  @Test
  public void shouldHaveDefaultValues() {
    // given
    try (ZeebeClient client = ZeebeClient.newClient()) {
      // when
      final ZeebeClientConfiguration configuration = client.getConfiguration();

      // then
      assertThat(configuration.getBrokerContactPoint()).isEqualTo("0.0.0.0:26500");
      assertThat(configuration.getDefaultJobWorkerMaxJobsActive()).isEqualTo(32);
      assertThat(configuration.getNumJobWorkerExecutionThreads()).isEqualTo(1);
      assertThat(configuration.getDefaultJobWorkerName()).isEqualTo("default");
      assertThat(configuration.getDefaultJobTimeout()).isEqualTo(Duration.ofMinutes(5));
      assertThat(configuration.getDefaultJobPollInterval()).isEqualTo(Duration.ofMillis(100));
      assertThat(configuration.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofHours(1));
    }
  }
}
