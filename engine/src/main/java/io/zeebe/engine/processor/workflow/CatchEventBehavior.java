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
package io.zeebe.engine.processor.workflow;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventSupplier;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processor.workflow.message.MessageCorrelationKeyContext;
import io.zeebe.engine.processor.workflow.message.MessageCorrelationKeyContext.VariablesDocumentSupplier;
import io.zeebe.engine.processor.workflow.message.MessageCorrelationKeyException;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.engine.state.message.WorkflowInstanceSubscription;
import io.zeebe.model.bpmn.util.time.Timer;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResults;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public class CatchEventBehavior {

  private final ZeebeState state;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final int partitionsCount;

  private final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();
  private final WorkflowInstanceSubscription subscription = new WorkflowInstanceSubscription();
  private final TimerRecord timerRecord = new TimerRecord();
  private final Map<DirectBuffer, DirectBuffer> extractedCorrelationKeys = new HashMap<>();

  public CatchEventBehavior(
      ZeebeState state, SubscriptionCommandSender subscriptionCommandSender, int partitionsCount) {
    this.state = state;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.partitionsCount = partitionsCount;
  }

  public void unsubscribeFromEvents(long elementInstanceKey, BpmnStepContext<?> context) {
    unsubscribeFromTimerEvents(elementInstanceKey, context.getOutput().getStreamWriter());
    unsubscribeFromMessageEvents(elementInstanceKey, context);
    context.getStateDb().getEventScopeInstanceState().deleteInstance(elementInstanceKey);
  }

  public void subscribeToEvents(
      BpmnStepContext<?> context, final ExecutableCatchEventSupplier supplier)
      throws MessageCorrelationKeyException {
    final List<ExecutableCatchEvent> events = supplier.getEvents();
    final VariablesDocumentSupplier variablesSupplier =
        context.getElementInstanceState().getVariablesState()::getVariablesAsDocument;
    final MessageCorrelationKeyContext elementContext =
        new MessageCorrelationKeyContext(variablesSupplier, context.getRecord().getKey());
    final MessageCorrelationKeyContext scopeContext =
        new MessageCorrelationKeyContext(
            variablesSupplier, context.getRecord().getValue().getFlowScopeKey());

    // collect all message correlation keys from their respective variables, as this might fail and
    // we might need to raise an incident
    final Map<DirectBuffer, DirectBuffer> extractedCorrelationKeys =
        extractMessageCorrelationKeys(events, elementContext, scopeContext);

    // if all subscriptions are valid then open the subscriptions
    for (final ExecutableCatchEvent event : events) {
      if (event.isTimer()) {
        subscribeToTimerEvent(
            context.getRecord().getKey(),
            context.getRecord().getValue().getWorkflowInstanceKey(),
            context.getRecord().getValue().getWorkflowKey(),
            event.getId(),
            event.getTimer(),
            context.getOutput().getStreamWriter());
      } else if (event.isMessage()) {
        subscribeToMessageEvent(context, event, extractedCorrelationKeys.get(event.getId()));
      }
    }

    context
        .getStateDb()
        .getEventScopeInstanceState()
        .createIfNotExists(context.getRecord().getKey(), supplier.getInterruptingElementIds());
  }

  public void subscribeToTimerEvent(
      long elementInstanceKey,
      long workflowInstanceKey,
      long workflowKey,
      DirectBuffer handlerNodeId,
      Timer timer,
      TypedStreamWriter writer) {
    timerRecord.reset();
    timerRecord
        .setRepetitions(timer.getRepetitions())
        .setDueDate(timer.getDueDate(ActorClock.currentTimeMillis()))
        .setElementInstanceKey(elementInstanceKey)
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setHandlerNodeId(handlerNodeId)
        .setWorkflowKey(workflowKey);
    writer.appendNewCommand(TimerIntent.CREATE, timerRecord);
  }

  private void unsubscribeFromTimerEvents(long elementInstanceKey, TypedStreamWriter writer) {
    state
        .getWorkflowState()
        .getTimerState()
        .forEachTimerForElementInstance(
            elementInstanceKey, t -> unsubscribeFromTimerEvent(t, writer));
  }

  public void unsubscribeFromTimerEvent(TimerInstance timer, TypedStreamWriter writer) {
    timerRecord.reset();
    timerRecord
        .setElementInstanceKey(timer.getElementInstanceKey())
        .setWorkflowInstanceKey(timer.getWorkflowInstanceKey())
        .setDueDate(timer.getDueDate())
        .setRepetitions(timer.getRepetitions())
        .setHandlerNodeId(timer.getHandlerNodeId())
        .setWorkflowKey(timer.getWorkflowKey());

    writer.appendFollowUpCommand(timer.getKey(), TimerIntent.CANCEL, timerRecord);
  }

  private void subscribeToMessageEvent(
      BpmnStepContext<?> context, ExecutableCatchEvent handler, DirectBuffer extractedKey) {
    final ExecutableMessage message = handler.getMessage();

    final long workflowInstanceKey = context.getValue().getWorkflowInstanceKey();
    final long elementInstanceKey = context.getRecord().getKey();
    final DirectBuffer messageName = cloneBuffer(message.getMessageName());
    final DirectBuffer correlationKey = extractedKey;
    final boolean closeOnCorrelate = handler.shouldCloseMessageSubscriptionOnCorrelate();
    final int subscriptionPartitionId =
        SubscriptionUtil.getSubscriptionPartitionId(correlationKey, partitionsCount);

    subscription.setSubscriptionPartitionId(subscriptionPartitionId);
    subscription.setMessageName(messageName);
    subscription.setElementInstanceKey(elementInstanceKey);
    subscription.setCommandSentTime(ActorClock.currentTimeMillis());
    subscription.setWorkflowInstanceKey(workflowInstanceKey);
    subscription.setCorrelationKey(correlationKey);
    subscription.setHandlerNodeId(handler.getId());
    subscription.setCloseOnCorrelate(closeOnCorrelate);
    state.getWorkflowInstanceSubscriptionState().put(subscription);

    context
        .getSideEffect()
        .add(
            () ->
                sendOpenMessageSubscription(
                    subscriptionPartitionId,
                    workflowInstanceKey,
                    elementInstanceKey,
                    messageName,
                    correlationKey,
                    closeOnCorrelate));
  }

  private void unsubscribeFromMessageEvents(long elementInstanceKey, BpmnStepContext<?> context) {
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
      ExecutableMessage message, MessageCorrelationKeyContext context) {
    final QueryResults results =
        queryProcessor.process(message.getCorrelationKey(), context.getVariablesAsDocument());
    final String errorMessage;

    if (results.size() == 1) {
      final QueryResult result = results.getSingleResult();
      if (result.isString()) {
        return result.getString();
      }

      if (result.isLong()) {
        return result.getLongAsString();
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
    throw new MessageCorrelationKeyException(context, failureMessage);
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
      int subscriptionPartitionId,
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      boolean closeOnCorrelate) {
    return subscriptionCommandSender.openMessageSubscription(
        subscriptionPartitionId,
        workflowInstanceKey,
        elementInstanceKey,
        messageName,
        correlationKey,
        closeOnCorrelate);
  }

  private Map<DirectBuffer, DirectBuffer> extractMessageCorrelationKeys(
      List<ExecutableCatchEvent> events,
      MessageCorrelationKeyContext elementContext,
      MessageCorrelationKeyContext scopeContext) {
    extractedCorrelationKeys.clear();

    for (ExecutableCatchEvent event : events) {
      if (event.isMessage()) {
        final MessageCorrelationKeyContext context =
            event.getElementType() == BpmnElementType.BOUNDARY_EVENT
                ? scopeContext
                : elementContext;
        final DirectBuffer correlationKey = extractCorrelationKey(event.getMessage(), context);

        extractedCorrelationKeys.put(event.getId(), cloneBuffer(correlationKey));
      }
    }

    return extractedCorrelationKeys;
  }
}
