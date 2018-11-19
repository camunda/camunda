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

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.workflow.state.StoredRecord.Purpose;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
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
      "elementInstanceStateElementParentChild".getBytes();
  private static final byte[] ELEMENT_INSTANCE_KEY_FAMILY_NAME =
      "elementInstanceStateElementInstanceKey".getBytes();
  private static final byte[] TOKEN_EVENTS_KEY_FAMILY_NAME =
      "elementInstanceStateTokenEvents".getBytes();
  private static final byte[] TOKEN_PARENT_CHILD_KEY_FAMILY_NAME =
      "elementInstanceStateTokenParentChild".getBytes();
  private static final byte[] EMPTY_VALUE = new byte[1];

  public static final byte[][] COLUMN_FAMILY_NAMES = {
    ELEMENT_PARENT_CHILD_KEY_FAMILY_NAME,
    ELEMENT_INSTANCE_KEY_FAMILY_NAME,
    TOKEN_EVENTS_KEY_FAMILY_NAME,
    TOKEN_PARENT_CHILD_KEY_FAMILY_NAME
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

  // key => record
  private final ColumnFamilyHandle tokenEventHandle;
  // (element instance key, purpose) => token event key
  private final ColumnFamilyHandle tokenParentChildHandle;

  private final Map<Long, ElementInstance> cachedInstances = new HashMap<>();

  public ElementInstanceState(StateController rocksDbWrapper) {
    this.rocksDbWrapper = rocksDbWrapper;
    this.helper = new PersistenceHelper(rocksDbWrapper);

    this.keyBuffer = new ExpandableArrayBuffer();
    this.valueBuffer = new ExpandableArrayBuffer();

    elementParentChildHandle =
        rocksDbWrapper.getColumnFamilyHandle(ELEMENT_PARENT_CHILD_KEY_FAMILY_NAME);
    elementInstanceHandle = rocksDbWrapper.getColumnFamilyHandle(ELEMENT_INSTANCE_KEY_FAMILY_NAME);
    tokenEventHandle = rocksDbWrapper.getColumnFamilyHandle(TOKEN_EVENTS_KEY_FAMILY_NAME);
    tokenParentChildHandle =
        rocksDbWrapper.getColumnFamilyHandle(TOKEN_PARENT_CHILD_KEY_FAMILY_NAME);
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
          keyBuffer.putLong(0, key, STATE_BYTE_ORDER);
          return helper.getValueInstance(
              ElementInstance.class, elementInstanceHandle, keyBuffer, 0, Long.BYTES);
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

      longKeyBuffer.putLong(0, key, STATE_BYTE_ORDER);

      rocksDbWrapper.removeEntriesWithPrefix(
          tokenParentChildHandle,
          longKeyBuffer.byteArray(),
          (k, v) -> {
            rocksDbWrapper.remove(tokenEventHandle, getLong(k, Long.BYTES + BitUtil.SIZE_OF_BYTE));
          });

      final long parentKey = instance.getParentKey();
      if (parentKey > 0) {
        final ElementInstance parentInstance = getInstance(parentKey);
        parentInstance.decrementChildCount();
      }
    }
  }

  public StoredRecord getTokenEvent(long key) {
    keyBuffer.putLong(0, key, STATE_BYTE_ORDER);
    return helper.getValueInstance(StoredRecord.class, tokenEventHandle, keyBuffer, 0, Long.BYTES);
  }

  void updateInstance(ElementInstance scopeInstance) {
    writeElementInstance(scopeInstance);
  }

  public List<ElementInstance> getChildren(long parentKey) {
    final List<ElementInstance> children = new ArrayList<>();
    final ElementInstance parentInstance = getInstance(parentKey);
    if (parentInstance != null) {
      longKeyBuffer.putLong(0, parentKey, STATE_BYTE_ORDER);

      rocksDbWrapper.whileEqualPrefix(
          elementParentChildHandle,
          longKeyBuffer.byteArray(),
          (key, value) -> {
            iterateKeyBuffer.wrap(key);
            final long childKey =
                iterateKeyBuffer.getLong(parentInstance.getParentKeyLength(), STATE_BYTE_ORDER);

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
    buffer.putLong(offset, scopeKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;
    buffer.putByte(offset, (byte) purpose.ordinal());
    offset += BitUtil.SIZE_OF_BYTE;
    buffer.putLong(offset, recordKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;
    return offset;
  }

  public void storeTokenEvent(
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
        tokenEventHandle,
        keyBuffer.byteArray(),
        Long.BYTES + BitUtil.SIZE_OF_BYTE,
        Long.BYTES,
        valueBuffer.byteArray(),
        0,
        storedRecord.getLength());

    rocksDbWrapper.put(
        tokenParentChildHandle,
        keyBuffer.byteArray(),
        0,
        keyLength,
        EMPTY_VALUE,
        0,
        EMPTY_VALUE.length);
  }

  public void removeStoredRecord(long scopeKey, long recordKey, Purpose purpose) {
    final int keyLength = writeStoreRecordKeyIntoBuffer(keyBuffer, 0, scopeKey, recordKey, purpose);

    rocksDbWrapper.remove(tokenParentChildHandle, keyBuffer.byteArray(), 0, keyLength);
    rocksDbWrapper.remove(
        tokenEventHandle, keyBuffer.byteArray(), Long.BYTES + BitUtil.SIZE_OF_BYTE, Long.BYTES);
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

    keyBuffer.putLong(0, indexedRecord.getKey(), STATE_BYTE_ORDER);
    storedRecord.write(valueBuffer, 0);

    rocksDbWrapper.put(
        tokenEventHandle,
        keyBuffer.byteArray(),
        0,
        Long.BYTES,
        valueBuffer.byteArray(),
        0,
        storedRecord.getLength());
  }

  public List<IndexedRecord> getFinishedTokens(long scopeKey) {
    return collectTokenEvents(scopeKey, Purpose.FINISHED_TOKEN);
  }

  private List<IndexedRecord> collectTokenEvents(long scopeKey, Purpose purpose) {
    final List<IndexedRecord> records = new ArrayList<>();
    visitTokens(scopeKey, purpose, records::add);
    return records;
  }

  @FunctionalInterface
  public interface TokenVisitor {
    void visitToken(IndexedRecord indexedRecord);
  }

  public void visitFailedTokens(long scopeKey, TokenVisitor visitor) {
    visitTokens(scopeKey, Purpose.FAILED_TOKEN, visitor);
  }

  private void visitTokens(long scopeKey, Purpose purpose, TokenVisitor visitor) {
    longKeyPurposeBuffer.putLong(0, scopeKey, STATE_BYTE_ORDER);
    longKeyPurposeBuffer.putByte(Long.BYTES, (byte) purpose.ordinal());

    rocksDbWrapper.whileEqualPrefix(
        tokenParentChildHandle,
        longKeyPurposeBuffer.byteArray(),
        (key, value) -> {
          final StoredRecord tokenEvent =
              getTokenEvent(getLong(key, Long.BYTES + BitUtil.SIZE_OF_BYTE));
          if (tokenEvent != null) {
            visitor.visitToken(tokenEvent.getRecord());
          }
        });
  }

  private static long getLong(byte[] array, int offset) {
    return new UnsafeBuffer(array, offset, Long.BYTES).getLong(0, STATE_BYTE_ORDER);
  }

  public void flushDirtyState() {
    for (Entry<Long, ElementInstance> entry : cachedInstances.entrySet()) {
      updateInstance(entry.getValue());
    }
    cachedInstances.clear();
  }
}
