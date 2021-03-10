/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbByte;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.concurrent.UnsafeBuffer;

public final class DbElementInstanceState implements MutableElementInstanceState {

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

  private final AwaitProcessInstanceResultMetadata awaitResultMetadata;
  private final ColumnFamily<DbLong, AwaitProcessInstanceResultMetadata>
      awaitProcessInstanceResultMetadataColumnFamily;

  private final MutableVariableState variableState;

  public DbElementInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final MutableVariableState variableState) {

    this.variableState = variableState;

    elementInstanceKey = new DbLong();
    parentKey = new DbLong();
    parentChildKey = new DbCompositeKey<>(parentKey, elementInstanceKey);
    parentChildColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD,
            transactionContext,
            parentChildKey,
            DbNil.INSTANCE);

    elementInstance = new ElementInstance();
    elementInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
            transactionContext,
            elementInstanceKey,
            elementInstance);

    recordKey = new DbLong();
    storedRecord = new StoredRecord();
    recordColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.STORED_INSTANCE_EVENTS, transactionContext, recordKey, storedRecord);

    recordParentKey = new DbLong();
    stateKey = new DbByte();
    recordParentStateKey = new DbCompositeKey<>(recordParentKey, stateKey);
    recordParentStateRecordKey = new DbCompositeKey<>(recordParentStateKey, recordKey);
    recordParentChildColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.STORED_INSTANCE_EVENTS_PARENT_CHILD,
            transactionContext,
            recordParentStateRecordKey,
            DbNil.INSTANCE);

    awaitResultMetadata = new AwaitProcessInstanceResultMetadata();
    awaitProcessInstanceResultMetadataColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AWAIT_WORKLOW_RESULT,
            transactionContext,
            elementInstanceKey,
            awaitResultMetadata);
  }

  @Override
  public ElementInstance newInstance(
      final long key, final ProcessInstanceRecord value, final ProcessInstanceIntent state) {
    return newInstance(null, key, value, state);
  }

  @Override
  public ElementInstance newInstance(
      final ElementInstance parent,
      final long key,
      final ProcessInstanceRecord value,
      final ProcessInstanceIntent state) {

    final ElementInstance instance;
    if (parent == null) {
      instance = new ElementInstance(key, state, value);
    } else {
      instance = new ElementInstance(key, parent, state, value);
      updateInstance(parent);
    }
    updateInstance(instance);

    return instance;
  }

  @Override
  public void removeInstance(final long key) {
    final ElementInstance instance = getInstance(key);

    if (instance != null) {
      elementInstanceKey.wrapLong(key);
      parentKey.wrapLong(instance.getParentKey());

      parentChildColumnFamily.delete(parentChildKey);
      elementInstanceColumnFamily.delete(elementInstanceKey);

      recordParentChildColumnFamily.whileEqualPrefix(
          elementInstanceKey,
          (compositeKey, nil) -> {
            recordParentChildColumnFamily.delete(compositeKey);
            recordColumnFamily.delete(compositeKey.getSecond());
          });

      variableState.removeScope(key);

      awaitProcessInstanceResultMetadataColumnFamily.delete(elementInstanceKey);

      final long parentKey = instance.getParentKey();
      if (parentKey > 0) {
        final ElementInstance parentInstance = getInstance(parentKey);
        if (parentInstance == null) {
          final var errorMsg =
              "Expected to find parent instance for element instance with key %d, but none was found.";
          throw new IllegalStateException(String.format(errorMsg, parentKey));
        }
        parentInstance.decrementChildCount();
        updateInstance(parentInstance);
      }
    }
  }

  @Override
  public void updateInstance(final ElementInstance scopeInstance) {
    writeElementInstance(scopeInstance);
  }

  @Override
  public void updateInstance(final long key, final Consumer<ElementInstance> modifier) {
    final var scopeInstance = getInstance(key);
    modifier.accept(scopeInstance);
    updateInstance(scopeInstance);
  }

  @Override
  public void consumeToken(final long scopeKey) {
    final ElementInstance elementInstance = getInstance(scopeKey);
    if (elementInstance != null) {
      elementInstance.consumeToken();
      updateInstance(elementInstance);
    }
  }

  @Override
  public void spawnToken(final long scopeKey) {
    final ElementInstance elementInstance = getInstance(scopeKey);
    if (elementInstance != null) {
      elementInstance.spawnToken();
      updateInstance(elementInstance);
    }
  }

  @Override
  public void storeRecord(
      final long key,
      final long scopeKey,
      final ProcessInstanceRecord value,
      final ProcessInstanceIntent intent,
      final Purpose purpose) {
    final IndexedRecord indexedRecord = new IndexedRecord(key, intent, value);
    final StoredRecord storedRecord = new StoredRecord(indexedRecord, purpose);

    setRecordKeys(scopeKey, key, purpose);

    recordColumnFamily.put(recordKey, storedRecord);
    recordParentChildColumnFamily.put(recordParentStateRecordKey, DbNil.INSTANCE);
  }

  @Override
  public void removeStoredRecord(final long scopeKey, final long recordKey, final Purpose purpose) {
    setRecordKeys(scopeKey, recordKey, purpose);

    recordColumnFamily.delete(this.recordKey);
    recordParentChildColumnFamily.delete(recordParentStateRecordKey);
  }

  @Override
  public void setAwaitResultRequestMetadata(
      final long processInstanceKey, final AwaitProcessInstanceResultMetadata metadata) {
    elementInstanceKey.wrapLong(processInstanceKey);
    awaitProcessInstanceResultMetadataColumnFamily.put(elementInstanceKey, metadata);
  }

  private void writeElementInstance(final ElementInstance instance) {
    elementInstanceKey.wrapLong(instance.getKey());
    parentKey.wrapLong(instance.getParentKey());

    elementInstanceColumnFamily.put(elementInstanceKey, instance);
    parentChildColumnFamily.put(parentChildKey, DbNil.INSTANCE);
    variableState.createScope(elementInstanceKey.getValue(), parentKey.getValue());
  }

  @Override
  public ElementInstance getInstance(final long key) {
    elementInstanceKey.wrapLong(key);
    final ElementInstance elementInstance = elementInstanceColumnFamily.get(elementInstanceKey);
    return copyElementInstance(elementInstance);
  }

  @Override
  public StoredRecord getStoredRecord(final long recordKey) {
    this.recordKey.wrapLong(recordKey);
    return recordColumnFamily.get(this.recordKey);
  }

  @Override
  public List<ElementInstance> getChildren(final long parentKey) {
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

  @Override
  public List<IndexedRecord> getDeferredRecords(final long scopeKey) {
    return collectRecords(scopeKey, Purpose.DEFERRED);
  }

  @Override
  public IndexedRecord getFailedRecord(final long key) {
    final StoredRecord storedRecord = getStoredRecord(key);
    if (storedRecord != null && storedRecord.getPurpose() == Purpose.FAILED) {
      return storedRecord.getRecord();
    } else {
      return null;
    }
  }

  @Override
  public AwaitProcessInstanceResultMetadata getAwaitResultRequestMetadata(
      final long processInstanceKey) {
    elementInstanceKey.wrapLong(processInstanceKey);
    return awaitProcessInstanceResultMetadataColumnFamily.get(elementInstanceKey);
  }

  private void setRecordKeys(final long scopeKey, final long recordKey, final Purpose purpose) {
    recordParentKey.wrapLong(scopeKey);
    stateKey.wrapByte((byte) purpose.ordinal());
    this.recordKey.wrapLong(recordKey);
  }

  private List<IndexedRecord> collectRecords(final long scopeKey, final Purpose purpose) {
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
          final byte[] bytes = new byte[indexedRecord.getLength()];
          final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

          indexedRecord.write(buffer, 0);
          final IndexedRecord copiedRecord = new IndexedRecord();
          copiedRecord.wrap(buffer, 0, indexedRecord.getLength());

          records.add(copiedRecord);
        });
    return records;
  }

  private void visitRecords(
      final long scopeKey, final Purpose purpose, final RecordVisitor visitor) {
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

  private ElementInstance copyElementInstance(final ElementInstance elementInstance) {
    if (elementInstance != null) {
      final byte[] bytes = new byte[elementInstance.getLength()];
      final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

      elementInstance.write(buffer, 0);
      final ElementInstance copiedElementInstance = new ElementInstance();
      copiedElementInstance.wrap(buffer, 0, elementInstance.getLength());
      return copiedElementInstance;
    }
    return null;
  }

  @FunctionalInterface
  public interface RecordVisitor {

    void visitRecord(IndexedRecord indexedRecord);
  }
}
