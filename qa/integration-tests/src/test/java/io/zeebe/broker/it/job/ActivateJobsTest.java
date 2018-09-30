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
package io.zeebe.broker.it.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

// TODO(menski): replace with gRPC client implementation
// https://github.com/zeebe-io/zeebe/issues/1426
public class ActivateJobsTest {

  public static final String JOB_TYPE = "testJob";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule clientRule = new ClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public Timeout timeout = Timeout.seconds(20);

  private BrokerClient client;

  @Before
  public void setUp() {
    client = (BrokerClient) clientRule.getClient();
  }

  @Test
  public void shouldActivateMultipleJobs() {
    // given
    final String worker = "testWorker";
    final Duration timeout = Duration.ofMinutes(4);
    final int amount = 5;

    final List<Long> jobKeys = createJobs(amount);
    final BrokerActivateJobsRequest request =
        new BrokerActivateJobsRequest(JOB_TYPE)
            .setWorker(worker)
            .setTimeout(timeout.toMillis())
            .setAmount(amount);

    // when
    final BrokerResponse<JobBatchRecord> response = client.sendRequest(request).join();

    // then
    assertThat(response.isResponse()).isTrue();
    final JobBatchRecord batch = response.getResponse();
    assertThat(batch.jobKeys()).extracting(LongValue::getValue).containsOnlyElementsOf(jobKeys);
    assertThat(batch.jobs())
        .extracting(JobRecord::getType)
        .containsOnly(BufferUtil.wrapString(JOB_TYPE));
  }

  private List<Long> createJobs(int amount) {
    return IntStream.range(0, amount)
        .mapToLong(this::createJob)
        .boxed()
        .collect(Collectors.toList());
  }

  private long createJob(int index) {
    return clientRule
        .getJobClient()
        .newCreateCommand()
        .jobType(JOB_TYPE)
        .addCustomHeader("index", index)
        .payload(Collections.singletonMap("index", index))
        .send()
        .join()
        .getKey();
  }
}
