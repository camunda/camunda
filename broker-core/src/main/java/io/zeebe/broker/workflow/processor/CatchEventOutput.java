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
package io.zeebe.broker.workflow.processor;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.broker.workflow.model.element.ExecutableCatchEvent;
import io.zeebe.broker.workflow.model.element.ExecutableMessage;
import io.zeebe.broker.workflow.processor.boundary.BoundaryEventHelper;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.EventTrigger;
import io.zeebe.broker.workflow.state.TimerInstance;
import io.zeebe.broker.workflow.state.WorkflowInstanceSubscription;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResults;
import io.zeebe.protocol.impl.record.value.incident.ErrorType;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import java.util.List;
import org.agrona.DirectBuffer;

/** Split into multiple files once we have a reasonable amount of event triggers. */
public class CatchEventOutput {
  private final BoundaryEventHelper boundaryEventHelper = new BoundaryEventHelper();
  private final ZeebeState state;
  private final SubscriptionCommandSender subscriptionCommandSender;

  public CatchEventOutput(ZeebeState state, SubscriptionCommandSender subscriptionCommandSender) {
    this.state = state;
    this.subscriptionCommandSender = subscriptionCommandSender;
  }

  public void unsubscribeFromCatchEvents(long elementInstanceKey, BpmnStepContext<?> context) {
    // at the moment, the way the state is handled we don't need specific event information to
    // unsubscribe from an event trigger, but once messages are supported it will be necessary.
    unsubscribeFromTimerEvents(elementInstanceKey, context.getOutput().getStreamWriter());
    unsubscribeFromMessageEvents(elementInstanceKey, context);
  }

  public void subscribeToCatchEvents(
      BpmnStepContext<?> context, final List<? extends ExecutableCatchEvent> events) {
    for (final ExecutableCatchEvent event : events) {
      if (event.isTimer()) {
        subscribeToTimerEvent(
            context.getRecord().getKey(), event, context.getOutput().getStreamWriter());
      } else if (event.isMessage()) {
        subscribeToMessageEvent(context, event);
      }
    }
  }

  public void triggerBoundaryEventFromInterruptedElement(
      ElementInstance element, TypedStreamWriter writer) {
    assert element.isInterrupted() : "element must have been interrupted";

    final EventTrigger interruptingEventTrigger = element.getInterruptingEventTrigger();
    boundaryEventHelper.triggerCatchEvent(
        element.getValue(),
        interruptingEventTrigger.getHandlerNodeId(),
        interruptingEventTrigger.getPayload(),
        writer);
  }

  // TIMERS
  private final TimerRecord timerRecord = new TimerRecord();

  public void subscribeToTimerEvent(
      long elementInstanceKey, ExecutableCatchEvent event, TypedStreamWriter writer) {
    final Duration duration = event.getDuration();
    final long dueDate = ActorClock.currentTimeMillis() + duration.toMillis();

    timerRecord
        .setElementInstanceKey(elementInstanceKey)
        .setDueDate(dueDate)
        .setHandlerNodeId(event.getId());
    writer.appendNewCommand(TimerIntent.CREATE, timerRecord);
  }

  public void unsubscribeFromTimerEvent(TimerInstance timer, TypedStreamWriter writer) {
    timerRecord
        .setElementInstanceKey(timer.getElementInstanceKey())
        .setDueDate(timer.getDueDate())
        .setHandlerNodeId(timer.getHandlerNodeId());

    writer.appendFollowUpCommand(timer.getKey(), TimerIntent.CANCEL, timerRecord);
  }

  public void unsubscribeFromTimerEvents(long elementInstanceKey, TypedStreamWriter writer) {
    state
        .getWorkflowState()
        .getTimerState()
        .forEachTimerForElementInstance(
            elementInstanceKey, t -> unsubscribeFromTimerEvent(t, writer));
  }

  // MESSAGES
  private final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();
  private WorkflowInstanceSubscription subscription = new WorkflowInstanceSubscription();

  public void subscribeToMessageEvent(BpmnStepContext<?> context, ExecutableCatchEvent handler) {
    final ExecutableMessage message = handler.getMessage();
    final DirectBuffer extractedKey = extractCorrelationKey(context, message);

    if (extractedKey == null) {
      return;
    }

    final long workflowInstanceKey = context.getValue().getWorkflowInstanceKey();
    final long elementInstanceKey = context.getRecord().getKey();
    final DirectBuffer messageName = cloneBuffer(message.getMessageName());
    final DirectBuffer correlationKey = cloneBuffer(extractedKey);

    subscription.setMessageName(messageName);
    subscription.setElementInstanceKey(elementInstanceKey);
    subscription.setCommandSentTime(ActorClock.currentTimeMillis());
    subscription.setWorkflowInstanceKey(workflowInstanceKey);
    subscription.setCorrelationKey(correlationKey);
    subscription.setHandlerNodeId(handler.getId());
    state.getWorkflowInstanceSubscriptionState().put(subscription);

    context
        .getSideEffect()
        .add(
            () ->
                sendOpenMessageSubscription(
                    workflowInstanceKey, elementInstanceKey, messageName, correlationKey));
  }

  public void unsubscribeFromMessageEvents(long elementInstanceKey, BpmnStepContext<?> context) {
    state
        .getWorkflowInstanceSubscriptionState()
        .visitElementSubscriptions(
            elementInstanceKey, sub -> unsubscribeFromMessageEvent(context, sub));
  }

  private boolean unsubscribeFromMessageEvent(
      BpmnStepContext<?> context, WorkflowInstanceSubscription subscription) {
    final DirectBuffer messageName = cloneBuffer(subscription.getMessageName());
    final int subscriptionPartitionId = subscription.getSubscriptionPartitionId();
    final long workflowInstanceKey = subscription.getWorkflowInstanceKey();
    final long elementInstanceKey = subscription.getElementInstanceKey();

    subscription.setClosing();
    state
        .getWorkflowInstanceSubscriptionState()
        .updateToClosingState(subscription, ActorClock.currentTimeMillis());

    context
        .getSideEffect()
        .add(
            () ->
                sendCloseMessageSubscriptionCommand(
                    subscriptionPartitionId, workflowInstanceKey, elementInstanceKey, messageName));

    return true;
  }

  private DirectBuffer extractCorrelationKey(
      BpmnStepContext<?> context, ExecutableMessage message) {
    final QueryResults results =
        queryProcessor.process(message.getCorrelationKey(), context.getValue().getPayload());
    final String errorMessage;

    if (results.size() == 1) {
      final QueryResult result = results.getSingleResult();
      if (result.isString()) {
        return result.getString();
      }

      if (result.isLong()) {
        return result.getLongAsBuffer();
      }

      errorMessage = "the value must be either a string or a number";
    } else if (results.size() > 1) {
      errorMessage = "multiple values found";
    } else {
      errorMessage = "no value found";
    }

    final String expression = bufferAsString(message.getCorrelationKey().getExpression());
    final String failureMessage =
        String.format(
            "Failed to extract the correlation-key by '%s': %s", expression, errorMessage);

    context.raiseIncident(ErrorType.EXTRACT_VALUE_ERROR, failureMessage);
    return null;
  }

  private boolean sendCloseMessageSubscriptionCommand(
      int subscriptionPartitionId,
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName) {
    return subscriptionCommandSender.closeMessageSubscription(
        subscriptionPartitionId, workflowInstanceKey, elementInstanceKey, messageName);
  }

  private boolean sendOpenMessageSubscription(
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey) {
    return subscriptionCommandSender.openMessageSubscription(
        workflowInstanceKey, elementInstanceKey, messageName, correlationKey);
  }
}
