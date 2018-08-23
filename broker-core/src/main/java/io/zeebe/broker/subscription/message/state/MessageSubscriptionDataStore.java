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

  public boolean addSubscription(MessageSubscription subscription) {

    if (getData().getSubscriptions().contains(subscription)) {
      return false;
    }

    getData().getSubscriptions().add(subscription);
    return true;
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

  public List<MessageSubscription> findPendingSubscriptionsWithSentTimeBefore(long sentTime) {
    return getData()
        .getSubscriptions()
        .stream()
        .filter(s -> s.commandSentTime > 0 && s.commandSentTime < sentTime)
        .collect(Collectors.toList());
  }

  public boolean removeSubscription(MessageSubscriptionRecord record) {
    return getData()
        .getSubscriptions()
        .removeIf(
            s ->
                s.getWorkflowInstancePartitionId() == record.getWorkflowInstancePartitionId()
                    && s.getWorkflowInstanceKey() == record.getWorkflowInstanceKey()
                    && s.getActivityInstanceKey() == record.getActivityInstanceKey()
                    && s.getMessageName().equals(bufferAsString(record.getMessageName())));
  }

  public static class MessageSubscriptiopnData {

    private final List<MessageSubscription> subscriptions = new ArrayList<>();

    public List<MessageSubscription> getSubscriptions() {
      return subscriptions;
    }
  }

  public static class MessageSubscription {

    private int workflowInstancePartitionId;
    private long workflowInstanceKey;
    private long activityInstanceKey;
    private String messageName;
    private String correlationKey;

    private byte[] messagePayload;
    private long commandSentTime;

    /* required for json deserialization */
    public MessageSubscription() {}

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

    public long getCommandSentTime() {
      return commandSentTime;
    }

    public void setCommandSentTime(long correlationSentTime) {
      this.commandSentTime = correlationSentTime;
    }

    public byte[] getMessagePayload() {
      return messagePayload;
    }

    public void setMessagePayload(byte[] messagePayload) {
      this.messagePayload = messagePayload;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + (int) (activityInstanceKey ^ (activityInstanceKey >>> 32));
      result = prime * result + ((correlationKey == null) ? 0 : correlationKey.hashCode());
      result = prime * result + ((messageName == null) ? 0 : messageName.hashCode());
      result = prime * result + (int) (workflowInstanceKey ^ (workflowInstanceKey >>> 32));
      result = prime * result + workflowInstancePartitionId;
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final MessageSubscription other = (MessageSubscription) obj;
      if (activityInstanceKey != other.activityInstanceKey) {
        return false;
      }
      if (correlationKey == null) {
        if (other.correlationKey != null) {
          return false;
        }
      } else if (!correlationKey.equals(other.correlationKey)) {
        return false;
      }
      if (messageName == null) {
        if (other.messageName != null) {
          return false;
        }
      } else if (!messageName.equals(other.messageName)) {
        return false;
      }
      if (workflowInstanceKey != other.workflowInstanceKey) {
        return false;
      }
      if (workflowInstancePartitionId != other.workflowInstancePartitionId) {
        return false;
      }
      return true;
    }
  }
}
