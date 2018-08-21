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
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionDataStore.WorkflowInstanceSubscriptionData;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WorkflowInstanceSubscriptionDataStore
    extends JsonSnapshotSupport<WorkflowInstanceSubscriptionData> {

  public WorkflowInstanceSubscriptionDataStore() {
    super(WorkflowInstanceSubscriptionData.class);
  }

  public void addSubscription(WorkflowInstanceSubscription subscription) {
    getData().getSubscriptions().add(subscription);
  }

  public List<WorkflowInstanceSubscription> findSubscriptionWithSentTimeBefore(long sentTime) {
    return getData()
        .getSubscriptions()
        .stream()
        .filter(s -> s.sentTime < sentTime)
        .collect(Collectors.toList());
  }

  public boolean removeSubscription(WorkflowInstanceSubscriptionRecord record) {
    return getData()
        .getSubscriptions()
        .removeIf(
            s ->
                s.getWorkflowInstanceKey() == record.getWorkflowInstanceKey()
                    && s.getActivityInstanceKey() == record.getActivityInstanceKey()
                    && s.getMessageName().equals(bufferAsString(record.getMessageName())));
  }

  public static class WorkflowInstanceSubscriptionData {

    private final List<WorkflowInstanceSubscription> subscriptions = new ArrayList<>();

    public List<WorkflowInstanceSubscription> getSubscriptions() {
      return subscriptions;
    }
  }

  public static class WorkflowInstanceSubscription {

    private long workflowInstanceKey;
    private long activityInstanceKey;
    private String messageName;
    private String correlationKey;

    private long sentTime;

    public WorkflowInstanceSubscription() {
      // required for JSON deserialization
    }

    public WorkflowInstanceSubscription(
        long workflowInstanceKey,
        long activityInstanceKey,
        String messageName,
        String correlationKey) {
      this.workflowInstanceKey = workflowInstanceKey;
      this.activityInstanceKey = activityInstanceKey;
      this.messageName = messageName;
      this.correlationKey = correlationKey;
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

    public long getSentTime() {
      return sentTime;
    }

    public void setSentTime(long sentTime) {
      this.sentTime = sentTime;
    }
  }
}
