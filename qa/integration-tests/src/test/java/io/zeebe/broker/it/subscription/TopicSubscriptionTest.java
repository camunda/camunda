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
package io.zeebe.broker.it.subscription;

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.JobCommandName;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.record.Record;
import io.zeebe.gateway.api.record.RecordMetadata;
import io.zeebe.gateway.api.record.ValueType;
import io.zeebe.gateway.api.subscription.RecordHandler;
import io.zeebe.gateway.api.subscription.TopicSubscription;
import io.zeebe.gateway.impl.job.CreateJobCommandImpl;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class TopicSubscriptionTest {

  public static final String SUBSCRIPTION_NAME = "foo";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(3));

  public ClientRule clientRule = new ClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public Timeout timeout = Timeout.seconds(30);

  protected ZeebeClient client;
  protected RecordingEventHandler recordingHandler;
  protected ObjectMapper objectMapper;

  @Before
  public void setUp() {
    this.client = clientRule.getClient();
    this.recordingHandler = new RecordingEventHandler();
    this.objectMapper = new ObjectMapper();
  }

  @Test
  public void shouldOpenSubscription() {
    // when
    final TopicSubscription subscription =
        client.newSubscription().name(SUBSCRIPTION_NAME).recordHandler(recordingHandler).open();

    // then
    assertThat(subscription.isOpen()).isTrue();
  }

  @Test
  public void shouldReceiveEventsCreatedAfterSubscription() throws IOException {
    // given
    client.newSubscription().name(SUBSCRIPTION_NAME).recordHandler(recordingHandler).open();

    // when
    final JobEvent job = client.jobClient().newCreateCommand().jobType("foo").send().join();

    // then
    waitUntil(() -> recordingHandler.numJobRecords() == 2);

    assertThat(recordingHandler.numJobRecords()).isEqualTo(2);

    final long taskKey = job.getKey();
    recordingHandler.assertJobRecord(0, ExecuteCommandResponseDecoder.keyNullValue(), "CREATE");
    recordingHandler.assertJobRecord(1, taskKey, "CREATED");
  }

  @Test
  public void shouldReceiveEventsCreatedBeforeSubscription() throws IOException {
    // given
    final JobEvent job = client.jobClient().newCreateCommand().jobType("foo").send().join();

    // when
    client
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(recordingHandler)
        .startAtHead()
        .open();

    // then
    waitUntil(() -> recordingHandler.numJobRecords() == 2);

    assertThat(recordingHandler.numJobRecords()).isEqualTo(2);

    final long jobKey = job.getKey();
    recordingHandler.assertJobRecord(0, ExecuteCommandResponseDecoder.keyNullValue(), "CREATE");
    recordingHandler.assertJobRecord(1, jobKey, "CREATED");
  }

  @Test
  public void shouldReceiveEventsFromTailOfLog() throws IOException {
    client.jobClient().newCreateCommand().jobType("foo").send().join();

    client
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(recordingHandler)
        .startAtTail()
        .open();

    // when
    final JobEvent job2 = client.jobClient().newCreateCommand().jobType("foo").send().join();

    // then

    waitUntil(() -> recordingHandler.numJobRecords() >= 2);

    assertThat(recordingHandler.numJobRecords()).isEqualTo(2);

    // task 1 has not been received
    final long job2Key = job2.getKey();
    recordingHandler.assertJobRecord(0, ExecuteCommandResponseDecoder.keyNullValue(), "CREATE");
    recordingHandler.assertJobRecord(1, job2Key, "CREATED");
  }

  @Test
  public void shouldReceiveEventsFromPosition() {
    client.jobClient().newCreateCommand().jobType("foo").send().join();

    client
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(recordingHandler)
        .startAtHead()
        .open();

    waitUntil(() -> recordingHandler.numJobRecords() == 2);

    final List<Record> recordedTaskEvents =
        recordingHandler
            .getRecords()
            .stream()
            .filter((re) -> re.getMetadata().getValueType() == ValueType.JOB)
            .collect(Collectors.toList());

    final RecordingEventHandler subscription2Handler = new RecordingEventHandler();
    final RecordMetadata metadata = recordedTaskEvents.get(1).getMetadata();
    final long secondTaskEventPosition = metadata.getPosition();

    // when
    client
        .newSubscription()
        .name("another" + SUBSCRIPTION_NAME)
        .recordHandler(subscription2Handler)
        .startAtPosition(metadata.getPartitionId(), secondTaskEventPosition)
        .open();

    // then
    waitUntil(() -> subscription2Handler.numRecords() > 0);

    // only the second event is pushed to the second subscription
    final Record firstEvent = subscription2Handler.getRecords().get(0);
    assertThat(firstEvent.getMetadata().getPosition()).isEqualTo(secondTaskEventPosition);
  }

  @Test
  public void shouldReceiveEventsFromPositionBeyondTail() {
    // given
    client
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(recordingHandler)
        .startAtPosition(0, Long.MAX_VALUE)
        .open();

    client.jobClient().newCreateCommand().jobType("foo").send().join();

    // then
    waitUntil(() -> recordingHandler.numJobRecords() == 2);

    // the events are nevertheless received, although they have a lower position
    assertThat(recordingHandler.numJobRecords()).isEqualTo(2);
  }

  @Test
  public void shouldCloseSubscription() throws InterruptedException {
    // given
    final TopicSubscription subscription =
        client.newSubscription().name(SUBSCRIPTION_NAME).recordHandler(recordingHandler).open();

    // when
    subscription.close();

    // then
    assertThat(subscription.isOpen()).isFalse();

    client.jobClient().newCreateCommand().jobType("foo").send().join();

    Thread.sleep(1000L);
    assertThat(recordingHandler.numJobRecords()).isEqualTo(0);
  }

  @Test
  public void shouldRepeatedlyRecoverSubscription() {
    for (int i = 0; i < 100; i++) {
      // given
      final TopicSubscription subscription =
          client.newSubscription().name(SUBSCRIPTION_NAME).recordHandler(recordingHandler).open();

      client.jobClient().newCreateCommand().jobType("foo").send().join();

      final int eventCount = i;
      waitUntil(() -> recordingHandler.numJobRecords() >= eventCount);

      // when
      subscription.close();

      // then
      assertThat(subscription.isOpen()).isFalse();
    }

    assertThat(recordingHandler.numRecords()).isGreaterThan(100);
  }

  @Test
  public void shouldOpenMultipleSubscriptions() throws IOException {
    // given
    final JobEvent job = client.jobClient().newCreateCommand().jobType("foo").send().join();

    client
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(recordingHandler)
        .startAtHead()
        .open();

    final RecordingEventHandler secondEventHandler = new RecordingEventHandler();

    client
        .newSubscription()
        .name("another" + SUBSCRIPTION_NAME)
        .recordHandler(secondEventHandler)
        .startAtHead()
        .open();

    // when
    waitUntil(() -> recordingHandler.numJobRecords() == 2);
    waitUntil(() -> secondEventHandler.numJobRecords() == 2);

    // then
    final long jobKey = job.getKey();
    recordingHandler.assertJobRecord(0, ExecuteCommandResponseDecoder.keyNullValue(), "CREATE");
    recordingHandler.assertJobRecord(1, jobKey, "CREATED");
    secondEventHandler.assertJobRecord(0, ExecuteCommandResponseDecoder.keyNullValue(), "CREATE");
    secondEventHandler.assertJobRecord(1, jobKey, "CREATED");
  }

  @Test
  public void shouldHandleOneEventAtATime() throws InterruptedException {
    client.jobClient().newCreateCommand().jobType("foo").send().join();

    final Duration handlingIntervalLength = Duration.ofSeconds(1);
    final ParallelismDetectionHandler handler =
        new ParallelismDetectionHandler(handlingIntervalLength);

    // when
    client.newSubscription().name(SUBSCRIPTION_NAME).recordHandler(handler).startAtHead().open();

    // then
    final int numExpectedEvents = 2;
    Thread.sleep(handlingIntervalLength.toMillis() * numExpectedEvents);

    // at least CREATE and CREATED of the task, but we may have already handled a third event (e.g.
    // raft)
    waitUntil(() -> handler.numInvocations() >= numExpectedEvents);
    assertThat(handler.hasDetectedParallelism()).isFalse();
  }

  @Test
  public void shouldResumeSubscription() {
    client.jobClient().newCreateCommand().jobType("foo").send().join();

    final TopicSubscription subscription =
        client
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtHead()
            .open();

    // that was received by the subscription
    waitUntil(() -> recordingHandler.numJobRecords() == 2);

    subscription.close();

    final long lastEventPosition =
        recordingHandler
            .getRecords()
            .get(recordingHandler.numRecords() - 1)
            .getMetadata()
            .getPosition();

    recordingHandler.reset();

    // and a second not-yet-received job
    client.jobClient().newCreateCommand().jobType("foo").send().join();

    // when
    client
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(recordingHandler)
        .startAtHead()
        .open();

    // then
    waitUntil(() -> recordingHandler.numRecords() > 0);

    final long firstEventPositionAfterReopen =
        recordingHandler.getRecords().get(0).getMetadata().getPosition();

    assertThat(firstEventPositionAfterReopen).isGreaterThan(lastEventPosition);
  }

  protected static class ParallelismDetectionHandler implements RecordHandler {

    protected AtomicBoolean executing = new AtomicBoolean(false);
    protected AtomicBoolean parallelInvocationDetected = new AtomicBoolean(false);
    protected AtomicInteger numInvocations = new AtomicInteger(0);
    protected long timeout;

    public ParallelismDetectionHandler(final Duration duration) {
      this.timeout = duration.toMillis();
    }

    @Override
    public void onRecord(final Record record) throws Exception {
      numInvocations.incrementAndGet();
      if (executing.compareAndSet(false, true)) {
        try {
          Thread.sleep(timeout);
        } finally {
          executing.set(false);
        }
      } else {
        parallelInvocationDetected.set(true);
      }
    }

    public boolean hasDetectedParallelism() {
      return parallelInvocationDetected.get();
    }

    public int numInvocations() {
      return numInvocations.get();
    }
  }

  @Test
  public void testNameUniqueness() {
    // given
    client.newSubscription().name(SUBSCRIPTION_NAME).recordHandler(recordingHandler).open();

    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Could not open subscriber group");

    // when
    client.newSubscription().name(SUBSCRIPTION_NAME).recordHandler(recordingHandler).open();
  }

  /** E.g. subscription ACKs should not be pushed to the client */
  @Test
  public void shouldNotPushAnySubscriptionEvents() {
    // given
    client.newSubscription().name(SUBSCRIPTION_NAME).recordHandler(recordingHandler).open();

    client.jobClient().newCreateCommand().jobType("foo").send().join();

    // then
    waitUntil(() -> recordingHandler.numJobRecords() == 2);

    assertThat(recordingHandler.getRecords())
        .filteredOn((re) -> re.getMetadata().getValueType() == ValueType.UNKNOWN)
        .isEmpty();
  }

  @Test
  public void shouldReceiveMoreEventsThanSubscriptionCapacity() {
    // given
    final int subscriptionCapacity =
        clientRule.getClient().getConfiguration().getDefaultTopicSubscriptionBufferSize();

    for (int i = 0; i < subscriptionCapacity + 1; i++) {
      client.jobClient().newCreateCommand().jobType("foo").send().join();
    }

    // when
    client
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(recordingHandler)
        .startAtHead()
        .open();

    // then
    waitUntil(() -> recordingHandler.numRecords() > subscriptionCapacity);
  }

  @Test
  public void shouldReceiveEventsFromMultiplePartitions() {
    // given
    final ZeebeClient zeebeClient = clientRule.getClient();
    final int numPartitions = 3;

    final Integer[] partitionIds =
        IntStream.range(0, numPartitions).boxed().toArray(Integer[]::new);

    for (final int partitionId : partitionIds) {
      createTaskOnPartition(partitionId);
    }

    final List<Integer> receivedPartitionIds = new ArrayList<>();

    // when
    zeebeClient
        .newSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(recordingHandler)
        .jobCommandHandler(
            c -> {
              if (c.getName() == JobCommandName.CREATE) {
                receivedPartitionIds.add(c.getMetadata().getPartitionId());
              }
            })
        .startAtHead()
        .open();

    // then
    waitUntil(() -> receivedPartitionIds.size() == numPartitions);

    assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
  }

  protected void createTaskOnPartition(final int partition) {
    final CreateJobCommandImpl createCommand =
        (CreateJobCommandImpl) clientRule.getClient().jobClient().newCreateCommand().jobType("baz");

    createCommand.getCommand().setPartitionId(partition);
    createCommand.send().join();
  }
}
