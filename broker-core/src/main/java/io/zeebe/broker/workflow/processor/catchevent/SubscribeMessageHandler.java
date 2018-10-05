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
package io.zeebe.broker.workflow.processor.catchevent;

import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.workflow.model.element.ExecutableMessage;
import io.zeebe.broker.workflow.model.element.ExecutableMessageCatchElement;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.broker.workflow.state.WorkflowSubscription;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResults;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.util.sched.clock.ActorClock;
import org.agrona.DirectBuffer;

public class SubscribeMessageHandler implements BpmnStepHandler<ExecutableMessageCatchElement> {

  private final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();
  private final WorkflowState workflowState;

  private WorkflowInstanceRecord workflowInstance;
  private long activityInstanceKey;
  private ExecutableMessage message;
  private DirectBuffer extractedCorrelationKey;

  private final SubscriptionCommandSender subscriptionCommandSender;

  public SubscribeMessageHandler(
      final SubscriptionCommandSender subscriptionCommandSender,
      final WorkflowState workflowState) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.workflowState = workflowState;
  }

  @Override
  public void handle(final BpmnStepContext<ExecutableMessageCatchElement> context) {

    this.workflowInstance = context.getValue();
    this.activityInstanceKey = context.getRecord().getKey();
    this.message = context.getElement().getMessage();

    extractedCorrelationKey = extractCorrelationKey();
    context.getSideEffect().accept(this::openMessageSubscription);

    final WorkflowSubscription subscription =
        new WorkflowSubscription(
            workflowInstance.getWorkflowInstanceKey(),
            activityInstanceKey,
            cloneBuffer(message.getMessageName()),
            cloneBuffer(extractedCorrelationKey));
    subscription.setCommandSentTime(ActorClock.currentTimeMillis());
    workflowState.put(subscription);
  }

  private boolean openMessageSubscription() {
    return subscriptionCommandSender.openMessageSubscription(
        workflowInstance.getWorkflowInstanceKey(),
        activityInstanceKey,
        message.getMessageName(),
        extractedCorrelationKey);
  }

  private DirectBuffer extractCorrelationKey() {
    final QueryResults results =
        queryProcessor.process(message.getCorrelationKey(), workflowInstance.getPayload());
    if (results.size() == 1) {
      final QueryResult result = results.getSingleResult();

      if (result.isString()) {
        return result.getString();

      } else if (result.isLong()) {
        return result.getLongAsBuffer();

      } else {
        // the exception will be replaces by an incident - #1018
        throw new RuntimeException("Failed to extract correlation-key: wrong type");
      }
    } else {
      // the exception will be replaces by an incident - #1018
      throw new RuntimeException("Failed to extract correlation-key: no result");
    }
  }
}
