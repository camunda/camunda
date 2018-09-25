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
import io.zeebe.broker.workflow.state.StoredRecord.Purpose;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.agrona.BitUtil;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;

public class ElementInstanceState {

  private static final byte[] ELEMENT_PARENT_CHILD_KEY_FAMILY_NAME =
      "elementParentChild".getBytes();
  private static final byte[] ELEMENT_INSTANCE_KEY_FAMILY_NAME = "elementInstanceKey".getBytes();
  private static final byte[] STORED_RECORDS_KEY_FAMILY_NAME = "storedRecords".getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {
    ELEMENT_PARENT_CHILD_KEY_FAMILY_NAME,
    ELEMENT_INSTANCE_KEY_FAMILY_NAME,
    STORED_RECORDS_KEY_FAMILY_NAME
  };

  private final StateController rocksDbWrapper;
  private final PersistenceHelper helper;

  private final ExpandableArrayBuffer keyBuffer;
  private final ExpandableArrayBuffer valueBuffer;

  private final UnsafeBuffer longKeyBuffer = new UnsafeBuffer(new byte[Long.BYTES]);
  private final UnsafeBuffer longKeyPurposeBuffer =
      new UnsafeBuffer(new byte[Long.BYTES + BitUtil.SIZE_OF_BYTE]);
  private final UnsafeBuffer iterateKeyBuffer = new UnsafeBuffer(0, 0);

  private final ColumnFamilyHandle elementParentChildHandle;
  private final ColumnFamilyHandle elementInstanceHandle;
  // (scopeKey, purpose, key) => record
  private final ColumnFamilyHandle storedRecordsHandle;

  private final Map<Long, ElementInstance> cachedInstances = new HashMap<>();

  public ElementInstanceState(StateController rocksDbWrapper) {
    this.rocksDbWrapper = rocksDbWrapper;
    this.helper = new PersistenceHelper(rocksDbWrapper);

    this.keyBuffer = new ExpandableArrayBuffer();
    this.valueBuffer = new ExpandableArrayBuffer();

    elementParentChildHandle =
        rocksDbWrapper.getColumnFamilyHandle(ELEMENT_PARENT_CHILD_KEY_FAMILY_NAME);
    elementInstanceHandle = rocksDbWrapper.getColumnFamilyHandle(ELEMENT_INSTANCE_KEY_FAMILY_NAME);
    storedRecordsHandle = rocksDbWrapper.getColumnFamilyHandle(STORED_RECORDS_KEY_FAMILY_NAME);
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
    final int parentKeyOffset = 0;
    instance.writeParentKey(keyBuffer, parentKeyOffset);
    final int instanceKeyOffset = instance.getParentKeyLength();
    instance.writeKey(keyBuffer, instanceKeyOffset);
    instance.write(valueBuffer, 0);

    final int keyLength = instance.getKeyLength();
    rocksDbWrapper.put(
        elementInstanceHandle,
        keyBuffer.byteArray(),
        instanceKeyOffset,
        keyLength,
        valueBuffer.byteArray(),
        0,
        instance.getLength());

    final int compositeKeyLength = keyLength + instance.getParentKeyLength();
    rocksDbWrapper.put(
        elementParentChildHandle,
        keyBuffer.byteArray(),
        parentKeyOffset,
        compositeKeyLength,
        PersistenceHelper.EXISTENCE,
        0,
        PersistenceHelper.EXISTENCE.length);
  }

  public ElementInstance getInstance(long key) {
    return cachedInstances.computeIfAbsent(
        key,
        k -> {
          keyBuffer.putLong(0, key, ByteOrder.LITTLE_ENDIAN);
          return helper.getValueInstance(
              ElementInstance.class, elementInstanceHandle, keyBuffer, 0, Long.BYTES, valueBuffer);
        });
  }

