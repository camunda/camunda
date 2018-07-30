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
package io.zeebe.broker.subscription.message.state;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.logstreams.processor.JsonSnapshotSupport;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionDataStore.MessageSubscriptiopnData;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MessageSubscriptionDataStore extends JsonSnapshotSupport<MessageSubscriptiopnData> {

  public MessageSubscriptionDataStore() {
    super(MessageSubscriptiopnData.class);
  }

  public void addSubscription(MessageSubscriptionRecord record) {
    final MessageSubscription subscription =
        new MessageSubscription(
            record.getWorkflowInstancePartitionId(),
            record.getWorkflowInstanceKey(),
            record.getActivityInstanceKey(),
            bufferAsString(record.getMessageName()),
            bufferAsString(record.getCorrelationKey()));

    getData().getSubscriptions().add(subscription);
  }

  public List<MessageSubscription> findSubscriptions(String messageName, String correlationKey) {
    return getData()
        .getSubscriptions()
        .stream()
        .filter(
            m ->
                m.getMessageName().equals(messageName)
                    && m.getCorrelationKey().equals(correlationKey))
        .collect(Collectors.toList());
  }

  public static class MessageSubscriptiopnData {

    private final List<MessageSubscription> subscriptions = new ArrayList<>();

    public List<MessageSubscription> getSubscriptions() {
      return subscriptions;
    }
  }

  public static class MessageSubscription {

    private final int workflowInstancePartitionId;
    private final long workflowInstanceKey;
    private final long activityInstanceKey;
    private final String messageName;
    private final String correlationKey;

    public MessageSubscription(
        int workflowInstancePartitionId,
        long workflowInstanceKey,
        long activityInstanceKey,
        String messageName,
        String correlationKey) {
      this.workflowInstancePartitionId = workflowInstancePartitionId;
      this.workflowInstanceKey = workflowInstanceKey;
      this.activityInstanceKey = activityInstanceKey;
      this.messageName = messageName;
      this.correlationKey = correlationKey;
    }

    public int getWorkflowInstancePartitionId() {
      return workflowInstancePartitionId;
    }

    public long getWorkflowInstanceKey() {
      return workflowInstanceKey;
    }

    public long getActivityInstanceKey() {
      return activityInstanceKey;
    }

    public String getMessageName() {
      return messageName;
    }

    public String getCorrelationKey() {
      return correlationKey;
    }
  }
}
