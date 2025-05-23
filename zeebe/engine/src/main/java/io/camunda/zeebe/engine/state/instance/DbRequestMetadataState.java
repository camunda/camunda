/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableRequestMetadataState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.RequestMetadataRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class DbRequestMetadataState implements MutableRequestMetadataState {

  private final DbLong scopeKey;
  private final DbEnumValue<ValueType> valueType;
  private final DbString triggeringIntent;
  private final DbCompositeKey<DbCompositeKey<DbLong, DbEnumValue<ValueType>>, DbString>
      compositeKey;

  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1916
  private final RequestMetadataRecordValue requestMetadataRecordToRead =
      new RequestMetadataRecordValue();
  private final RequestMetadataRecordValue requestMetadataRecordToWrite =
      new RequestMetadataRecordValue();
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbLong, DbEnumValue<ValueType>>, DbString>,
          RequestMetadataRecordValue>
      requestMetadataColumnFamily;

  public DbRequestMetadataState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    scopeKey = new DbLong();
    valueType = new DbEnumValue<>(ValueType.class);
    triggeringIntent = new DbString();

    compositeKey =
        new DbCompositeKey<>(new DbCompositeKey<>(scopeKey, valueType), triggeringIntent);
    requestMetadataColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.REQUEST_METADATA,
            transactionContext,
            compositeKey,
            requestMetadataRecordToRead);
  }

  @Override
  public void store(final RequestMetadataRecord metadataRecord) {
    scopeKey.wrapLong(metadataRecord.getScopeKey());
    valueType.setValue(metadataRecord.getValueType());
    triggeringIntent.wrapString(metadataRecord.getIntent().name());

    requestMetadataRecordToWrite.setRecord(metadataRecord);
    requestMetadataColumnFamily.insert(compositeKey, requestMetadataRecordToWrite);
  }

  @Override
  public void remove(final RequestMetadataRecord metadataRecord) {
    remove(metadataRecord.getScopeKey(), metadataRecord.getValueType(), metadataRecord.getIntent());
  }

  @Override
  public void remove(final long scopeKey, final ValueType valueType, final Intent intent) {
    this.scopeKey.wrapLong(scopeKey);
    this.valueType.setValue(valueType);
    this.triggeringIntent.wrapString(intent.name());

    requestMetadataColumnFamily.deleteExisting(compositeKey);
  }

  @Override
  public Optional<RequestMetadata> find(
      final long scopeKey, final ValueType valueType, final Intent triggeringIntent) {
    this.scopeKey.wrapLong(scopeKey);
    this.valueType.setValue(valueType);
    this.triggeringIntent.wrapString(triggeringIntent.name());

    return Optional.ofNullable(requestMetadataColumnFamily.get(compositeKey))
        .map(RequestMetadataRecordValue::getRecord)
        .map(RequestMetadata::new);
  }

  @Override
  public Stream<RequestMetadata> findAllByScopeKey(final long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);
    final List<RequestMetadataRecord> records = new ArrayList<>();

    requestMetadataColumnFamily.whileEqualPrefix(
        this.scopeKey,
        (key, value) -> {
          records.add(value.getRecord());
        });

    return records.stream().map(RequestMetadata::new);
  }
}
