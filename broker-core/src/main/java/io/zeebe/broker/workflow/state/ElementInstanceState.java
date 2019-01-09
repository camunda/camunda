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
import org.agrona.ExpandableArrayBuffer;

public class ElementInstanceState {
  private final Map<Long, ElementInstance> cachedInstances = new HashMap<>();

  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> parentChildColumnFamily;
  private final DbCompositeKey<DbLong, DbLong> parentChildKey;
  private final DbLong parentKey;

  private final DbLong elementInstanceKey;
  private final ElementInstance elementInstance;
  private final ColumnFamily<DbLong, ElementInstance> elementInstanceColumnFamily;

  private final DbLong recordKey;
  private final StoredRecord storedRecord;
  private final ColumnFamily<DbLong, StoredRecord> recordColumnFamily;

  private final DbLong recordParentKey;
  private final DbCompositeKey<DbCompositeKey<DbLong, DbByte>, DbLong> recordParentStateRecordKey;
  private final DbByte stateKey;
  private final DbCompositeKey<DbLong, DbByte> recordParentStateKey;
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbLong, DbByte>, DbLong>, DbNil>
      recordParentChildColumnFamily;

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

    recordKey = new DbLong();
    storedRecord = new StoredRecord();
    recordColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.STORED_INSTANCE_EVENTS, recordKey, storedRecord);

    recordParentKey = new DbLong();
    stateKey = new DbByte();
    recordParentStateKey = new DbCompositeKey<>(recordParentKey, stateKey);
    recordParentStateRecordKey = new DbCompositeKey<>(recordParentStateKey, recordKey);
    recordParentChildColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.STORED_INSTANCE_EVENTS_PARENT_CHILD,
            recordParentStateRecordKey,
            DbNil.INSTANCE);

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

      recordParentChildColumnFamily.whileEqualPrefix(
          elementInstanceKey,
          (compositeKey, nil) -> {
            recordParentChildColumnFamily.delete(compositeKey);
            recordColumnFamily.delete(compositeKey.getSecond());
          });

      variablesState.removeScope(key);

      final long parentKey = instance.getParentKey();
      if (parentKey > 0) {
        final ElementInstance parentInstance = getInstance(parentKey);
        parentInstance.decrementChildCount();
      }
    }
  }

  public StoredRecord getStoredRecord(long recordKey) {
    this.recordKey.wrapLong(recordKey);
    return recordColumnFamily.get(this.recordKey);
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

  public void storeRecord(
      long scopeKey, TypedRecord<WorkflowInstanceRecord> record, Purpose purpose) {
    final IndexedRecord indexedRecord =
        new IndexedRecord(
            record.getKey(),
            (WorkflowInstanceIntent) record.getMetadata().getIntent(),
            record.getValue());
    final StoredRecord storedRecord = new StoredRecord(indexedRecord, purpose);

    setRecordKeys(scopeKey, record.getKey(), purpose);

    recordColumnFamily.put(recordKey, storedRecord);
    recordParentChildColumnFamily.put(recordParentStateRecordKey, DbNil.INSTANCE);
  }

  public void removeStoredRecord(long scopeKey, long recordKey, Purpose purpose) {
    setRecordKeys(scopeKey, recordKey, purpose);

    recordColumnFamily.delete(this.recordKey);
    recordParentChildColumnFamily.delete(recordParentStateRecordKey);
  }

  private void setRecordKeys(long scopeKey, long recordKey, Purpose purpose) {
    recordParentKey.wrapLong(scopeKey);
    stateKey.wrapByte((byte) purpose.ordinal());
    this.recordKey.wrapLong(recordKey);
  }

  public List<IndexedRecord> getDeferredRecords(long scopeKey) {
    return collectRecords(scopeKey, Purpose.DEFERRED);
  }

  public IndexedRecord getFailedRecord(long key) {
    final StoredRecord storedRecord = getStoredRecord(key);
    if (storedRecord != null && storedRecord.getPurpose() == Purpose.FAILED) {
      return storedRecord.getRecord();
    } else {
      return null;
    }
  }

  public void updateFailedRecord(IndexedRecord indexedRecord) {
    final StoredRecord storedRecord = new StoredRecord(indexedRecord, Purpose.FAILED);
    recordKey.wrapLong(indexedRecord.getKey());
    recordColumnFamily.put(recordKey, storedRecord);
  }

  public List<IndexedRecord> getFinishedRecords(long scopeKey) {
    return collectRecords(scopeKey, Purpose.FINISHED);
  }

  private List<IndexedRecord> collectRecords(long scopeKey, Purpose purpose) {
    final List<IndexedRecord> records = new ArrayList<>();
    visitRecords(
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
    return elementInstanceColumnFamily.isEmpty()
        && parentChildColumnFamily.isEmpty()
        && recordColumnFamily.isEmpty()
        && recordParentChildColumnFamily.isEmpty()
        && variablesState.isEmpty();
  }

  @FunctionalInterface
  public interface RecordVisitor {
    void visitRecord(IndexedRecord indexedRecord);
  }

  public void visitFailedRecords(long scopeKey, RecordVisitor visitor) {
    visitRecords(scopeKey, Purpose.FAILED, visitor);
  }

  private void visitRecords(long scopeKey, Purpose purpose, RecordVisitor visitor) {
    recordParentKey.wrapLong(scopeKey);
    stateKey.wrapByte((byte) purpose.ordinal());

    recordParentChildColumnFamily.whileEqualPrefix(
        recordParentStateKey,
        (compositeKey, value) -> {
          final DbLong recordKey = compositeKey.getSecond();
          final StoredRecord storedRecord = recordColumnFamily.get(recordKey);
          if (storedRecord != null) {
            visitor.visitRecord(storedRecord.getRecord());
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
