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

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.broker.workflow.model.element.ExecutableIntermediateCatchElement;
import io.zeebe.broker.workflow.model.element.ExecutableMessage;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.TimerInstance;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.broker.workflow.state.WorkflowSubscription;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResults;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import org.agrona.DirectBuffer;

/**
 * FIXME: find a better name
 *
 * <p>Split into multiple files once we have a reasonable amount of event triggers.
 */
public class CatchEventOutput {
  private final WorkflowState state;
  private final SubscriptionCommandSender subscriptionCommandSender;

  public CatchEventOutput(
      WorkflowState state, SubscriptionCommandSender subscriptionCommandSender) {
    this.state = state;
    this.subscriptionCommandSender = subscriptionCommandSender;
  }

  // TIMERS
  private final TimerRecord timerRecord = new TimerRecord();

  public void subscribeToTimerEvent(
      ElementInstance element,
      ExecutableIntermediateCatchElement event,
      TypedBatchWriter batchWriter) {
    final Duration duration = event.getDuration();
    final long dueDate = ActorClock.currentTimeMillis() + duration.toMillis();

    timerRecord
        .setElementInstanceKey(element.getKey())
        .setDueDate(dueDate)
        .setHandlerNodeId(event.getId());
    batchWriter.addNewCommand(TimerIntent.CREATE, timerRecord);
  }

  public void unsubscribeFromTimerEvent(TimerInstance timer, TypedBatchWriter batchWriter) {
    timerRecord
        .setElementInstanceKey(timer.getElementInstanceKey())
        .setDueDate(timer.getDueDate())
        .setHandlerNodeId(timer.getHandlerNodeId());

    batchWriter.addFollowUpCommand(timer.getKey(), TimerIntent.CANCEL, timerRecord);
  }

  public void unsubscribeFromTimerEvents(long activityKey, TypedBatchWriter batchWriter) {
    state
        .getTimerState()
        .forEachTimerForActivity(activityKey, t -> unsubscribeFromTimerEvent(t, batchWriter));
  }

  // MESSAGES
  private final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();
  private WorkflowSubscription subscription;

  public void subscribeToMessageEvent(BpmnStepContext<?> context, ExecutableMessage message) {
    final DirectBuffer extractedKey = extractCorrelationKey(context, message);

    if (extractedKey == null) {
      return;
    }

    subscription =
        new WorkflowSubscription(
            context.getValue().getWorkflowInstanceKey(),
            context.getElementInstance().getKey(),
            cloneBuffer(message.getMessageName()),
            cloneBuffer(extractedKey));
    subscription.setCommandSentTime(ActorClock.currentTimeMillis());
    state.put(subscription);
    context.getSideEffect().accept(this::sendOpenMessageSubscription);
  }

  public void unsubscribeFromMessageEvent(BpmnStepContext<?> context) {
    subscription =
        state.findSubscription(
            context.getValue().getWorkflowInstanceKey(), context.getElementInstance().getKey());

    if (subscription != null) {
      subscription.setClosing();
      state.updateCommandSendTime(subscription);
      context.getSideEffect().accept(this::sendCloseMessageSubscriptionCommand);
    }
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

  private boolean sendCloseMessageSubscriptionCommand() {
    return subscriptionCommandSender.closeMessageSubscription(
        subscription.getSubscriptionPartitionId(),
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey());
  }

  private boolean sendOpenMessageSubscription() {
    return subscriptionCommandSender.openMessageSubscription(
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getMessageName(),
        subscription.getCorrelationKey());
  }
}
