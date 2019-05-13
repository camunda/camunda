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

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamProcessor;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.message.WorkflowInstanceSubscription;
import io.zeebe.engine.state.message.WorkflowInstanceSubscriptionState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class CorrelateWorkflowInstanceSubscription
    implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {

  private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);
  private static final String NO_EVENT_OCCURRED_MESSAGE =
      "Expected to correlate a workflow instance subscription with element key '%d' and message name '%s', "
          + "but the subscription is not active anymore";
  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to correlate workflow instance subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found";
  private static final String ALREADY_CLOSING_MESSAGE =
      "Expected to correlate workflow instance subscription with element key '%d' and message name '%s', "
          + "but it is already closing";

  private final WorkflowInstanceSubscriptionState subscriptionState;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final WorkflowState workflowState;
  private final KeyGenerator keyGenerator;

  private WorkflowInstanceSubscriptionRecord subscriptionRecord;
  private DirectBuffer correlationKey;

  public CorrelateWorkflowInstanceSubscription(
      final WorkflowInstanceSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final ZeebeState zeebeState) {
    this.subscriptionState = subscriptionState;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.workflowState = zeebeState.getWorkflowState();
    this.keyGenerator = zeebeState.getKeyGenerator();
  }

  @Override
  public void onOpen(final TypedStreamProcessor streamProcessor) {
    final ActorControl actor = streamProcessor.getActor();

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

    if (subscription == null || subscription.isClosing()) {
      RejectionType type = RejectionType.NOT_FOUND;
      String reason = NO_SUBSCRIPTION_FOUND_MESSAGE;

      if (subscription != null) { // closing
        type = RejectionType.INVALID_STATE;
        reason = ALREADY_CLOSING_MESSAGE;
        correlationKey = subscription.getCorrelationKey();
      } else {
        correlationKey = null;
      }

      sideEffect.accept(this::sendRejectionCommand);
      streamWriter.appendRejection(
          record,
          type,
          String.format(
              reason,
              subscriptionRecord.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscriptionRecord.getMessageName())));
      return;
    }

    if (subscription.shouldCloseOnCorrelate()) {
      subscriptionState.remove(subscription);
    }

    final ElementInstance elementInstance =
        workflowState.getElementInstanceState().getInstance(subscription.getElementInstanceKey());
    final long eventKey = keyGenerator.nextKey();
    final boolean isOccurred =
        workflowState
            .getEventScopeInstanceState()
            .triggerEvent(
                subscriptionRecord.getElementInstanceKey(),
                eventKey,
                subscription.getHandlerNodeId(),
                record.getValue().getVariables());

    if (isOccurred) {
      sideEffect.accept(this::sendAcknowledgeCommand);

      streamWriter.appendFollowUpEvent(
          record.getKey(), WorkflowInstanceSubscriptionIntent.CORRELATED, subscriptionRecord);
      streamWriter.appendFollowUpEvent(
          subscriptionRecord.getElementInstanceKey(),
          WorkflowInstanceIntent.EVENT_OCCURRED,
          elementInstance.getValue());
    } else {
      correlationKey = subscription.getCorrelationKey();
      sideEffect.accept(this::sendRejectionCommand);

      streamWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(
              NO_EVENT_OCCURRED_MESSAGE,
              subscriptionRecord.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscriptionRecord.getMessageName())));
    }
  }

  private boolean sendAcknowledgeCommand() {
    return subscriptionCommandSender.correlateMessageSubscription(
        subscriptionRecord.getSubscriptionPartitionId(),
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageName());
  }

  private boolean sendRejectionCommand() {
    return subscriptionCommandSender.rejectCorrelateMessageSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageKey(),
        subscriptionRecord.getMessageName(),
        correlationKey);
  }
}
