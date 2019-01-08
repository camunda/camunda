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

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.state.ZbColumnFamilies;
import io.zeebe.broker.workflow.state.StoredRecord.Purpose;
import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbByte;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.ExpandableArrayBuffer;

public class ElementInstanceState {
  private final Map<Long, ElementInstance> cachedInstances = new HashMap<>();

  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> parentChildColumnFamily;
  private final DbCompositeKey<DbLong, DbLong> parentChildKey;
  private final DbLong parentKey;

  private final DbLong elementInstanceKey;
  private final ElementInstance elementInstance;
  private final ColumnFamily<DbLong, ElementInstance> elementInstanceColumnFamily;

  private final DbLong tokenKey;
  private final StoredRecord storedRecord;
  private final ColumnFamily<DbLong, StoredRecord> tokenColumnFamily;

  private final DbLong tokenParentKey;
  private final DbCompositeKey<DbCompositeKey<DbLong, DbByte>, DbLong> tokenParentStateTokenKey;
  private final DbByte stateKey;
  private final DbCompositeKey<DbLong, DbByte> tokenParentStateKey;
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbLong, DbByte>, DbLong>, DbNil>
      tokenParentChildColumnFamily;

  private final ExpandableArrayBuffer copyBuffer = new ExpandableArrayBuffer();

  private final VariablesState variablesState;

  public ElementInstanceState(ZeebeDb<ZbColumnFamilies> zeebeDb) {

    elementInstanceKey = new DbLong();
    parentKey = new DbLong();
    parentChildKey = new DbCompositeKey<>(parentKey, elementInstanceKey);
    parentChildColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD, parentChildKey, DbNil.INSTANCE);

