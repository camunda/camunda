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

import static io.zeebe.broker.it.util.TopicEventRecorder.jobCommand;
import static io.zeebe.broker.it.util.TopicEventRecorder.jobRetries;
import static io.zeebe.broker.it.util.TopicEventRecorder.state;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.commands.JobCommand;
import io.zeebe.client.api.commands.JobCommandName;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.api.commands.Topics;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.impl.job.CreateJobCommandImpl;
import io.zeebe.test.util.TestUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class JobWorkerTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule clientRule = new ClientRule();

  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule, false);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public Timeout timeout = Timeout.seconds(20);

  private JobClient jobClient;

  @Before
  public void setUp() {
    jobClient = clientRule.getClient().topicClient().jobClient();

    final String defaultTopic = clientRule.getClient().getConfiguration().getDefaultTopic();
    clientRule.waitUntilTopicsExists(defaultTopic);
  }

  @Test
  public void shouldOpenSubscription() throws InterruptedException {
    // given
    final JobEvent job = createJobOfType("foo");

    // when
    final RecordingJobHandler jobHandler = new RecordingJobHandler();

    jobClient
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then
    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());

    final List<JobEvent> jobs = jobHandler.getHandledJobs();
    assertThat(jobs).hasSize(1);
    assertThat(jobs.get(0).getMetadata().getKey()).isEqualTo(job.getKey());
  }

  @Test
  public void shouldCompleteJob() throws InterruptedException {
    // given
    eventRecorder.startRecordingEvents();

    final JobEvent job = createJobOfType("foo");

    final RecordingJobHandler jobHandler = new RecordingJobHandler();

    jobClient
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());
    final JobEvent lockedJob = jobHandler.getHandledJobs().get(0);

    // when
    final JobEvent result = jobClient.newCompleteCommand(lockedJob).send().join();

    // then
    assertThat(result.getKey()).isEqualTo(job.getKey());
    waitUntil(() -> eventRecorder.hasJobEvent(state(JobState.COMPLETED)));

    final JobCommand createCommand =
        eventRecorder.getJobCommands(jobCommand(JobCommandName.CREATE)).get(0);
    assertThat(createCommand.getDeadline()).isNull();
    assertThat(createCommand.getWorker()).isNull();

    final JobEvent createdEvent = eventRecorder.getJobEvents(JobState.CREATED).get(0);
    assertThat(createdEvent.getDeadline()).isNull();

    final JobEvent lockedEvent = eventRecorder.getJobEvents(JobState.ACTIVATED).get(0);
    assertThat(lockedEvent.getDeadline()).isNotNull();
    assertThat(lockedEvent.getWorker()).isEqualTo("test");
  }

  @Test
  public void shouldCompleteJobInHandler() throws InterruptedException {
    // given
    eventRecorder.startRecordingEvents();

    final JobEvent createdEvent =
        jobClient
            .newCreateCommand()
            .jobType("foo")
            .payload("{\"a\":1}")
            .addCustomHeader("b", "2")
            .send()
            .join();

    // when
    final RecordingJobHandler jobHandler =
        new RecordingJobHandler((c, t) -> c.newCompleteCommand(t).payload("{\"a\":3}").send());

    jobClient
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then
    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());

    assertThat(jobHandler.getHandledJobs()).hasSize(1);

    final JobEvent subscribedJob = jobHandler.getHandledJobs().get(0);
    assertThat(subscribedJob.getMetadata().getKey()).isEqualTo(createdEvent.getKey());
    assertThat(subscribedJob.getType()).isEqualTo("foo");
    assertThat(subscribedJob.getDeadline()).isAfter(Instant.now());

    waitUntil(() -> eventRecorder.hasJobEvent(state(JobState.COMPLETED)));

    final JobEvent completedEvent = eventRecorder.getJobEvents(JobState.COMPLETED).get(0);
    assertThat(completedEvent.getPayload()).isEqualTo("{\"a\":3}");
    assertThat(completedEvent.getCustomHeaders()).containsEntry("b", "2");
  }

  @Test
  public void shouldCloseSubscription() throws InterruptedException {
    // given
    eventRecorder.startRecordingEvents();

    final RecordingJobHandler jobHandler = new RecordingJobHandler();

    final JobWorker subscription =
        jobClient
            .newWorker()
            .jobType("foo")
            .handler(jobHandler)
            .timeout(Duration.ofMinutes(5))
            .name("test")
            .open();

    // when
    subscription.close();

    // then
    jobClient.newCreateCommand().jobType("foo").send();

    createJobOfType("foo");

    waitUntil(() -> eventRecorder.hasJobEvent(JobState.CREATED));

    assertThat(jobHandler.getHandledJobs()).isEmpty();
    assertThat(eventRecorder.hasJobCommand(c -> c.getName() == JobCommandName.ACTIVATE)).isFalse();
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
              c.newCompleteCommand(j).send().join();
            });

    jobClient
        .newWorker()
        .jobType("foo")
        .handler(handler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .bufferSize(10)
        .open();

    // when
    waitUntil(() -> handler.getHandledJobs().size() == numJobs);

    // then
    assertThat(handler.getHandledJobs()).hasSize(numJobs);
  }

  @Test
  public void shouldMarkJobAsFailedAndRetryIfHandlerThrowsException() {
    // given
    eventRecorder.startRecordingEvents();

    final JobEvent job = createJobOfType("foo");

    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, j) -> {
              throw new RuntimeException("expected failure");
            },
            (c, j) -> c.newCompleteCommand(j).withoutPayload().send().join());

    // when
    jobClient
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then the subscription is not broken and other jobs are still handled
    waitUntil(() -> jobHandler.getHandledJobs().size() == 2);

    final long jobKey = job.getKey();
    assertThat(jobHandler.getHandledJobs())
        .extracting("metadata.key")
        .containsExactly(jobKey, jobKey);
    assertThat(eventRecorder.hasJobEvent(state(JobState.FAILED))).isTrue();
    waitUntil(() -> eventRecorder.hasJobEvent(state(JobState.COMPLETED)));
  }

  @Test
  public void shouldNotLockJobIfRetriesAreExhausted() {
    // given
    eventRecorder.startRecordingEvents();

    jobClient.newCreateCommand().jobType("foo").retries(1).send().join();

    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, t) -> {
              throw new RuntimeException("expected failure");
            });

    // when
    jobClient
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    waitUntil(() -> eventRecorder.hasJobEvent(state(JobState.FAILED).and(jobRetries(0))));

    assertThat(jobHandler.getHandledJobs()).hasSize(1);
  }

  @Test
  public void shouldUpdateJobRetries() {
    // given
    eventRecorder.startRecordingEvents();

    final JobEvent job = jobClient.newCreateCommand().jobType("foo").retries(1).send().join();

    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, j) -> {
              throw new RuntimeException("expected failure");
            },
            (c, j) -> c.newCompleteCommand(j).withoutPayload().send().join());

    jobClient
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    waitUntil(() -> jobHandler.getHandledJobs().size() == 1);
    waitUntil(() -> eventRecorder.hasJobEvent(state(JobState.FAILED).and(jobRetries(0))));

    // when
    final JobEvent updatedJob = jobClient.newUpdateRetriesCommand(job).retries(2).send().join();

    // then
    assertThat(updatedJob.getKey()).isEqualTo(job.getKey());

    waitUntil(() -> jobHandler.getHandledJobs().size() == 2);
    waitUntil(() -> eventRecorder.hasJobEvent(state(JobState.COMPLETED)));
  }

  @Test
  public void shouldExpireJobLock() {
    // given
    eventRecorder.startRecordingEvents();

    final JobEvent job = createJobOfType("foo");

    final RecordingJobHandler jobHandler =
        new RecordingJobHandler(
            (c, t) -> {
              // don't complete the job - just wait for lock expiration
            });

    jobClient
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

    final long jobKey = job.getKey();
    assertThat(jobHandler.getHandledJobs())
        .hasSize(2)
        .extracting("metadata.key")
        .containsExactly(jobKey, jobKey);

    assertThat(eventRecorder.hasJobEvent(state(JobState.TIMED_OUT))).isTrue();
  }

  @Test
  public void shouldGiveJobToSingleSubscription() {
    // given
    final RecordingJobHandler jobHandler =
        new RecordingJobHandler((c, t) -> c.newCompleteCommand(t).withoutPayload().send().join());

    jobClient
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    jobClient
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

    jobClient
        .newWorker()
        .jobType("foo")
        .handler(jobHandler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    jobClient
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
    jobClient
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
  public void shouldReceiveJobsFromMultiplePartitions() {
    // given
    final String topicName = "gyros";
    final int numPartitions = 2;

    final ZeebeClient client = clientRule.getClient();
    clientRule
        .getClient()
        .newCreateTopicCommand()
        .name(topicName)
        .partitions(numPartitions)
        .replicationFactor(1)
        .send()
        .join();
    clientRule.waitUntilTopicsExists(topicName);

    final Topics topics = client.newTopicsRequest().send().join();
    final Topic topic =
        topics.getTopics().stream().filter(t -> t.getName().equals(topicName)).findFirst().get();

    final Integer[] partitionIds =
        topic.getPartitions().stream().mapToInt(p -> p.getId()).boxed().toArray(Integer[]::new);

    final String jobType = "foooo";

    final RecordingJobHandler handler = new RecordingJobHandler();

    createJobOfTypeOnPartition(jobType, topicName, partitionIds[0]);
    createJobOfTypeOnPartition(jobType, topicName, partitionIds[1]);

    // when
    clientRule
        .getClient()
        .topicClient(topicName)
        .jobClient()
        .newWorker()
        .jobType(jobType)
        .handler(handler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then
    waitUntil(() -> handler.getHandledJobs().size() == 2);

    final Integer[] receivedPartitionIds =
        handler
            .getHandledJobs()
            .stream()
            .map(t -> t.getMetadata().getPartitionId())
            .toArray(Integer[]::new);

    assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
  }

  private JobEvent createJobOfType(String type) {
    return jobClient.newCreateCommand().jobType(type).send().join();
  }

  private JobEvent createJobOfTypeOnPartition(String type, String topic, int partition) {
    final CreateJobCommandImpl createCommand =
        (CreateJobCommandImpl) jobClient.newCreateCommand().jobType(type);

    createCommand.getCommand().setPartitionId(partition);

    return createCommand.send().join();
  }
}
