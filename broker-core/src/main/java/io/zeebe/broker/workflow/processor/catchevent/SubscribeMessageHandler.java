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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionDataStore;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionDataStore.WorkflowInstanceSubscription;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.model.ExecutableIntermediateMessageCatchEvent;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResults;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

public class SubscribeMessageHandler
    implements BpmnStepHandler<ExecutableIntermediateMessageCatchEvent> {

  private final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();

  private WorkflowInstanceRecord workflowInstance;
  private long activityInstanceKey;
  private ExecutableIntermediateMessageCatchEvent catchEvent;
  private DirectBuffer extractedCorrelationKey;

  private final SubscriptionCommandSender subscriptionCommandSender;
  private final WorkflowInstanceSubscriptionDataStore subscriptionStore;

  public SubscribeMessageHandler(
      SubscriptionCommandSender subscriptionCommandSender,
      WorkflowInstanceSubscriptionDataStore subscriptionStore) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.subscriptionStore = subscriptionStore;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableIntermediateMessageCatchEvent> context) {

    this.workflowInstance = context.getValue();
    this.activityInstanceKey = context.getRecord().getKey();
    this.catchEvent = context.getElement();

    if (subscriptionCommandSender.hasPartitionIds()) {
      onPartitionIdsAvailable(context);

    } else {
      // this async fetching will be removed when the partitions are known on startup
      final ActorFuture<Void> onCompleted = new CompletableActorFuture<>();
      context.getAsyncContext().async(onCompleted);

      context
          .getActor()
          .runOnCompletion(
              subscriptionCommandSender.fetchCreatedTopics(),
              (v, failure) -> {
                if (failure == null) {
                  onPartitionIdsAvailable(context);

                  onCompleted.complete(null);
                } else {
                  onCompleted.completeExceptionally(failure);
                }
              });
    }
  }

  private void onPartitionIdsAvailable(
      BpmnStepContext<ExecutableIntermediateMessageCatchEvent> context) {
    extractedCorrelationKey = extractCorrelationKey();
    context.getSideEffect().accept(this::openMessageSubscription);

    final WorkflowInstanceSubscription subscription =
        new WorkflowInstanceSubscription(
            workflowInstance.getWorkflowInstanceKey(),
            activityInstanceKey,
            bufferAsString(catchEvent.getMessageName()),
            bufferAsString(extractedCorrelationKey));
    subscription.setSentTime(ActorClock.currentTimeMillis());
    subscriptionStore.addSubscription(subscription);
  }

  private boolean openMessageSubscription() {
    return subscriptionCommandSender.openMessageSubscription(
        workflowInstance.getWorkflowInstanceKey(),
        activityInstanceKey,
        catchEvent.getMessageName(),
        extractedCorrelationKey);
  }

  private DirectBuffer extractCorrelationKey() {
    final QueryResults results =
        queryProcessor.process(catchEvent.getCorrelationKey(), workflowInstance.getPayload());
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
