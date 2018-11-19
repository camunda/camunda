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
package io.zeebe.broker.subscription.message;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.processor.MessageEventProcessors;
import io.zeebe.broker.subscription.message.processor.MessageObserver;
import io.zeebe.broker.util.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MessageStreamProcessorTest {

  @Rule public StreamProcessorRule rule = new StreamProcessorRule();

  @Mock private SubscriptionCommandSender mockSubscriptionCommandSender;
  @Mock private TopologyManager mockTopologyManager;

  private StreamProcessorControl streamProcessor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    when(mockSubscriptionCommandSender.openWorkflowInstanceSubscription(
            anyLong(), anyLong(), any()))
        .thenReturn(true);

    when(mockSubscriptionCommandSender.correlateWorkflowInstanceSubscription(
            anyLong(), anyLong(), any(), any()))
        .thenReturn(true);

    when(mockSubscriptionCommandSender.closeWorkflowInstanceSubscription(anyLong(), anyLong()))
        .thenReturn(true);

    streamProcessor =
        rule.runStreamProcessor(
            (typedEventStreamProcessorBuilder, zeebeState) -> {
              MessageEventProcessors.addMessageProcessors(
                  typedEventStreamProcessorBuilder,
                  zeebeState,
                  mockSubscriptionCommandSender,
                  mockTopologyManager);
              return typedEventStreamProcessorBuilder.build();
            });
  }

  @Test
  public void shouldRejectDuplicatedOpenMessageSubscription() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();

    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    // when
    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    streamProcessor.unblock();

    // then
    final TypedRecord<MessageSubscriptionRecord> rejection =
        awaitAndGetFirstSubscriptionRejection();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(MessageSubscriptionIntent.OPEN);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("subscription is already open");

    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .openWorkflowInstanceSubscription(
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            any());
  }

  @Test
  public void shouldRetryToCorrelateMessageSubscriptionAfterPublishedMessage() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    streamProcessor.blockAfterMessageEvent(
        m -> m.getMetadata().getIntent() == MessageIntent.PUBLISHED);

    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);
    rule.writeCommand(MessageIntent.PUBLISH, message);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.getClock()
        .addTime(
            MessageObserver.SUBSCRIPTION_CHECK_INTERVAL.plus(MessageObserver.SUBSCRIPTION_TIMEOUT));

    streamProcessor.unblock();

    // then
    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .correlateWorkflowInstanceSubscription(
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageName(),
            message.getPayload());
  }

  @Test
  public void shouldRetryToCorrelateMessageSubscriptionAfterOpenedSubscription() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    streamProcessor.blockAfterMessageSubscriptionEvent(
        m -> m.getMetadata().getIntent() == MessageSubscriptionIntent.OPENED);

    rule.writeCommand(MessageIntent.PUBLISH, message);
    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.getClock()
        .addTime(
            MessageObserver.SUBSCRIPTION_CHECK_INTERVAL.plus(MessageObserver.SUBSCRIPTION_TIMEOUT));

    streamProcessor.unblock();

    // then
    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .correlateWorkflowInstanceSubscription(
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageName(),
            message.getPayload());
  }

  @Test
  public void shouldRejectDuplicatedCorrelateMessageSubscription() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    rule.writeCommand(MessageIntent.PUBLISH, message);

    streamProcessor.blockAfterMessageSubscriptionEvent(
        m -> m.getMetadata().getIntent() == MessageSubscriptionIntent.OPENED);

    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.writeCommand(MessageSubscriptionIntent.CORRELATE, subscription);
    rule.writeCommand(MessageSubscriptionIntent.CORRELATE, subscription);

    streamProcessor.unblock();

    // then
    final TypedRecord<MessageSubscriptionRecord> rejection =
        awaitAndGetFirstSubscriptionRejection();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(MessageSubscriptionIntent.CORRELATE);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("subscription is already correlated");
  }

  @Test
  public void shouldRejectDuplicatedCloseMessageSubscription() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();

    streamProcessor.blockAfterMessageSubscriptionEvent(
        m -> m.getMetadata().getIntent() == MessageSubscriptionIntent.OPENED);

    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.writeCommand(MessageSubscriptionIntent.CLOSE, subscription);
    rule.writeCommand(MessageSubscriptionIntent.CLOSE, subscription);

    streamProcessor.unblock();

    // then
    final TypedRecord<MessageSubscriptionRecord> rejection =
        awaitAndGetFirstSubscriptionRejection();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(MessageSubscriptionIntent.CLOSE);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("subscription is already closed");

    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .closeWorkflowInstanceSubscription(
            subscription.getWorkflowInstanceKey(), subscription.getElementInstanceKey());
  }

  private MessageSubscriptionRecord messageSubscription() {
    final MessageSubscriptionRecord subscription = new MessageSubscriptionRecord();
    subscription
        .setWorkflowInstanceKey(1L)
        .setElementInstanceKey(2L)
        .setMessageName(wrapString("order canceled"))
        .setCorrelationKey(wrapString("order-123"));

    return subscription;
  }

  private MessageRecord message() {
    final MessageRecord message = new MessageRecord();
    message
        .setName(wrapString("order canceled"))
        .setCorrelationKey(wrapString("order-123"))
        .setTimeToLive(1L)
        .setPayload(asMsgPack("orderId", "order-123"));

    return message;
  }

  private TypedRecord<MessageSubscriptionRecord> awaitAndGetFirstSubscriptionRejection() {
    waitUntil(
        () ->
            rule.events()
                .onlyMessageSubscriptionRecords()
                .onlyRejections()
                .findFirst()
                .isPresent());

    return rule.events().onlyMessageSubscriptionRecords().onlyRejections().findFirst().get();
  }
}
