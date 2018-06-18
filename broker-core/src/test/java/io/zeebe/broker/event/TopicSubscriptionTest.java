/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.SubscriberIntent;
import io.zeebe.protocol.intent.SubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageResponse;
import io.zeebe.test.broker.protocol.clientapi.ErrorResponse;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.RawMessage;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionTest {
  public static final int MAXIMUM_SUBSCRIPTION_NAME_LENGTH = 32;

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Test
  public void shouldOpenSubscription() {
    // when
    final ExecuteCommandResponse subscriptionResponse =
        apiRule.openTopicSubscription("foo", 0).await();

    // then
    assertThat(subscriptionResponse.key()).isGreaterThanOrEqualTo(0);
  }

  @Test
  public void shouldCloseSubscription() {
    // given
    final ExecuteCommandResponse addResponse = apiRule.openTopicSubscription("foo", 0).await();

    final long subscriberKey = addResponse.key();

    // when
    final ControlMessageResponse removeResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
            .put("subscriberKey", subscriberKey)
            .done()
            .sendAndAwait();

    // then
    assertThat(removeResponse.getData()).containsOnly(entry("subscriberKey", subscriberKey));
  }

  @Test
  public void shouldNotPushEventsAfterClose() throws InterruptedException {
    // given
    final ExecuteCommandResponse addResponse = apiRule.openTopicSubscription("foo", 0).await();

    final long subscriberKey = addResponse.key();

    apiRule
        .createControlMessageRequest()
        .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
        .partitionId(apiRule.getDefaultPartitionId())
        .data()
        .put("subscriberKey", subscriberKey)
        .done()
        .sendAndAwait();

    apiRule.moveMessageStreamToTail();

    // when creating a job
    apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.CREATE)
        .command()
        .put("type", "theJobType")
        .done()
        .sendAndAwait();

    // then no events are received
    Thread.sleep(1000L);
    assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(0);
  }

  @Test
  public void shouldPushEvents() {
    // given
    final long fixedClockEpoch = 1L;
    final ControlledActorClock clock = brokerRule.getClock();
    clock.setCurrentTime(fixedClockEpoch);

    // given
    final ExecuteCommandResponse createJobResponse =
        apiRule
            .createCmdRequest()
            .type(ValueType.JOB, JobIntent.CREATE)
            .command()
            .put("type", "foo")
            .put("retries", 1)
            .done()
            .sendAndAwait();
    final long jobKey = createJobResponse.key();

    // when
    final ExecuteCommandResponse addResponse = apiRule.openTopicSubscription("foo", 0).await();

    final long subscriberKey = addResponse.key();

    // then
    final List<SubscribedRecord> jobEvents =
        apiRule
            .subscribedEvents()
            .filter((e) -> e.valueType() == ValueType.JOB)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(jobEvents).hasSize(2);
    SubscribedRecord jobEvent = jobEvents.get(0);
    assertThat(jobEvent.subscriberKey()).isEqualTo(subscriberKey);
    assertThat(jobEvent.subscriptionType()).isEqualTo(SubscriptionType.TOPIC_SUBSCRIPTION);
    assertThat(jobEvent.position()).isEqualTo(jobKey);
    assertThat(jobEvent.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
    assertThat(jobEvent.recordType()).isEqualTo(RecordType.COMMAND);
    assertThat(jobEvent.valueType()).isEqualTo(ValueType.JOB);
    assertThat(jobEvent.intent()).isEqualTo(JobIntent.CREATE);
    assertThat(jobEvent.sourceRecordPosition()).isEqualTo(-1L);
    assertThat(jobEvent.timestamp()).isEqualTo(fixedClockEpoch);

    jobEvent = jobEvents.get(1);
    assertThat(jobEvent.subscriberKey()).isEqualTo(subscriberKey);
    assertThat(jobEvent.subscriptionType()).isEqualTo(SubscriptionType.TOPIC_SUBSCRIPTION);
    assertThat(jobEvent.position()).isGreaterThan(jobKey);
    assertThat(jobEvent.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
    assertThat(jobEvent.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(jobEvent.valueType()).isEqualTo(ValueType.JOB);
    assertThat(jobEvent.intent()).isEqualTo(JobIntent.CREATED);
    assertThat(jobEvent.sourceRecordPosition()).isEqualTo(jobKey);
    assertThat(jobEvent.timestamp()).isEqualTo(fixedClockEpoch);
  }

  @Test
  public void shouldPushRejection() {
    // given
    apiRule
        .createCmdRequest()
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
        .command()
        .put("bpmnProcessId", "does not exist")
        .put("version", -1)
        .done()
        .sendAndAwait();

    // when
    apiRule.openTopicSubscription("foo", 0).await();

    // then
    final SubscribedRecord rejection =
        apiRule
            .subscribedEvents()
            .filter((e) -> e.valueType() == ValueType.WORKFLOW_INSTANCE)
            .filter(e -> e.recordType() == RecordType.COMMAND_REJECTION)
            .findFirst()
            .get();

    assertThat(rejection.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.valueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE);
    assertThat(rejection.intent()).isEqualTo(WorkflowInstanceIntent.CREATE);
    assertThat(rejection.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(rejection.rejectionReason()).isEqualTo("Workflow is not deployed");
  }

  @Test
  public void shouldReturnStartPositionOnOpen() {
    // given
    apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.CREATE)
        .command()
        .put("type", "foo")
        .put("retries", 1)
        .done()
        .sendAndAwait();

    // when
    final ExecuteCommandResponse addResponse = apiRule.openTopicSubscription("foo", 0).await();

    final long startPosition = (long) addResponse.getValue().get("startPosition");

    // then
    final Optional<Long> event = apiRule.subscribedEvents().map((e) -> e.position()).findFirst();

    assertThat(startPosition).isLessThan(event.get());
  }

  @Test
  public void shouldNotOpenSubscriptionForNonExistingPartition() {
    // given
    final ExecuteCommandRequest request =
        apiRule
            .createCmdRequest()
            .partitionId(999)
            .type(ValueType.SUBSCRIBER, SubscriberIntent.SUBSCRIBE)
            .command()
            .put("startPosition", 0)
            .put("name", "foo")
            .done()
            .sendWithoutRetries();

    // when
    final ErrorResponse errorResponse = request.awaitError();

    // then
    final String expectedMessage =
        String.format("Cannot execute command. Partition with id '%d' not found", 999);

    assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.PARTITION_NOT_FOUND);
    assertThat(errorResponse.getErrorData()).isEqualTo(expectedMessage);
  }

  @Test
  public void shouldNotCloseSubscriptionForNonExistingPartition() {
    // when
    final ErrorResponse errorResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .partitionId(999)
            .data()
            .put("subscriberKey", 0L)
            .done()
            .sendWithoutRetries()
            .awaitError();

    // then
    final String expectedMessage =
        String.format(
            "Cannot close topic subscription. No subscription management "
                + "processor registered for partition '%d'",
            999);

    assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
    assertThat(errorResponse.getErrorData()).isEqualTo(expectedMessage);
  }

  @Test
  public void shouldCloseSubscriptionNonExistingSubscription() {
    // when
    final ControlMessageResponse removeResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
            .put("subscriberKey", Long.MAX_VALUE)
            .done()
            .sendAndAwait();

    // then
    assertThat(removeResponse.getData()).containsOnly(entry("subscriberKey", Long.MAX_VALUE));
  }

  @Test
  public void shouldOpenSubscriptionWithMaximumNameLength() {
    // when
    final String subscriptionName = getStringOfLength(MAXIMUM_SUBSCRIPTION_NAME_LENGTH);
    final ExecuteCommandResponse response =
        apiRule.openTopicSubscription(subscriptionName, 0).await();

    // then
    assertThat(response.key()).isGreaterThanOrEqualTo(0);
  }

  @Test
  public void shouldNotOpenSubscriptionWithOverlongName() {
    // given
    final String subscriptionName = getStringOfLength(MAXIMUM_SUBSCRIPTION_NAME_LENGTH + 1);

    // when
    final ErrorResponse errorResponse =
        apiRule.openTopicSubscription(subscriptionName, 0).awaitError();

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
    assertThat(errorResponse.getErrorData())
        .isEqualTo(
            "Cannot open topic subscription '"
                + subscriptionName
                + "'. Subscription name must be "
                + MAXIMUM_SUBSCRIPTION_NAME_LENGTH
                + " characters or shorter.");
  }

  @Test
  public void shouldNotOpenSubscriptionWithNegativeBufferSize() {
    // when
    final ErrorResponse errorResponse =
        apiRule
            .createCmdRequest()
            .type(ValueType.SUBSCRIBER, SubscriberIntent.SUBSCRIBE)
            .command()
            .put("startPosition", -1)
            .put("name", "foo")
            .put("bufferSize", -1)
            .done()
            .send()
            .awaitError();

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
    assertThat(errorResponse.getErrorData())
        .isEqualTo("Cannot open topic subscription 'foo'. Buffer size must be greater than 0.");
  }

  @Test
  public void shouldNotOpenSubscriptionWithMissingBufferSize() {
    // when
    final ErrorResponse errorResponse =
        apiRule
            .createCmdRequest()
            .type(ValueType.SUBSCRIBER, SubscriberIntent.SUBSCRIBE)
            .command()
            .put("startPosition", -1)
            .put("name", "foo")
            .done()
            .send()
            .awaitError();

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE);
    assertThat(errorResponse.getErrorData()).contains("Property 'bufferSize' has no valid value");
  }

  @Test
  public void shouldOpenSubscriptionAndForceStartPosition() {
    // given
    final ExecuteCommandResponse subscriptionResponse =
        apiRule.openTopicSubscription("foo", 0).await();

    final long subscriberKey = subscriptionResponse.key();

    apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.CREATE)
        .command()
        .put("type", "foo")
        .put("retries", 1)
        .done()
        .sendAndAwait();

    // wait for two job events
    final List<Long> jobEvents =
        apiRule
            .subscribedEvents()
            .filter((e) -> e.valueType() == ValueType.JOB)
            .limit(2)
            .map((e) -> e.position())
            .collect(Collectors.toList());

    apiRule
        .createCmdRequest()
        .type(ValueType.SUBSCRIPTION, SubscriptionIntent.ACKNOWLEDGE)
        .command()
        .put("name", "foo")
        .put("ackPosition", jobEvents.get(1))
        .done()
        .sendAndAwait();

    apiRule
        .createControlMessageRequest()
        .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
        .partitionId(apiRule.getDefaultPartitionId())
        .data()
        .put("subscriberKey", subscriberKey)
        .done()
        .sendAndAwait();

    apiRule.moveMessageStreamToTail();

    // when
    apiRule
        .createCmdRequest()
        .type(ValueType.SUBSCRIBER, SubscriberIntent.SUBSCRIBE)
        .command()
        .put("startPosition", jobEvents.get(0))
        .put("name", "foo")
        .put("bufferSize", 32)
        .put("forceStart", true)
        .done()
        .sendAndAwait();

    // then
    final List<Long> jobEventsAfterReopening =
        apiRule
            .subscribedEvents()
            .filter((e) -> e.valueType() == ValueType.JOB)
            .limit(2)
            .map((e) -> e.position())
            .collect(Collectors.toList());

    assertThat(jobEventsAfterReopening).hasSize(2);
    assertThat(jobEventsAfterReopening).containsExactlyElementsOf(jobEvents);
  }

  @Test
  public void shouldPersistStartPositionOnOpen() {
    // given
    final ExecuteCommandResponse subscriptionResponse =
        apiRule.openTopicSubscription("foo", 0).await();
    final long subscriberKey = subscriptionResponse.key();

    apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.CREATE)
        .command()
        .put("type", "foo")
        .put("retries", 1)
        .done()
        .sendAndAwait();

    // wait for two job events, but send no ACK
    final List<Long> jobEvents =
        apiRule
            .subscribedEvents()
            .filter((e) -> e.valueType() == ValueType.JOB)
            .limit(2)
            .map((e) -> e.position())
            .collect(Collectors.toList());

    apiRule
        .createControlMessageRequest()
        .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
        .partitionId(apiRule.getDefaultPartitionId())
        .data()
        .put("subscriberKey", subscriberKey)
        .done()
        .sendAndAwait();
    apiRule.moveMessageStreamToTail();

    // when
    apiRule.openTopicSubscription("foo", jobEvents.get(1)).await();

    // then the subscription should restart at the last ACKED position, which is the original start
    // position
    final List<Long> jobEventsAfterReopen =
        apiRule
            .subscribedEvents()
            .filter((e) -> e.valueType() == ValueType.JOB)
            .limit(2)
            .map((e) -> e.position())
            .collect(Collectors.toList());

    assertThat(jobEventsAfterReopen).containsExactlyElementsOf(jobEvents);
  }

  @Test
  @Ignore
  public void shouldReturnErrorIfSubscriptionProcessorRemovalFails() {
    // given
    final ExecuteCommandResponse subscriptionResponse =
        apiRule.openTopicSubscription("foo", 0).await();

    final long subscriberKey = subscriptionResponse.key();

    // and the subscription service has abnormally closed
    final String name =
        "log.log."
            + ClientApiRule.DEFAULT_TOPIC_NAME
            + "."
            + apiRule.getDefaultPartitionId()
            + ".subscription.push.foo"; // TODO: ist das der richtige Name?
    final ServiceName<Object> subscriptionServiceName =
        ServiceName.newServiceName(name, Object.class);
    brokerRule.removeService(subscriptionServiceName);

    // when
    final ErrorResponse errorResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
            .put("subscriberKey", subscriberKey)
            .done()
            .send()
            .awaitError();

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
    assertThat(errorResponse.getErrorData())
        .contains("Cannot close topic subscription. Cannot remove service");
  }

  @Test
  public void shouldCloseSubscriptionOnTransportChannelClose() {
    // given
    ExecuteCommandResponse subscriptionResponse = apiRule.openTopicSubscription("foo", 0).await();

    final long firstSubscriberKey = subscriptionResponse.key();

    // when the transport channel is closed
    apiRule.interruptAllChannels();

    // then the subscription has been closed and we can reopen it
    subscriptionResponse =
        TestUtil.doRepeatedly(
                () -> {
                  // it is not guaranteed this succeeds the first time as the event of the closed
                  // channel is asynchronous by nature
                  return apiRule.openTopicSubscription("foo", 0).await();
                })
            .until(Objects::nonNull, 50, "Failed to reopen topic subscription");

    final long secondSubscriberKey = subscriptionResponse.key();

    assertThat(secondSubscriberKey).isNotEqualTo(firstSubscriberKey);
  }

  @Test
  public void shouldNotPushEventsBeforeSubscriptionResponse() {
    // given
    apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.CREATE)
        .command()
        .put("type", "foo")
        .put("retries", 1)
        .done()
        .sendAndAwait();

    // when
    apiRule.openTopicSubscription("foo", 0).await();

    // then
    final RawMessage subscriptionResponse =
        apiRule
            .commandResponses()
            .filter((m) -> asCommandResponse(m).intent() == SubscriberIntent.SUBSCRIBED)
            .findFirst()
            .get();

    apiRule.moveMessageStreamToHead();

    final RawMessage firstPushedEvent =
        apiRule.subscribedEvents().findFirst().get().getRawMessage();

    assertThat(firstPushedEvent.getSequenceNumber())
        .isGreaterThan(subscriptionResponse.getSequenceNumber());
  }

  protected String getStringOfLength(int numCharacters) {
    final char[] characters = new char[numCharacters];
    Arrays.fill(characters, 'a');
    return new String(characters);
  }

  protected static ExecuteCommandResponse asCommandResponse(RawMessage message) {
    final ExecuteCommandResponse response = new ExecuteCommandResponse(new MsgPackHelper());
    response.wrap(message.getMessage(), 0, message.getMessage().capacity());
    return response;
  }
}
