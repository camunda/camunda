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
package io.zeebe.broker.workflow.state;

import io.zeebe.broker.logstreams.state.ZbColumnFamilies;
import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public class EventScopeInstanceState {

  private final DbLong eventScopeInstanceKey;
  private final EventScopeInstance eventScopeInstance;
  private final ColumnFamily<DbLong, EventScopeInstance> eventScopeInstanceColumnFamily;

  private final DbLong eventTriggerScopeKey;
  private final DbLong eventTriggerPositionKey;
  private final DbCompositeKey<DbLong, DbLong> eventTriggerKey;
  private final EventTrigger eventTrigger;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, EventTrigger> eventTriggerColumnFamily;

  private final ExpandableArrayBuffer copyBuffer = new ExpandableArrayBuffer();

  public EventScopeInstanceState(ZeebeDb<ZbColumnFamilies> zeebeDb) {
    eventScopeInstanceKey = new DbLong();
    eventScopeInstance = new EventScopeInstance();
    eventScopeInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.EVENT_SCOPE, eventScopeInstanceKey, eventScopeInstance);

    eventTriggerScopeKey = new DbLong();
    eventTriggerPositionKey = new DbLong();
    eventTriggerKey = new DbCompositeKey<>(eventTriggerScopeKey, eventTriggerPositionKey);
    eventTrigger = new EventTrigger();
    eventTriggerColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.EVENT_TRIGGER, eventTriggerKey, eventTrigger);
  }

  /**
   * Creates a new event scope instance in the state
   *
   * @param eventScopeKey the event scope key
   * @param interrupting if the scope is interrupting, i.e. only accepts a single event trigger
   */
  public void createInstance(long eventScopeKey, boolean interrupting) {
    eventScopeInstanceKey.wrapLong(eventScopeKey);
    eventScopeInstance.setAccepting(true).setInterrupting(interrupting);
    eventScopeInstanceColumnFamily.put(eventScopeInstanceKey, eventScopeInstance);
  }

  /**
   * Returns a event scope instance by key or null if none exists with this key.
   *
   * @param eventScopeKey the key of the event scope
   * @return the event scope instance or null
   */
  public EventScopeInstance getInstance(long eventScopeKey) {
    eventScopeInstanceKey.wrapLong(eventScopeKey);
    final EventScopeInstance instance = eventScopeInstanceColumnFamily.get(eventScopeInstanceKey);
    return instance != null ? new EventScopeInstance(instance) : null;
  }

  /**
   * Delete an event scope from the state. Does not fail in case the scope does not exist.
   *
   * @param eventScopeKey the key of the event scope to delete
   */
  public void deleteInstance(long eventScopeKey) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);

    eventTriggerColumnFamily.whileEqualPrefix(
        eventTriggerScopeKey,
        (BiConsumer<DbCompositeKey<DbLong, DbLong>, EventTrigger>)
            (key, value) -> deleteTrigger(key));

    eventScopeInstanceKey.wrapLong(eventScopeKey);
    eventScopeInstanceColumnFamily.delete(eventScopeInstanceKey);
  }

  /**
   * @param eventScopeKey the key of the event scope the event is triggered in
   * @param position the position of the event record (used for ordering)
   * @param elementId the id of the element which should be triggered, e.g. boundary event
   * @param payload the payload of the occurred event, i.e. message payload
   * @return true if the event was accepted by the event scope, false otherwise
   */
  public boolean triggerEvent(
      long eventScopeKey, long position, DirectBuffer elementId, DirectBuffer payload) {
    eventScopeInstanceKey.wrapLong(eventScopeKey);
    final EventScopeInstance instance = eventScopeInstanceColumnFamily.get(eventScopeInstanceKey);

    if (instance != null && instance.isAccepting()) {
      if (instance.isInterrupting()) {
        instance.setAccepting(false);
        eventScopeInstanceColumnFamily.put(eventScopeInstanceKey, instance);
      }

      createTrigger(eventScopeKey, position, elementId, payload);

      return true;
    } else {
      return false;
    }
  }

  private void createTrigger(
      long eventScopeKey, long position, DirectBuffer elementId, DirectBuffer payload) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    eventTriggerPositionKey.wrapLong(position);

    eventTrigger.setElementId(elementId).setPayload(payload);

    eventTriggerColumnFamily.put(eventTriggerKey, eventTrigger);
  }

  /**
   * Returns the next event trigger for the event scope or null if non exists. This will not remove
   * the event trigger from the state.
   *
   * @param eventScopeKey the key of the event scope
   * @return the next event trigger or null if non exist
   */
  public EventTrigger peekEventTrigger(long eventScopeKey) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    final EventTrigger[] next = new EventTrigger[1];
    eventTriggerColumnFamily.whileEqualPrefix(
        eventTriggerScopeKey,
        (key, value) -> {
          next[0] = new EventTrigger(value);
          return false;
        });

    return next[0];
  }

  /**
   * Returns the next event trigger for the event scope or null if non exists. This will remove the
   * polled event trigger from the state if it exists.
   *
   * @param eventScopeKey the key of the event scope
   * @return the next event trigger or null if non exist
   */
  public EventTrigger pollEventTrigger(long eventScopeKey) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    final EventTrigger[] next = new EventTrigger[1];
    eventTriggerColumnFamily.whileEqualPrefix(
        eventTriggerScopeKey,
        (key, value) -> {
          next[0] = new EventTrigger(value);
          eventTriggerColumnFamily.delete(key);
          return false;
        });

    return next[0];
  }

  /**
   * Deletes an event trigger by key and position. Does not fail if the trigger does not exist.
   *
   * @param eventScopeKey the key of the event scope
   * @param position the position of the event trigger
   */
  public void deleteTrigger(long eventScopeKey, long position) {
    eventTriggerScopeKey.wrapLong(eventScopeKey);
    eventTriggerPositionKey.wrapLong(position);
    deleteTrigger(eventTriggerKey);
  }

  private void deleteTrigger(DbCompositeKey<DbLong, DbLong> triggerKey) {
    eventTriggerColumnFamily.delete(triggerKey);
  }
}
