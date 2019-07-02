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

import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.client.impl.worker.JobPoller;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import java.time.Duration;
import org.junit.Test;

public class JobPollerTest extends ClientTest {

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(123);
    final JobPoller jobPoller =
        new JobPoller(
            rule.getGatewayStub(),
            ActivateJobsRequest.newBuilder(),
            new ZeebeObjectMapper(),
            requestTimeout);

    // when
    jobPoller.poll(123, job -> {}, integer -> {});

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }
}
