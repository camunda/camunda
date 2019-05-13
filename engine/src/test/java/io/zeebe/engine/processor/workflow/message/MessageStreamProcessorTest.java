/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.message;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.StreamProcessorControl;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MessageStreamProcessorTest {

  @Rule public StreamProcessorRule rule = new StreamProcessorRule();

  @Mock private SubscriptionCommandSender mockSubscriptionCommandSender;

  private StreamProcessorControl streamProcessor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    when(mockSubscriptionCommandSender.openWorkflowInstanceSubscription(
            anyLong(), anyLong(), any(), anyBoolean()))
        .thenReturn(true);

    when(mockSubscriptionCommandSender.correlateWorkflowInstanceSubscription(
            anyLong(), anyLong(), any(), anyLong(), any()))
        .thenReturn(true);

    when(mockSubscriptionCommandSender.closeWorkflowInstanceSubscription(
            anyLong(), anyLong(), any(DirectBuffer.class)))
        .thenReturn(true);

    streamProcessor =
        rule.runTypedStreamProcessor(
            (typedEventStreamProcessorBuilder, zeebeDb, dbContext) -> {
              final ZeebeState zeebeState = new ZeebeState(zeebeDb, dbContext);
              MessageEventProcessors.addMessageProcessors(
                  typedEventStreamProcessorBuilder, zeebeState, mockSubscriptionCommandSender);
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

    Assertions.assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(MessageSubscriptionIntent.OPEN);
    Assertions.assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.INVALID_STATE);

    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .openWorkflowInstanceSubscription(
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            any(),
            anyBoolean());
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
    final long messageKey =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst().getKey();
    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .correlateWorkflowInstanceSubscription(
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageName(),
            messageKey,
            message.getVariables());
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
    final long messageKey =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst().getKey();

    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .correlateWorkflowInstanceSubscription(
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageName(),
            messageKey,
            message.getVariables());
  }

  @Test
  public void shouldRejectCorrelateIfMessageSubscriptionClosed() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    rule.writeCommand(MessageIntent.PUBLISH, message);

    streamProcessor.blockAfterMessageSubscriptionEvent(
        m -> m.getMetadata().getIntent() == MessageSubscriptionIntent.OPENED);

    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.writeCommand(MessageSubscriptionIntent.CLOSE, subscription);
    rule.writeCommand(MessageSubscriptionIntent.CORRELATE, subscription);

    streamProcessor.unblock();

    // then
    final TypedRecord<MessageSubscriptionRecord> rejection =
        awaitAndGetFirstSubscriptionRejection();

    Assertions.assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(MessageSubscriptionIntent.CORRELATE);
    Assertions.assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.NOT_FOUND);
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

    Assertions.assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(MessageSubscriptionIntent.CLOSE);
    Assertions.assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.NOT_FOUND);

    // cannot verify messageName buffer since it is a view around another buffer which is changed
    // by the time we perform the verification.
    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .closeWorkflowInstanceSubscription(
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            any(DirectBuffer.class));
  }

  @Test
  public void shouldNotCorrelateNewMessagesIfSubscriptionNotCorrelatable() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    streamProcessor.blockAfterMessageEvent(
        m -> m.getMetadata().getIntent() == MessageIntent.PUBLISHED);

    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);
    rule.writeCommand(MessageIntent.PUBLISH, message);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.writeCommand(MessageIntent.PUBLISH, message);
    streamProcessor.unblock();

    // then
    final long messageKey =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst().getKey();

    verify(mockSubscriptionCommandSender, timeout(5_000).times(1))
        .correlateWorkflowInstanceSubscription(
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageName(),
            messageKey,
            message.getVariables());
  }

  @Test
  public void shouldCorrelateNewMessagesIfSubscriptionIsReusable() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    subscription.setCloseOnCorrelate(false);
    streamProcessor.blockAfterMessageEvent(
        m -> m.getMetadata().getIntent() == MessageIntent.PUBLISHED);

    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);
    rule.writeCommand(MessageIntent.PUBLISH, message);
    rule.writeCommand(MessageSubscriptionIntent.CORRELATE, subscription);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.writeCommand(MessageIntent.PUBLISH, message);
    streamProcessor.unblock();

    // then
    waitUntil(
        () ->
            rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).limit(2).count()
                == 2);
    final long firstMessageKey =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst().getKey();
    final long lastMessageKey =
        rule.events()
            .onlyMessageRecords()
            .withIntent(MessageIntent.PUBLISHED)
            .skip(1)
            .getFirst()
            .getKey();

    verify(mockSubscriptionCommandSender, timeout(5_000))
        .correlateWorkflowInstanceSubscription(
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageName(),
            firstMessageKey,
            message.getVariables());

    verify(mockSubscriptionCommandSender, timeout(5_000))
        .correlateWorkflowInstanceSubscription(
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageName(),
            lastMessageKey,
            message.getVariables());
  }

  @Test
  public void shouldCorrelateMultipleMessagesOneBeforeOpenOneAfter() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription().setCloseOnCorrelate(false);
    final MessageRecord first = message().setVariables(asMsgPack("foo", "bar"));
    final MessageRecord second = message().setVariables(asMsgPack("foo", "baz"));

    // when
    streamProcessor.blockAfterMessageSubscriptionEvent(
        m -> m.getMetadata().getIntent() == MessageSubscriptionIntent.OPENED);

    rule.writeCommand(MessageIntent.PUBLISH, first);
    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());
    rule.writeCommand(MessageSubscriptionIntent.CORRELATE, subscription);
    streamProcessor.unblock();
    rule.writeCommand(MessageIntent.PUBLISH, second);

    // then
    assertAllMessagesReceived(subscription, first, second);
  }

  @Test
  public void shouldCorrelateMultipleMessagesTwoBeforeOpen() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription().setCloseOnCorrelate(false);
    final MessageRecord first = message().setVariables(asMsgPack("foo", "bar"));
    final MessageRecord second = message().setVariables(asMsgPack("foo", "baz"));

    // when
    streamProcessor.blockAfterMessageSubscriptionEvent(
        m -> m.getMetadata().getIntent() == MessageSubscriptionIntent.OPENED);

    rule.writeCommand(MessageIntent.PUBLISH, first);
    rule.writeCommand(MessageIntent.PUBLISH, second);
    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());
    rule.writeCommand(MessageSubscriptionIntent.CORRELATE, subscription);
    streamProcessor.unblock();

    // then
    assertAllMessagesReceived(subscription, first, second);
  }

  @Test
  public void shouldCorrelateToFirstSubscriptionAfterRejection() {
    // given
    final MessageRecord message = message();
    final MessageSubscriptionRecord firstSubscription =
        messageSubscription().setElementInstanceKey(5L);
    final MessageSubscriptionRecord secondSubscription =
        messageSubscription().setElementInstanceKey(10L);
    streamProcessor.blockAfterMessageSubscriptionEvent(
        r ->
            r.getMetadata().getIntent() == MessageSubscriptionIntent.OPENED
                && r.getValue().getElementInstanceKey()
                    == secondSubscription.getElementInstanceKey());

    // when
    rule.writeCommand(MessageIntent.PUBLISH, message);
    rule.writeCommand(MessageSubscriptionIntent.OPEN, firstSubscription);
    rule.writeCommand(MessageSubscriptionIntent.OPEN, secondSubscription);
    waitUntil(streamProcessor::isBlocked);

    final long messageKey =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst().getKey();
    firstSubscription.setMessageKey(messageKey);
    rule.writeCommand(MessageSubscriptionIntent.REJECT, firstSubscription);
    streamProcessor.unblock();

    // then
    verify(mockSubscriptionCommandSender, timeout(5_000))
        .correlateWorkflowInstanceSubscription(
            eq(firstSubscription.getWorkflowInstanceKey()),
            eq(firstSubscription.getElementInstanceKey()),
            any(DirectBuffer.class),
            eq(messageKey),
            any(DirectBuffer.class));

    verify(mockSubscriptionCommandSender, timeout(5_000))
        .correlateWorkflowInstanceSubscription(
            eq(secondSubscription.getWorkflowInstanceKey()),
            eq(secondSubscription.getElementInstanceKey()),
            any(DirectBuffer.class),
            eq(messageKey),
            any(DirectBuffer.class));
  }

  private void assertAllMessagesReceived(
      MessageSubscriptionRecord subscription, MessageRecord first, MessageRecord second) {
    final ArgumentCaptor<DirectBuffer> nameCaptor = ArgumentCaptor.forClass(DirectBuffer.class);
    final ArgumentCaptor<DirectBuffer> variablesCaptor =
        ArgumentCaptor.forClass(DirectBuffer.class);

    waitUntil(
        () ->
            rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).limit(2).count()
                == 2);
    final long firstMessageKey =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst().getKey();
    final long lastMessageKey =
        rule.events()
            .onlyMessageRecords()
            .withIntent(MessageIntent.PUBLISHED)
            .skip(1)
            .getFirst()
            .getKey();

    verify(mockSubscriptionCommandSender, timeout(5_000))
        .correlateWorkflowInstanceSubscription(
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            nameCaptor.capture(),
            eq(firstMessageKey),
            variablesCaptor.capture());

    verify(mockSubscriptionCommandSender, timeout(5_000))
        .correlateWorkflowInstanceSubscription(
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            nameCaptor.capture(),
            eq(lastMessageKey),
            variablesCaptor.capture());

    assertThat(variablesCaptor.getAllValues().get(0)).isEqualTo(first.getVariables());
    assertThat(nameCaptor.getValue()).isEqualTo(subscription.getMessageName());
    assertThat(BufferUtil.equals(nameCaptor.getAllValues().get(1), second.getName())).isTrue();
    assertThat(BufferUtil.equals(variablesCaptor.getAllValues().get(1), second.getVariables()))
        .isTrue();
  }

  private MessageSubscriptionRecord messageSubscription() {
    final MessageSubscriptionRecord subscription = new MessageSubscriptionRecord();
    subscription
        .setWorkflowInstanceKey(1L)
        .setElementInstanceKey(2L)
        .setMessageKey(-1L)
        .setMessageName(wrapString("order canceled"))
        .setCorrelationKey(wrapString("order-123"))
        .setCloseOnCorrelate(true);

    return subscription;
  }

  private MessageRecord message() {
    final MessageRecord message = new MessageRecord();
    message
        .setName(wrapString("order canceled"))
        .setCorrelationKey(wrapString("order-123"))
        .setTimeToLive(1L)
        .setVariables(asMsgPack("orderId", "order-123"));

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
