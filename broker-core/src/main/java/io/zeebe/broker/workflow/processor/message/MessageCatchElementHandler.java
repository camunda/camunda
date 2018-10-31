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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionState;
import io.zeebe.broker.workflow.model.element.ExecutableMessage;
import io.zeebe.broker.workflow.model.element.ExecutableMessageCatchElement;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.state.WorkflowInstanceSubscription;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResults;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.util.sched.clock.ActorClock;
import org.agrona.DirectBuffer;

public class MessageCatchElementHandler implements BpmnStepHandler<ExecutableMessageCatchElement> {

  private final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();
  private final WorkflowInstanceSubscriptionState subscriptionState;

  private WorkflowInstanceRecord workflowInstance;
  private long elementInstanceKey;
  private ExecutableMessage message;
  private DirectBuffer extractedCorrelationKey;

  private final SubscriptionCommandSender subscriptionCommandSender;

  public MessageCatchElementHandler(
      final SubscriptionCommandSender subscriptionCommandSender,
      final WorkflowInstanceSubscriptionState subscriptionState) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void handle(final BpmnStepContext<ExecutableMessageCatchElement> context) {

    this.workflowInstance = context.getValue();
    this.elementInstanceKey = context.getRecord().getKey();
    this.message = context.getElement().getMessage();

    extractedCorrelationKey = extractCorrelationKey(context);

    if (extractedCorrelationKey != null) {
      context.getSideEffect().accept(this::openMessageSubscription);

      final WorkflowInstanceSubscription subscription =
          new WorkflowInstanceSubscription(
              workflowInstance.getWorkflowInstanceKey(),
              elementInstanceKey,
              message.getMessageName(),
              extractedCorrelationKey,
              ActorClock.currentTimeMillis());
      subscriptionState.put(subscription);
    }
  }

  private boolean openMessageSubscription() {
    return subscriptionCommandSender.openMessageSubscription(
        workflowInstance.getWorkflowInstanceKey(),
        elementInstanceKey,
        message.getMessageName(),
        extractedCorrelationKey);
  }

  private DirectBuffer extractCorrelationKey(
      BpmnStepContext<ExecutableMessageCatchElement> context) {
    final QueryResults results =
        queryProcessor.process(message.getCorrelationKey(), workflowInstance.getPayload());
    if (results.size() == 1) {
      final QueryResult result = results.getSingleResult();

      if (result.isString()) {
        return result.getString();

      } else if (result.isLong()) {
        return result.getLongAsBuffer();

      } else {
        raiseIncident(context, "the value must be either a string or a number");
      }
    } else {
      raiseIncident(context, "no value found");
    }
    return null;
  }

  private void raiseIncident(
      BpmnStepContext<ExecutableMessageCatchElement> context, String failure) {
    final String expression = bufferAsString(message.getCorrelationKey().getExpression());
    final String failureMessage =
        String.format("Failed to extract the correlation-key by '%s': %s", expression, failure);

    context.raiseIncident(ErrorType.EXTRACT_VALUE_ERROR, failureMessage);
  }
}
