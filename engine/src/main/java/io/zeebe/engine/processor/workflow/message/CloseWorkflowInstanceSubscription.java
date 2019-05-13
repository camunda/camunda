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

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.message.WorkflowInstanceSubscriptionState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;

public final class CloseWorkflowInstanceSubscription
    implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {
  public static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to close workflow instance subscription for element with key '%d' and message name '%s', "
          + "but no such subscription was found";

  private final WorkflowInstanceSubscriptionState subscriptionState;

  public CloseWorkflowInstanceSubscription(
      final WorkflowInstanceSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void processRecord(
      final TypedRecord<WorkflowInstanceSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final WorkflowInstanceSubscriptionRecord subscription = record.getValue();

    final boolean removed =
        subscriptionState.remove(
            subscription.getElementInstanceKey(), subscription.getMessageName());
    if (removed) {
      streamWriter.appendFollowUpEvent(
          record.getKey(), WorkflowInstanceSubscriptionIntent.CLOSED, subscription);

    } else {
      streamWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(
              NO_SUBSCRIPTION_FOUND_MESSAGE,
              subscription.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscription.getMessageName())));
    }
  }
}
