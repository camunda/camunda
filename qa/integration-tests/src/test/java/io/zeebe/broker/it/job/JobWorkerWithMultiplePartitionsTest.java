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

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.protocol.Protocol;
import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class JobWorkerWithMultiplePartitionsTest {

  public static final int PARTITION_COUNT = 3;
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(PARTITION_COUNT));

  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public Timeout timeout = Timeout.seconds(20);

  @Test
  public void shouldReceiveJobsFromMultiplePartitions() {
    // given
    final Integer[] partitionIds =
        IntStream.range(START_PARTITION_ID, START_PARTITION_ID + PARTITION_COUNT)
            .boxed()
            .toArray(Integer[]::new);

    final String jobType = "foooo";

    final RecordingJobHandler handler = new RecordingJobHandler();

    createJobOfType(jobType);
    createJobOfType(jobType);
    createJobOfType(jobType);

    // when
    clientRule
        .getClient()
        .newWorker()
        .jobType(jobType)
        .handler(handler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then
    waitUntil(() -> handler.getHandledJobs().size() == 3);

    final Integer[] receivedPartitionIds =
        handler.getHandledJobs().stream()
            .map(ActivatedJob::getKey)
            .map(Protocol::decodePartitionId)
            .toArray(Integer[]::new);

    assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
  }

  private long createJobOfType(final String type) {
    return clientRule.createSingleJob(type);
  }
}
