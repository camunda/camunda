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
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.util.StreamUtil;
import io.zeebe.util.collection.Tuple;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class ActivateJobsTest {

  private static final String JOB_TYPE = "testJob";
  private static final Map<String, Object> CUSTOM_HEADERS = Collections.singletonMap("foo", "bar");
  private static final Map<String, Object> VARIABLES = Collections.singletonMap("hello", "world");
  private static final int PARTITION_COUNT = 3;

  private final EmbeddedBrokerRule embeddedBrokerRule =
      new EmbeddedBrokerRule(setPartitionCount(PARTITION_COUNT));
  public GrpcClientRule clientRule = new GrpcClientRule(embeddedBrokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(embeddedBrokerRule).around(clientRule);

  @Rule public Timeout timeout = Timeout.seconds(120);

  private ZeebeClient client;

  @Before
  public void setUp() {

    client = clientRule.getClient();
    waitUntil(
        () ->
            client.newTopologyRequest().send().join().getBrokers().stream()
                    .flatMap(b -> b.getPartitions().stream())
                    .filter(PartitionInfo::isLeader)
                    .count()
                == PARTITION_COUNT);
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
    final int amount = PARTITION_COUNT * 3;

    final List<Long> jobKeys = createJobs(amount);

    // when
    final ActivateJobsResponse response =
        client
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(amount)
            .workerName(worker)
            .timeout(timeout)
            .send()
            .join();

    waitUntil(() -> jobRecords(JobIntent.ACTIVATED).limit(amount).count() == amount);

    // then
    assertThat(response.getJobs()).extracting(ActivatedJob::getKey).containsOnlyElementsOf(jobKeys);
    assertThat(response.getJobs())
        .extracting(
            ActivatedJob::getType,
            ActivatedJob::getWorker,
            ActivatedJob::getCustomHeaders,
            ActivatedJob::getVariablesAsMap)
        .containsOnly(tuple(JOB_TYPE, worker, CUSTOM_HEADERS, VARIABLES));

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
    // map from job type to tuple of available jobs and maxJobsToActivate
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
        client.newActivateJobsCommand().jobType(jobType).maxJobsToActivate(amount).send().join();

    // then
    assertThat(response.getJobs()).hasSize(expectedJobsCount);
  }

  @Test
  public void shouldActivateJobsOnPartitionsRoundRobin() {
    // given
    createJobs(PARTITION_COUNT * 3);

    // when
    final List<Integer> activatedPartitionIds =
        IntStream.range(0, PARTITION_COUNT)
            .boxed()
            .flatMap(
                i ->
                    client.newActivateJobsCommand().jobType(JOB_TYPE).maxJobsToActivate(1)
                        .workerName("worker").timeout(1000).send().join().getJobs().stream())
            .map(ActivatedJob::getKey)
            .map(Protocol::decodePartitionId)
            .collect(Collectors.toList());

    // then
    assertThat(activatedPartitionIds)
        .containsOnly(START_PARTITION_ID, START_PARTITION_ID + 1, START_PARTITION_ID + 2);
  }

  @Test
  public void shouldCompleteJobsIfBatchRecordIsTruncated()
      throws IOException, InterruptedException {
    // given
    final int numJobs = 15;
    final byte[] variablesBytes =
        StreamUtil.read(
            ActivateJobsTest.class.getResourceAsStream("/variables/large_random_variables.json"));
    final String variables = new String(variablesBytes, Charset.forName("UTF-8"));

    createJobs(JOB_TYPE, numJobs, variables);

    // when
    final CountDownLatch latch = new CountDownLatch(numJobs);
    client
        .newWorker()
        .jobType(JOB_TYPE)
        .handler(
            (client, job) -> {
              client.newCompleteCommand(job.getKey()).send().join();
              latch.countDown();
            })
        .name("worker")
        .timeout(Duration.ofMinutes(2))
        .maxJobsActive(10)
        .open();
    latch.await();

    // then
    Assertions.assertThat(
            RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).getFirst().getValue())
        .isTruncated();
    assertThat(RecordingExporter.jobRecords(JobIntent.COMPLETED).limit(numJobs).count())
        .isEqualTo(numJobs);
  }

  private List<Long> createJobs(int amount) {
    return createJobs(JOB_TYPE, amount);
  }

  private List<Long> createJobs(String type, int amount) {
    return createJobs(type, amount, "{\"hello\":\"world\"}");
  }

  private List<Long> createJobs(String type, int amount, String variables) {
    final BpmnModelInstance modelInstance =
        clientRule.createSingleJobModelInstance(type, b -> b.zeebeTaskHeader("foo", "bar"));
    final long workflowKey = clientRule.deployWorkflow(modelInstance);

    for (int i = 0; i < amount; i++) {
      clientRule.createWorkflowInstance(workflowKey, variables);
    }

    final List<Long> jobKeys =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(type)
            .filter(r -> r.getValue().getHeaders().getWorkflowKey() == workflowKey)
            .limit(amount)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(jobKeys).hasSize(amount);

    return jobKeys;
  }
}