  public void removeInstance(long key) {
    final ElementInstance instance = getInstance(key);

    if (instance != null) {
      final int parentKeyOffset = 0;
      instance.writeParentKey(keyBuffer, parentKeyOffset);
      final int instanceKeyOffset = instance.getParentKeyLength();
      instance.writeKey(keyBuffer, instanceKeyOffset);
      final int compositeKeyLength = instance.getParentKeyLength() + instance.getKeyLength();

      rocksDbWrapper.remove(
          elementParentChildHandle, keyBuffer.byteArray(), parentKeyOffset, compositeKeyLength);
      rocksDbWrapper.remove(
          elementInstanceHandle, keyBuffer.byteArray(), instanceKeyOffset, instance.getKeyLength());
      cachedInstances.remove(key);

      longKeyBuffer.putLong(0, key, ByteOrder.LITTLE_ENDIAN);
      rocksDbWrapper.removeEntriesWithPrefix(storedRecordsHandle, longKeyBuffer.byteArray());

      final long parentKey = instance.getParentKey();
      if (parentKey > 0) {
        final ElementInstance parentInstance = getInstance(parentKey);
        parentInstance.decrementChildCount();
      }
    }
  }

  void updateInstance(ElementInstance scopeInstance) {
    writeElementInstance(scopeInstance);
  }

  public List<ElementInstance> getChildren(long parentKey) {
    final List<ElementInstance> children = new ArrayList<>();
    final ElementInstance parentInstance = getInstance(parentKey);
    if (parentInstance != null) {
      longKeyBuffer.putLong(0, parentKey, ByteOrder.LITTLE_ENDIAN);

      rocksDbWrapper.whileEqualPrefix(
          elementParentChildHandle,
          longKeyBuffer.byteArray(),
          (key, value) -> {
            iterateKeyBuffer.wrap(key);
            final long childKey =
                iterateKeyBuffer.getLong(
                    parentInstance.getParentKeyLength(), ByteOrder.LITTLE_ENDIAN);

            final ElementInstance instance = getInstance(childKey);
            children.add(instance);
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

  private int writeStoreRecordKeyIntoBuffer(
      MutableDirectBuffer buffer, int offset, long scopeKey, long recordKey, Purpose purpose) {
    buffer.putLong(offset, scopeKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;
    buffer.putByte(offset, (byte) purpose.ordinal());
    offset += BitUtil.SIZE_OF_BYTE;
    buffer.putLong(offset, recordKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;
    return offset;
  }

  public void storeRecord(
      long scopeKey, TypedRecord<WorkflowInstanceRecord> record, Purpose purpose) {
    final IndexedRecord indexedRecord =
        new IndexedRecord(
            record.getKey(),
            (WorkflowInstanceIntent) record.getMetadata().getIntent(),
            record.getValue());

    final StoredRecord storedRecord = new StoredRecord(indexedRecord, purpose);

    final int keyLength =
        writeStoreRecordKeyIntoBuffer(keyBuffer, 0, scopeKey, record.getKey(), purpose);
    storedRecord.write(valueBuffer, 0);

    rocksDbWrapper.put(
        storedRecordsHandle,
        keyBuffer.byteArray(),
        0,
        keyLength,
        valueBuffer.byteArray(),
        0,
        storedRecord.getLength());
  }

  public void removeStoredRecord(long scopeKey, long recordKey, Purpose purpose) {
    final int keyLength = writeStoreRecordKeyIntoBuffer(keyBuffer, 0, scopeKey, recordKey, purpose);

    rocksDbWrapper.remove(storedRecordsHandle, keyBuffer.byteArray(), 0, keyLength);
  }

  public List<IndexedRecord> getDeferredTokens(long scopeKey) {
    return getStoredRecords(scopeKey, Purpose.DEFERRED_TOKEN);
  }

  public List<IndexedRecord> getFinishedTokens(long scopeKey) {
    return getStoredRecords(scopeKey, Purpose.FINISHED_TOKEN);
  }

  private List<IndexedRecord> getStoredRecords(long scopeKey, Purpose purpose) {
    longKeyPurposeBuffer.putLong(0, scopeKey, ByteOrder.LITTLE_ENDIAN);
    longKeyPurposeBuffer.putByte(Long.BYTES, (byte) purpose.ordinal());

    final List<IndexedRecord> records = new ArrayList<>();
    rocksDbWrapper.whileEqualPrefix(
        storedRecordsHandle,
        longKeyPurposeBuffer.byteArray(),
        (key, value) -> {
          final StoredRecord record = new StoredRecord();
          record.wrap(new UnsafeBuffer(value), 0, value.length);
          records.add(record.getRecord());
        });
    return records;
  }

  public void flushDirtyState() {
    for (Entry<Long, ElementInstance> entry : cachedInstances.entrySet()) {
      updateInstance(entry.getValue());
    }
    cachedInstances.clear();
  }
}
