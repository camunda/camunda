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
package io.zeebe.engine.state.message;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import org.agrona.DirectBuffer;

public class WorkflowInstanceSubscriptionState {

  private final DbContext dbContext;

  // (elementInstanceKey, messageName) => WorkflowInstanceSubscription
  private final DbLong elementInstanceKey;
  private final DbString messageName;
  private final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName;
  private final WorkflowInstanceSubscription workflowInstanceSubscription;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, WorkflowInstanceSubscription>
      subscriptionColumnFamily;

  // (sentTime, elementInstanceKey, messageName) => \0
  private final DbLong sentTime;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>> sentTimeCompositeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>, DbNil>
      sentTimeColumnFamily;

  public WorkflowInstanceSubscriptionState(ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    this.dbContext = dbContext;

    elementInstanceKey = new DbLong();
    messageName = new DbString();
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, messageName);
    workflowInstanceSubscription = new WorkflowInstanceSubscription();

    subscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.WORKFLOW_SUBSCRIPTION_BY_KEY,
            dbContext,
            elementKeyAndMessageName,
            workflowInstanceSubscription);

    sentTime = new DbLong();
    sentTimeCompositeKey = new DbCompositeKey<>(sentTime, elementKeyAndMessageName);
    sentTimeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.WORKFLOW_SUBSCRIPTION_BY_SENT_TIME,
            dbContext,
            sentTimeCompositeKey,
            DbNil.INSTANCE);
  }

  public void put(final WorkflowInstanceSubscription subscription) {
    wrapSubscriptionKeys(subscription.getElementInstanceKey(), subscription.getMessageName());

    subscriptionColumnFamily.put(elementKeyAndMessageName, subscription);

    sentTime.wrapLong(subscription.getCommandSentTime());
    sentTimeColumnFamily.put(sentTimeCompositeKey, DbNil.INSTANCE);
  }

  public WorkflowInstanceSubscription getSubscription(
      long elementInstanceKey, DirectBuffer messageName) {
    wrapSubscriptionKeys(elementInstanceKey, messageName);

    return subscriptionColumnFamily.get(elementKeyAndMessageName);
  }

  public void visitElementSubscriptions(
      long elementInstanceKey, WorkflowInstanceSubscriptionVisitor visitor) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);

    subscriptionColumnFamily.whileEqualPrefix(
        this.elementInstanceKey,
        (compositeKey, subscription) -> {
          visitor.visit(subscription);
        });
  }

  public void visitSubscriptionBefore(
      final long deadline, WorkflowInstanceSubscriptionVisitor visitor) {

    sentTimeColumnFamily.whileTrue(
        (compositeKey, nil) -> {
          final long sentTime = compositeKey.getFirst().getValue();
          if (sentTime < deadline) {
            final WorkflowInstanceSubscription workflowInstanceSubscription =
                subscriptionColumnFamily.get(compositeKey.getSecond());

            return visitor.visit(workflowInstanceSubscription);
          }
          return false;
        });
  }

  public void updateToOpenedState(
      final WorkflowInstanceSubscription subscription, int subscriptionPartitionId) {
    subscription.setOpened();
    subscription.setSubscriptionPartitionId(subscriptionPartitionId);
    updateSentTime(subscription, 0);
  }

  public void updateToClosingState(final WorkflowInstanceSubscription subscription, long sentTime) {
    subscription.setClosing();
    updateSentTime(subscription, sentTime);
  }

  public void updateSentTimeInTransaction(
      final WorkflowInstanceSubscription subscription, long sentTime) {
    dbContext.runInTransaction(() -> updateSentTime(subscription, sentTime));
  }

  public void updateSentTime(final WorkflowInstanceSubscription subscription, long sentTime) {
    wrapSubscriptionKeys(subscription.getElementInstanceKey(), subscription.getMessageName());

    if (subscription.getCommandSentTime() > 0) {
      this.sentTime.wrapLong(subscription.getCommandSentTime());
      sentTimeColumnFamily.delete(sentTimeCompositeKey);
    }

    subscription.setCommandSentTime(sentTime);
    subscriptionColumnFamily.put(elementKeyAndMessageName, subscription);

    if (sentTime > 0) {
      this.sentTime.wrapLong(sentTime);
      sentTimeColumnFamily.put(sentTimeCompositeKey, DbNil.INSTANCE);
    }
  }

  public boolean existSubscriptionForElementInstance(
      long elementInstanceKey, DirectBuffer messageName) {
    wrapSubscriptionKeys(elementInstanceKey, messageName);

    return subscriptionColumnFamily.exists(elementKeyAndMessageName);
  }

  public boolean remove(long elementInstanceKey, DirectBuffer messageName) {
    final WorkflowInstanceSubscription subscription =
        getSubscription(elementInstanceKey, messageName);
    final boolean found = subscription != null;
    if (found) {
      remove(subscription);
    }
    return found;
  }

  public void remove(final WorkflowInstanceSubscription subscription) {
    wrapSubscriptionKeys(subscription.getElementInstanceKey(), subscription.getMessageName());

    subscriptionColumnFamily.delete(elementKeyAndMessageName);

    sentTime.wrapLong(subscription.getCommandSentTime());
    sentTimeColumnFamily.delete(sentTimeCompositeKey);
  }

  @FunctionalInterface
  public interface WorkflowInstanceSubscriptionVisitor {
    boolean visit(WorkflowInstanceSubscription subscription);
  }

  private void wrapSubscriptionKeys(long elementInstanceKey, DirectBuffer messageName) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);
  }
}
