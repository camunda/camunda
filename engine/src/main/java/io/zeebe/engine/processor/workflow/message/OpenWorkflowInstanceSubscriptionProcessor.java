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
import io.zeebe.engine.state.message.WorkflowInstanceSubscription;
import io.zeebe.engine.state.message.WorkflowInstanceSubscriptionState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;

public class OpenWorkflowInstanceSubscriptionProcessor
    implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {

  public static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to open workflow instance subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found";
  public static final String NOT_OPENING_MSG =
      "Expected to open workflow instance subscription with element key '%d' and message name '%s', "
          + "but it is already %s";
  private final WorkflowInstanceSubscriptionState subscriptionState;

  public OpenWorkflowInstanceSubscriptionProcessor(
      final WorkflowInstanceSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void processRecord(
      final TypedRecord<WorkflowInstanceSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final WorkflowInstanceSubscriptionRecord subscriptionRecord = record.getValue();
    final WorkflowInstanceSubscription subscription =
        subscriptionState.getSubscription(
            subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageName());

    if (subscription != null && subscription.isOpening()) {

      subscriptionState.updateToOpenedState(
          subscription, subscription.getSubscriptionPartitionId());

      streamWriter.appendFollowUpEvent(
          record.getKey(), WorkflowInstanceSubscriptionIntent.OPENED, subscriptionRecord);

    } else {
      final String messageName = BufferUtil.bufferAsString(subscriptionRecord.getMessageName());

      if (subscription == null) {
        streamWriter.appendRejection(
            record,
            RejectionType.NOT_FOUND,
            String.format(
                NO_SUBSCRIPTION_FOUND_MESSAGE,
                subscriptionRecord.getElementInstanceKey(),
                messageName));
      } else {
        final String state = subscription.isClosing() ? "closing" : "opened";
        streamWriter.appendRejection(
            record,
            RejectionType.INVALID_STATE,
            String.format(
                NOT_OPENING_MSG, subscriptionRecord.getElementInstanceKey(), messageName, state));
      }
    }
  }
}
