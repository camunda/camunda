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

import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.collection.Tuple;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class ActivateJobsTest {

  private static final String JOB_TYPE = "testJob";
  private static final Map<String, Object> CUSTOM_HEADERS = Collections.singletonMap("foo", "bar");
  private static final Map<String, Object> PAYLOAD = Collections.singletonMap("hello", "world");

  @Rule public ClusteringRule clusteringRule = new ClusteringRule();
  @Rule public GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule public Timeout timeout = Timeout.seconds(10);

  private ZeebeClient client;

  @Before
  public void setUp() {
    client = clientRule.getClient();
  }

  @After
  public void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  public void shouldActivateMultipleJobs() {
    // given
    final String worker = "testWorker";
    final Duration timeout = Duration.ofMinutes(4);
    final int amount = clusteringRule.getPartitionIds().size() * 3;

    final List<Long> jobKeys = createJobs(amount);

    // when
    final ActivateJobsResponse response =
        client
            .jobClient()
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .amount(amount)
            .workerName(worker)
            .timeout(timeout)
            .send()
            .join();

    TestUtil.waitUntil(() -> jobRecords(JobIntent.ACTIVATED).limit(amount).count() == amount);

    // then
    assertThat(response.getJobs()).extracting(ActivatedJob::getKey).containsOnlyElementsOf(jobKeys);
    assertThat(response.getJobs())
        .extracting(
            ActivatedJob::getType,
            ActivatedJob::getWorker,
            ActivatedJob::getCustomHeaders,
            ActivatedJob::getPayloadAsMap)
        .containsOnly(tuple(JOB_TYPE, worker, CUSTOM_HEADERS, PAYLOAD));

    final List<Instant> deadlines =
        jobRecords(JobIntent.ACTIVATED)
            .limit(amount)
            .map(r -> r.getValue().getDeadline())
            .collect(Collectors.toList());

    assertThat(response.getJobs())
        .extracting(ActivatedJob::getDeadline)
        .containsOnlyElementsOf(deadlines);
  }

  @Test
  public void shouldActivateJobsRespectingAmountLimit() {
    // map from job type to tuple of available jobs and amount to activate
    final Map<String, Tuple<Integer, Integer>> jobCounts = new HashMap<>();
    jobCounts.put("foo", new Tuple<>(3, 7));
    jobCounts.put("bar", new Tuple<>(7, 3));
    jobCounts.put("baz", new Tuple<>(0, 10));

    jobCounts.forEach(this::shouldActivateJobsRespectingAmountLimit);
  }

  private void shouldActivateJobsRespectingAmountLimit(
      String jobType, Tuple<Integer, Integer> availableAmountTuple) {
    // given
    final int available = availableAmountTuple.getLeft();
    final int amount = availableAmountTuple.getRight();
    final int expectedJobsCount = Math.min(available, amount);

    createJobs(jobType, available);

    // when
    final ActivateJobsResponse response =
        client.jobClient().newActivateJobsCommand().jobType(jobType).amount(amount).send().join();

    // then
    assertThat(response.getJobs()).hasSize(expectedJobsCount);
  }

  @Test
  public void shouldActivateJobsOnPartitionsRoundRobin() {
    // given
    final List<Integer> partitionIds = clusteringRule.getPartitionIds();
    createJobs(partitionIds.size() * 3);

    // when
    final List<Integer> activatedPartitionIds =
        IntStream.range(0, partitionIds.size())
            .boxed()
            .flatMap(
                i ->
                    client
                        .jobClient()
                        .newActivateJobsCommand()
                        .jobType(JOB_TYPE)
                        .amount(1)
                        .workerName("worker")
                        .timeout(1000)
                        .send()
                        .join()
                        .getJobs()
                        .stream())
            .map(ActivatedJob::getKey)
            .map(Protocol::decodePartitionId)
            .collect(Collectors.toList());

    // then
    assertThat(activatedPartitionIds).containsOnlyElementsOf(partitionIds);
  }

  private List<Long> createJobs(int amount) {
    return createJobs(JOB_TYPE, amount);
  }

  private List<Long> createJobs(String type, int amount) {
    return IntStream.range(0, amount)
        .mapToLong(i -> createJob(type))
        .boxed()
        .collect(Collectors.toList());
  }

  private long createJob(String type) {
    return client
        .jobClient()
        .newCreateCommand()
        .jobType(type)
        .addCustomHeaders(CUSTOM_HEADERS)
        .payload(PAYLOAD)
        .send()
        .join()
        .getKey();
  }
}
