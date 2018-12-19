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
package io.zeebe.broker.workflow.processor.message;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionState;
import io.zeebe.broker.workflow.processor.CatchEventBehavior;
import io.zeebe.broker.workflow.state.WorkflowInstanceSubscription;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.function.Consumer;

public final class CorrelateWorkflowInstanceSubscription
    implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final CatchEventBehavior catchEventBehavior;
  private final TopologyManager topologyManager;
  private final WorkflowInstanceSubscriptionState subscriptionState;
  private final SubscriptionCommandSender subscriptionCommandSender;

  private WorkflowInstanceSubscriptionRecord subscriptionRecord;

  public CorrelateWorkflowInstanceSubscription(
      final TopologyManager topologyManager,
      final WorkflowInstanceSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final CatchEventBehavior catchEventBehavior) {
    this.topologyManager = topologyManager;
    this.subscriptionState = subscriptionState;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.catchEventBehavior = catchEventBehavior;
  }

  @Override
  public void onOpen(final TypedStreamProcessor streamProcessor) {
    final ActorControl actor = streamProcessor.getActor();
    final LogStream logStream = streamProcessor.getEnvironment().getStream();

    subscriptionCommandSender.init(topologyManager, actor, logStream);

    final PendingWorkflowInstanceSubscriptionChecker pendingSubscriptionChecker =
        new PendingWorkflowInstanceSubscriptionChecker(
            subscriptionCommandSender, subscriptionState, SUBSCRIPTION_TIMEOUT.toMillis());
    actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }

  @Override
  public void onClose() {}

  @Override
  public void processRecord(
      final TypedRecord<WorkflowInstanceSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    subscriptionRecord = record.getValue();
    final long elementInstanceKey = subscriptionRecord.getElementInstanceKey();

    final WorkflowInstanceSubscription subscription =
        subscriptionState.getSubscription(elementInstanceKey, subscriptionRecord.getMessageName());

    sideEffect.accept(this::sendAcknowledgeCommand);
    if (subscription == null || subscription.isClosing()) {
      streamWriter.appendRejection(
          record,
          RejectionType.NOT_APPLICABLE,
          "subscription was already correlated or is closing");
      return;
    }

    if (subscription.shouldCloseOnCorrelate()) {
      subscriptionState.remove(subscription);
    }

    final boolean isOccurred =
        catchEventBehavior.occurEventForElement(
            elementInstanceKey,
            subscription.getHandlerNodeId(),
            record.getValue().getPayload(),
            streamWriter);

    if (isOccurred) {
      streamWriter.appendFollowUpEvent(
          record.getKey(), WorkflowInstanceSubscriptionIntent.CORRELATED, subscriptionRecord);
    } else {
      streamWriter.appendRejection(
          record, RejectionType.NOT_APPLICABLE, "activity is not active anymore");
    }
  }

  private boolean sendAcknowledgeCommand() {
    return subscriptionCommandSender.correlateMessageSubscription(
        subscriptionRecord.getSubscriptionPartitionId(),
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageName());
  }
}
