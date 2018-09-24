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
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;

public class ElementInstanceState {

  private static final byte[] ELEMENT_TREE_KEY_FAMILY_NAME = "elementTreeKey".getBytes();
  private static final byte[] ELEMENT_INSTANCE_KEY_FAMILY_NAME = "elementInstanceKey".getBytes();
  private static final byte[] STORED_RECORDS_KEY_FAMILY_NAME = "storedRecords".getBytes();

  private final StateController rocksDbWrapper;
  private final PersistenceHelper helper;

  private final ExpandableArrayBuffer keyBuffer;
  private final ExpandableArrayBuffer valueBuffer;

  private final UnsafeBuffer longKeyBuffer = new UnsafeBuffer(new byte[Long.BYTES]);
  private final UnsafeBuffer iterateKeyBuffer = new UnsafeBuffer(0, 0);

  private final ColumnFamilyHandle elementTreeHandle;
  private final ColumnFamilyHandle elementInstanceHandle;
  private final ColumnFamilyHandle storedRecordsHandle;

  public ElementInstanceState(StateController rocksDbWrapper) throws Exception {
    this.rocksDbWrapper = rocksDbWrapper;
    this.helper = new PersistenceHelper(rocksDbWrapper);

    this.keyBuffer = new ExpandableArrayBuffer();
    this.valueBuffer = new ExpandableArrayBuffer();

    elementTreeHandle = rocksDbWrapper.createColumnFamily(ELEMENT_TREE_KEY_FAMILY_NAME);
    elementInstanceHandle = rocksDbWrapper.createColumnFamily(ELEMENT_INSTANCE_KEY_FAMILY_NAME);
    storedRecordsHandle = rocksDbWrapper.createColumnFamily(STORED_RECORDS_KEY_FAMILY_NAME);
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
    instance.writeKey(keyBuffer, 0);
    instance.write(valueBuffer, 0);

    rocksDbWrapper.put(
        elementInstanceHandle, instance.getKey(), valueBuffer.byteArray(), 0, instance.getLength());

    rocksDbWrapper.put(
        elementTreeHandle,
        keyBuffer.byteArray(),
        0,
        instance.getKeyLength(),
        valueBuffer.byteArray(),
        0,
        instance.getLength());
  }

  private final Map<Long, ElementInstance> cachedInstances = new HashMap<>();

  public ElementInstance getInstance(long key) {
    return cachedInstances.computeIfAbsent(
        key,
        k -> {
          keyBuffer.putLong(0, key, ByteOrder.LITTLE_ENDIAN);
          return helper.getValueInstance(
              ElementInstance.class, elementInstanceHandle, keyBuffer, Long.BYTES, valueBuffer);
        });
  }

  public void removeInstance(long key) {
    final ElementInstance instance = getInstance(key);

    if (instance != null) {
      instance.writeKey(keyBuffer, 0);

      rocksDbWrapper.remove(elementTreeHandle, keyBuffer.byteArray(), 0, instance.getKeyLength());
      rocksDbWrapper.remove(elementInstanceHandle, key);
      cachedInstances.remove(key);

      final int treeDepth = instance.getDepth();
      if (treeDepth > 0) {
        instance.writeParentKey(keyBuffer, 0);
        final int parentKeyLength = instance.getParentKeyLength();
        final long parentKey =
            keyBuffer.getLong(parentKeyLength - Long.BYTES, ByteOrder.LITTLE_ENDIAN);
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
      final DirectBuffer identifier = parentInstance.getIdentifier();

      rocksDbWrapper.foreach(
          elementTreeHandle,
          identifier.byteArray(),
          (key, value) -> {
            final DirectBuffer keyBuffer = new UnsafeBuffer(key, 0, identifier.capacity());
            final boolean equalKeyPrefix = keyBuffer.equals(identifier);
            if (equalKeyPrefix && key.length == identifier.capacity() + Long.BYTES) {
              valueBuffer.putBytes(0, value);
              final ElementInstance instance = new ElementInstance();
              instance.wrap(valueBuffer, 0, value.length);
              children.add(instance);
            }
            return equalKeyPrefix;
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
      MutableDirectBuffer buffer, int offset, long scopeKey, long recordKey) {
    buffer.putLong(offset, scopeKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;
    buffer.putLong(offset, recordKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;
    return offset;
  }

  public void storeRecord(long scopeKey, TypedRecord<WorkflowInstanceRecord> record) {
    final IndexedRecord indexedRecord =
        new IndexedRecord(
            record.getKey(),
            (WorkflowInstanceIntent) record.getMetadata().getIntent(),
            record.getValue());

    final int keyLength = writeStoreRecordKeyIntoBuffer(keyBuffer, 0, scopeKey, record.getKey());
    indexedRecord.write(valueBuffer, 0);

    rocksDbWrapper.put(
        storedRecordsHandle,
        keyBuffer.byteArray(),
        0,
        keyLength,
        valueBuffer.byteArray(),
        0,
        indexedRecord.getLength());
  }

  public boolean removeStoredRecord(long scopeKey, long recordKey) {
    final int keyLength = writeStoreRecordKeyIntoBuffer(keyBuffer, 0, scopeKey, recordKey);

    final IndexedRecord record =
        helper.getValueInstance(
            IndexedRecord.class, storedRecordsHandle, keyBuffer, keyLength, valueBuffer);

    final boolean exist = record != null;
    if (exist) {
      rocksDbWrapper.remove(storedRecordsHandle, keyBuffer.byteArray(), 0, keyLength);
    }
    return exist;
  }

  public List<IndexedRecord> getStoredRecords(long scopeKey) {
    longKeyBuffer.putLong(0, scopeKey, ByteOrder.LITTLE_ENDIAN);

    final List<IndexedRecord> records = new ArrayList<>();
    rocksDbWrapper.foreach(
        storedRecordsHandle,
        longKeyBuffer.byteArray(),
        (key, value) -> {
          iterateKeyBuffer.wrap(key, 0, Long.BYTES);

          final boolean hasSamePrefix = iterateKeyBuffer.equals(longKeyBuffer);
          if (hasSamePrefix) {
            final IndexedRecord record = new IndexedRecord();
            record.wrap(new UnsafeBuffer(value), 0, value.length);
            records.add(record);
          }
          return hasSamePrefix;
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
