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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.processor.MessageStreamProcessor;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
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

    when(mockSubscriptionCommandSender.openedMessageSubscription(
            anyInt(), anyLong(), anyLong(), any()))
        .thenReturn(true);

    streamProcessor =
        rule.runStreamProcessor(
            env -> {
              final MessageStreamProcessor streamProcessor =
                  new MessageStreamProcessor(mockSubscriptionCommandSender, mockTopologyManager);

              return streamProcessor.createStreamProcessors(env);
            });
  }

  @Test
  public void shouldRejectDuplicatedMessageSubscription() {
    // given
    final MessageSubscriptionRecord subscription = new MessageSubscriptionRecord();
    subscription
        .setWorkflowInstancePartitionId(0)
        .setWorkflowInstanceKey(1L)
        .setActivityInstanceKey(2L)
        .setMessageName(wrapString("order canceled"))
        .setCorrelationKey(wrapString("order-123"));

    rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    // when
    final long secondCommandPosition =
        rule.writeCommand(MessageSubscriptionIntent.OPEN, subscription);

    streamProcessor.unblock();

    // then
    waitUntil(
        () ->
            rule.events()
                .onlyMessageSubscriptionRecords()
                .onlyRejections()
                .findFirst()
                .isPresent());

    final TypedRecord<MessageSubscriptionRecord> rejection =
        rule.events().onlyMessageSubscriptionRecords().onlyRejections().findFirst().get();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(MessageSubscriptionIntent.OPEN);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("subscription is already open");

    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .openedMessageSubscription(
            eq(subscription.getWorkflowInstancePartitionId()),
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getActivityInstanceKey()),
            any());
  }
}
