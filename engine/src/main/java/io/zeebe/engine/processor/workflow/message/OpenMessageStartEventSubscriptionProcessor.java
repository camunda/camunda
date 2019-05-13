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
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.message.MessageStartEventSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import java.util.Collections;

public class OpenMessageStartEventSubscriptionProcessor
    implements TypedRecordProcessor<MessageStartEventSubscriptionRecord> {

  private final MessageStartEventSubscriptionState subscriptionState;
  private final WorkflowState workflowState;

  public OpenMessageStartEventSubscriptionProcessor(
      MessageStartEventSubscriptionState subscriptionState, WorkflowState workflowState) {
    this.subscriptionState = subscriptionState;
    this.workflowState = workflowState;
  }

  @Override
  public void processRecord(
      TypedRecord<MessageStartEventSubscriptionRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {

    final MessageStartEventSubscriptionRecord subscription = record.getValue();
    subscriptionState.put(subscription);

    workflowState
        .getEventScopeInstanceState()
        .createIfNotExists(subscription.getWorkflowKey(), Collections.emptyList());

    streamWriter.appendFollowUpEvent(
        record.getKey(), MessageStartEventSubscriptionIntent.OPENED, subscription);
  }
}