    elementInstance = new ElementInstance();
    elementInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_KEY, elementInstanceKey, elementInstance);

    tokenKey = new DbLong();
    storedRecord = new StoredRecord();
    tokenColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.TOKEN_EVENTS, tokenKey, storedRecord);

    tokenParentKey = new DbLong();
    stateKey = new DbByte();
    tokenParentStateKey = new DbCompositeKey<>(tokenParentKey, stateKey);
    tokenParentStateTokenKey = new DbCompositeKey<>(tokenParentStateKey, tokenKey);
    tokenParentChildColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TOKEN_PARENT_CHILD, tokenParentStateTokenKey, DbNil.INSTANCE);

    variablesState = new VariablesState(zeebeDb);
  }

  public ElementInstance newInstance(
      long key, WorkflowInstanceRecord value, WorkflowInstanceIntent state) {
    return newInstance(null, key, value, state);
  }

  public ElementInstance newInstance(
      ElementInstance parent,
      long key,
      WorkflowInstanceRecord value,
      WorkflowInstanceIntent state) {

    final ElementInstance instance;
    if (parent == null) {
      instance = new ElementInstance(key, state, value);
    } else {
      instance = new ElementInstance(key, parent, state, value);
    }
    cachedInstances.put(key, instance);

    return instance;
  }

  private void writeElementInstance(ElementInstance instance) {
    elementInstanceKey.wrapLong(instance.getKey());
    parentKey.wrapLong(instance.getParentKey());

    elementInstanceColumnFamily.put(elementInstanceKey, instance);
    parentChildColumnFamily.put(parentChildKey, DbNil.INSTANCE);
    variablesState.createScope(elementInstanceKey.getValue(), parentKey.getValue());
  }

  public ElementInstance getInstance(long key) {
    return cachedInstances.computeIfAbsent(
        key,
        k -> {
          elementInstanceKey.wrapLong(key);
          final ElementInstance elementInstance =
              elementInstanceColumnFamily.get(elementInstanceKey);

          final ElementInstance copiedElementInstance = copyElementInstance(elementInstance);
          return copiedElementInstance != null ? copiedElementInstance : null;
        });
  }

  public void removeInstance(long key) {
    final ElementInstance instance = getInstance(key);

    if (instance != null) {
      elementInstanceKey.wrapLong(key);
      parentKey.wrapLong(instance.getParentKey());

      parentChildColumnFamily.delete(parentChildKey);
      elementInstanceColumnFamily.delete(elementInstanceKey);
      cachedInstances.remove(key);

      tokenParentChildColumnFamily.whileEqualPrefix(
          elementInstanceKey,
          (compositeKey, nil) -> {
            tokenParentChildColumnFamily.delete(compositeKey);
            tokenColumnFamily.delete(compositeKey.getSecond());
          });

      variablesState.removeScope(key);

      final long parentKey = instance.getParentKey();
      if (parentKey > 0) {
        final ElementInstance parentInstance = getInstance(parentKey);
        parentInstance.decrementChildCount();
      }
    }
  }

  public StoredRecord getTokenEvent(long tokenKey) {
    this.tokenKey.wrapLong(tokenKey);
    return tokenColumnFamily.get(this.tokenKey);
  }

  void updateInstance(ElementInstance scopeInstance) {
    writeElementInstance(scopeInstance);
  }

  public List<ElementInstance> getChildren(long parentKey) {
    final List<ElementInstance> children = new ArrayList<>();
    final ElementInstance parentInstance = getInstance(parentKey);
    if (parentInstance != null) {
      this.parentKey.wrapLong(parentKey);

      parentChildColumnFamily.whileEqualPrefix(
          this.parentKey,
          (key, value) -> {
            final DbLong childKey = key.getSecond();
            final ElementInstance childInstance = getInstance(childKey.getValue());

            final ElementInstance copiedElementInstance = copyElementInstance(childInstance);
            children.add(copiedElementInstance);
          });
    }
    return children;
  }

  public void consumeToken(long scopeKey) {
    final ElementInstance elementInstance = getInstance(scopeKey);
    if (elementInstance != null) {
      elementInstance.consumeToken();
    }
  }

  public void spawnToken(long scopeKey) {
    final ElementInstance elementInstance = getInstance(scopeKey);
    if (elementInstance != null) {
      elementInstance.spawnToken();
    }
  }

  public void storeTokenEvent(
      long scopeKey, TypedRecord<WorkflowInstanceRecord> record, Purpose purpose) {
    final IndexedRecord indexedRecord =
        new IndexedRecord(
            record.getKey(),
            (WorkflowInstanceIntent) record.getMetadata().getIntent(),
            record.getValue());
    final StoredRecord storedRecord = new StoredRecord(indexedRecord, purpose);

    setTokenKeys(scopeKey, record.getKey(), purpose);

    tokenColumnFamily.put(tokenKey, storedRecord);
    tokenParentChildColumnFamily.put(tokenParentStateTokenKey, DbNil.INSTANCE);
  }

  public void removeStoredRecord(long scopeKey, long recordKey, Purpose purpose) {
    setTokenKeys(scopeKey, recordKey, purpose);

    tokenColumnFamily.delete(tokenKey);
    tokenParentChildColumnFamily.delete(tokenParentStateTokenKey);
  }

  private void setTokenKeys(long scopeKey, long recordKey, Purpose purpose) {
    tokenParentKey.wrapLong(scopeKey);
    stateKey.wrapByte((byte) purpose.ordinal());
    tokenKey.wrapLong(recordKey);
  }

  public List<IndexedRecord> getDeferredTokens(long scopeKey) {
    return collectTokenEvents(scopeKey, Purpose.DEFERRED_TOKEN);
  }

  public IndexedRecord getFailedToken(long key) {
    final StoredRecord tokenEvent = getTokenEvent(key);
    if (tokenEvent != null && tokenEvent.getPurpose() == Purpose.FAILED_TOKEN) {
      return tokenEvent.getRecord();
    } else {
      return null;
    }
  }

  public void updateFailedToken(IndexedRecord indexedRecord) {
    final StoredRecord storedRecord = new StoredRecord(indexedRecord, Purpose.FAILED_TOKEN);
    tokenKey.wrapLong(indexedRecord.getKey());
    tokenColumnFamily.put(tokenKey, storedRecord);
  }

  public List<IndexedRecord> getFinishedTokens(long scopeKey) {
    return collectTokenEvents(scopeKey, Purpose.FINISHED_TOKEN);
  }

  private List<IndexedRecord> collectTokenEvents(long scopeKey, Purpose purpose) {
    final List<IndexedRecord> records = new ArrayList<>();
    visitTokens(
        scopeKey,
        purpose,
        (indexedRecord) -> {
          // the visited elements are only transient
          // they will change on next iteration we have to copy them
          // if we need to store the values

          // for now we simply copy them into buffer and wrap the buffer
          // which does another copy
          // this could be improve if UnpackedObject has a clone method
          indexedRecord.write(copyBuffer, 0);
          final IndexedRecord copiedRecord = new IndexedRecord();
          copiedRecord.wrap(copyBuffer, 0, indexedRecord.getLength());

          records.add(copiedRecord);
        });
    return records;
  }

  public boolean isEmpty() {
    final AtomicBoolean isEmpty = new AtomicBoolean(true);

    elementInstanceColumnFamily.whileTrue(
        (k, v) -> {
          isEmpty.compareAndSet(true, false);
          return false;
        });

    parentChildColumnFamily.whileTrue(
        (k, v) -> {
          isEmpty.compareAndSet(true, false);
          return false;
        });

    tokenColumnFamily.whileTrue(
        (k, v) -> {
          isEmpty.compareAndSet(true, false);
          return false;
        });

    tokenParentChildColumnFamily.whileTrue(
        (k, v) -> {
          isEmpty.compareAndSet(true, false);
          return false;
        });

    return isEmpty.get() && variablesState.isEmpty();
  }

  @FunctionalInterface
  public interface TokenVisitor {
    void visitToken(IndexedRecord indexedRecord);
  }

  public void visitFailedTokens(long scopeKey, TokenVisitor visitor) {
    visitTokens(scopeKey, Purpose.FAILED_TOKEN, visitor);
  }

  private void visitTokens(long scopeKey, Purpose purpose, TokenVisitor visitor) {
    tokenParentKey.wrapLong(scopeKey);
    stateKey.wrapByte((byte) purpose.ordinal());

    tokenParentChildColumnFamily.whileEqualPrefix(
        tokenParentStateKey,
        (compositeKey, value) -> {
          final DbLong tokenKey = compositeKey.getSecond();
          final StoredRecord tokenEvent = tokenColumnFamily.get(tokenKey);
          if (tokenEvent != null) {
            visitor.visitToken(tokenEvent.getRecord());
          }
        });
  }

  public VariablesState getVariablesState() {
    return variablesState;
  }

  public void flushDirtyState() {
    for (Entry<Long, ElementInstance> entry : cachedInstances.entrySet()) {
      updateInstance(entry.getValue());
    }
    cachedInstances.clear();
  }

  private ElementInstance copyElementInstance(ElementInstance elementInstance) {
    if (elementInstance != null) {
      elementInstance.write(copyBuffer, 0);

      final ElementInstance copiedElementInstance = new ElementInstance();
      copiedElementInstance.wrap(copyBuffer, 0, elementInstance.getLength());
      return copiedElementInstance;
    }
    return null;
  }
}
