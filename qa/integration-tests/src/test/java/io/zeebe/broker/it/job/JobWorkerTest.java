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

import static io.zeebe.exporter.api.record.Assertions.assertThat;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.test.util.record.RecordingExporter.jobBatchRecords;
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.TestUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class JobWorkerTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public Timeout timeout = Timeout.seconds(20);

  private ZeebeClient client;

  @Before
  public void setUp() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldOpenSubscription() throws InterruptedException {
    // given
    final long jobKey = createJobOfType("foo");

    // when
    final RecordingJobHandler jobHandler = new RecordingJobHandler();

    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then
    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());

    final List<ActivatedJob> jobs = jobHandler.getHandledJobs();
    assertThat(jobs).hasSize(1);
    assertThat(jobs.get(0).getKey()).isEqualTo(jobKey);
  }

  @Test
  public void shouldCompleteJob() throws InterruptedException {
    // given
    createJobOfType("foo");

    final RecordingJobHandler jobHandler = new RecordingJobHandler();

    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());
    final ActivatedJob lockedJob = jobHandler.getHandledJobs().get(0);

    // when
    client.newCompleteCommand(lockedJob.getKey()).send().join();

    // then
    waitUntil(() -> jobRecords(JobIntent.COMPLETED).exists());

    final Record<JobRecordValue> createRecord = jobRecords(JobIntent.CREATE).getFirst();
    assertThat(createRecord.getValue()).hasDeadline(null).hasWorker("");

    final Record<JobRecordValue> createdRecord = jobRecords(JobIntent.CREATED).getFirst();
    assertThat(createdRecord.getValue()).hasDeadline(null);

    final Record<JobRecordValue> activatedRecord = jobRecords(JobIntent.ACTIVATED).getFirst();
    assertThat(activatedRecord.getValue().getDeadline()).isNotNull();
    assertThat(activatedRecord.getValue()).hasWorker("test");
  }

  @Test
  public void shouldCompleteJobInHandler() throws InterruptedException {
    // given
    final long jobKey =
        clientRule.createSingleJob("foo", b -> b.zeebeTaskHeader("b", "2"), "{\"a\":1}");

    // when
    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, t) -> c.newCompleteCommand(t.getKey()).variables("{\"a\":3}").send());

    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then
    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());

    assertThat(jobHandler.getHandledJobs()).hasSize(1);

    final ActivatedJob subscribedJob = jobHandler.getHandledJobs().get(0);
    assertThat(subscribedJob.getKey()).isEqualTo(jobKey);
    assertThat(subscribedJob.getType()).isEqualTo("foo");
    assertThat(subscribedJob.getDeadline()).isAfter(Instant.now());

    waitUntil(() -> jobRecords(JobIntent.COMPLETED).exists());

    final Record<JobRecordValue> completedRecord = jobRecords(JobIntent.COMPLETED).getFirst();
    assertThat(completedRecord.getValue().getVariables()).isEqualTo("{\"a\":3}");
    assertThat(completedRecord.getValue()).hasCustomHeaders(Collections.singletonMap("b", "2"));
  }

  @Test
  public void shouldCloseWorker() {
    // given
    final RecordingJobHandler jobHandler = new RecordingJobHandler();

    final JobWorker subscription =
        client
            .newWorker()
            .jobType("foo")
            .handler(jobHandler)
            .timeout(Duration.ofMinutes(5))
            .name("test")
            .open();

    // when
    subscription.close();

    // then
    clientRule.createSingleJob("foo");

    createJobOfType("foo");

    waitUntil(() -> jobRecords(JobIntent.CREATED).exists());

    assertThat(jobHandler.getHandledJobs()).isEmpty();
    assertThat(jobBatchRecords(JobBatchIntent.ACTIVATE).exists()).isFalse();
  }

  @Test
  public void shouldFetchAndHandleJobs() {
    // given
    final int numJobs = 50;
    for (int i = 0; i < numJobs; i++) {
      createJobOfType("foo");
    }

    final RecordingJobHandler handler =
        new RecordingJobHandler(
            (c, j) -> {
              c.newCompleteCommand(j.getKey()).send().join();
            });

    client
        .newWorker()
        .jobType("foo")
        .handler(handler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .maxJobsActive(10)
        .open();

    // when
    waitUntil(() -> handler.getHandledJobs().size() == numJobs);

    // then
    assertThat(handler.getHandledJobs()).hasSize(numJobs);
  }

  @Test
  public void shouldFailJobManuallyAndRetry() {
    // given
    createJobOfType("foo");

    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, j) -> c.newFailCommand(j.getKey()).retries(1).send(),
            (c, j) -> c.newCompleteCommand(j.getKey()).send().join());

    // when
    client.newWorker().jobType("foo").handler(jobHandler).name("myWorker").open();

    // then
    waitUntil(() -> jobHandler.getHandledJobs().size() == 2);
    Record<JobRecordValue> record = jobRecords(JobIntent.FAILED).getFirst();
    assertThat(record.getValue()).hasType("foo").hasWorker("myWorker").hasRetries(1);
    record = jobRecords(JobIntent.COMPLETED).getFirst();
    assertThat(record.getValue()).hasType("foo").hasWorker("myWorker");
  }

  @Test
  public void shouldFailJobWithErrorMessage() {
    // given
    createJobOfType("foo");

    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, j) -> c.newFailCommand(j.getKey()).retries(0).errorMessage("this failed").send());

    // when
    client.newWorker().jobType("foo").handler(jobHandler).name("myWorker").open();

    // then
    waitUntil(() -> jobHandler.getHandledJobs().size() == 1);
    final Record<JobRecordValue> record = jobRecords(JobIntent.FAILED).getFirst();
    assertThat(record.getValue())
        .hasType("foo")
        .hasWorker("myWorker")
        .hasRetries(0)
        .hasErrorMessage("this failed");
  }

  @Test
  public void shouldMarkJobAsFailedAndRetryIfHandlerThrowsException() {
    // given
    final long jobKey = createJobOfType("foo");
    final String failureMessage = "expected failure";

    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, j) -> {
              throw new RuntimeException(failureMessage);
            },
            (c, j) -> c.newCompleteCommand(j.getKey()).send().join());

    // when
    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then the subscription is not broken and other jobs are still handled
    waitUntil(() -> jobHandler.getHandledJobs().size() == 2);

    assertThat(jobHandler.getHandledJobs())
        .extracting(ActivatedJob::getKey)
        .containsExactly(jobKey, jobKey);

    final Record<JobRecordValue> failedJob = jobRecords(JobIntent.FAILED).getFirst();
    assertThat(failedJob.getValue()).hasErrorMessage(failureMessage);

    waitUntil(() -> jobRecords(JobIntent.COMPLETED).exists());
  }

  @Test
  public void shouldNotLockJobIfRetriesAreExhausted() {
    // given
    clientRule.createSingleJob("foo", b -> b.zeebeTaskRetries(1));

    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, t) -> {
              throw new RuntimeException("expected failure");
            });

    // when
    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    waitUntil(() -> jobRecords(JobIntent.FAILED).withRetries(0).exists());

    assertThat(jobHandler.getHandledJobs()).hasSize(1);
  }

  @Test
  public void shouldExpireJobLock() {
    // given
    final long jobKey = createJobOfType("foo");

    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, t) -> {
              // don't complete the job - just wait for lock expiration
            });

    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    waitUntil(() -> jobHandler.getHandledJobs().size() == 1);

    // then
    brokerRule.getClock().addTime(Duration.ofMinutes(6));
    waitUntil(() -> jobHandler.getHandledJobs().size() == 2);

    assertThat(jobHandler.getHandledJobs())
        .hasSize(2)
        .extracting(ActivatedJob::getKey)
        .containsExactly(jobKey, jobKey);

    assertThat(jobRecords(JobIntent.TIMED_OUT).exists()).isTrue();
  }

  @Test
  public void shouldGiveJobToSingleSubscription() {
    // given
    final RecordingJobHandler jobHandler =
        new RecordingJobHandler((c, t) -> c.newCompleteCommand(t.getKey()).send().join());

    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // when
    createJobOfType("foo");

    waitUntil(() -> jobHandler.getHandledJobs().size() == 1);

    // then
    assertThat(jobHandler.getHandledJobs()).hasSize(1);
  }

  @Test
  public void shouldSubscribeToMultipleTypes() throws InterruptedException {
    // given
    createJobOfType("foo");
    createJobOfType("bar");

    final RecordingJobHandler jobHandler = new RecordingJobHandler();

    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    client
        .newWorker()
        .jobType("bar")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    waitUntil(() -> jobHandler.getHandledJobs().size() == 2);
  }

  @Test
  public void shouldHandleMoreJobsThanPrefetchCapacity() {
    // given
    final int subscriptionCapacity = 16;

    for (int i = 0; i < subscriptionCapacity + 1; i++) {
      createJobOfType("foo");
    }
    final RecordingJobHandler jobHandler = new RecordingJobHandler();

    // when
    client
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then
    TestUtil.waitUntil(() -> jobHandler.getHandledJobs().size() > subscriptionCapacity);
  }

  @Test
  public void shouldOnlyFetchVariablesSpecified() throws InterruptedException {
    // given
    final String jobType = "test";
    final List<String> fetchVariables = Arrays.asList("foo", "bar", "baz");
    final Set<String> capturedVariables = new HashSet<>();
    final CountDownLatch latch = new CountDownLatch(1);

    client
        .newWorker()
        .jobType(jobType)
        .handler(
            (c, job) -> {
              final Map<String, Object> variables = job.getVariablesAsMap();
              capturedVariables.addAll(variables.keySet());
              latch.countDown();
            })
        .fetchVariables(fetchVariables)
        .open();

    // when
    clientRule.createSingleJob(jobType, b -> {}, "{\"foo\":1,\"baz\":2,\"hello\":\"world\"}");

    // then
    latch.await();
    assertThat(capturedVariables).isSubsetOf(fetchVariables);
  }

  @Test
  public void shouldOnlyFetchVariablesSpecifiedAsVargs() throws InterruptedException {
    // given
    final String jobType = "test";
    final String[] fetchVariables = new String[] {"foo", "bar", "baz"};
    final Set<String> capturedVariables = new HashSet<>();
    final CountDownLatch latch = new CountDownLatch(1);

    client
        .newWorker()
        .jobType(jobType)
        .handler(
            (c, job) -> {
              final Map<String, Object> variables = job.getVariablesAsMap();
              capturedVariables.addAll(variables.keySet());
              latch.countDown();
            })
        .fetchVariables(fetchVariables)
        .open();

    // when
    clientRule.createSingleJob(jobType, b -> {}, "{\"foo\":1,\"baz\":2,\"hello\":\"world\"}");

    // then
    latch.await();
    assertThat(capturedVariables).isSubsetOf(fetchVariables);
  }

  private long createJobOfType(final String type) {
    return clientRule.createSingleJob(type);
  }
}
