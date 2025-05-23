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
import io.camunda.zeebe.engine.state.TriggeringRecordMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableTriggeringRecordMetadataState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DbTriggeringRecordMetadataState implements MutableTriggeringRecordMetadataState {

  private final DbLong eventKey;
  // probably it's not safe to use DbEnumValue for `ValueType` enum,
  // as `DbEnumValue` relies on ordinal value instead of `ValueType#value`
  private final DbEnumValue<ValueType> valueType;
  private final DbString intent;
  private final DbCompositeKey<DbCompositeKey<DbLong, DbEnumValue<ValueType>>, DbString> fullKey;
  private final DbCompositeKey<DbLong, DbEnumValue<ValueType>> eventKeyAndValueTypeCompositeKey;

  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1916
  private final TriggeringRecordMetadataValue metadataValueToRead =
      new TriggeringRecordMetadataValue();
  private final TriggeringRecordMetadataValue metadataValueToWrite =
      new TriggeringRecordMetadataValue();
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbLong, DbEnumValue<ValueType>>, DbString>,
          TriggeringRecordMetadataValue>
      metadataColumnFamily;

  public DbTriggeringRecordMetadataState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    eventKey = new DbLong();
    valueType = new DbEnumValue<>(ValueType.class);
    intent = new DbString();

    eventKeyAndValueTypeCompositeKey = new DbCompositeKey<>(eventKey, valueType);
    fullKey = new DbCompositeKey<>(eventKeyAndValueTypeCompositeKey, intent);
    metadataColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TRIGGERING_RECORD_METADATA,
            transactionContext,
            fullKey,
            metadataValueToRead);
  }

  @Override
  public void store(final long eventKey, final TriggeringRecordMetadata metadata) {
    this.eventKey.wrapLong(eventKey);
    valueType.setValue(metadata.getValueType());
    intent.wrapString(metadata.getIntent().name());

    metadataValueToWrite.wrap(metadata);
    metadataColumnFamily.insert(fullKey, metadataValueToWrite);
  }

  @Override
  public void remove(final long eventKey, final TriggeringRecordMetadata metadata) {
    remove(eventKey, metadata.getValueType(), metadata.getIntent());
  }

  @Override
  public void remove(final long eventKey, final ValueType valueType, final Intent intent) {
    this.eventKey.wrapLong(eventKey);
    this.valueType.setValue(valueType);
    this.intent.wrapString(intent.name());

    metadataColumnFamily.deleteIfExists(fullKey);
  }

  @Override
  public Optional<TriggeringRecordMetadata> findExact(
      final long eventKey, final ValueType valueType, final Intent intent) {
    this.eventKey.wrapLong(eventKey);
    this.valueType.setValue(valueType);
    this.intent.wrapString(intent.name());

    return Optional.ofNullable(metadataColumnFamily.get(fullKey))
        .map(TriggeringRecordMetadata::from);
  }

  @Override
  public Optional<TriggeringRecordMetadata> findOnly(
      final long eventKey, final ValueType valueType) {
    this.eventKey.wrapLong(eventKey);
    this.valueType.setValue(valueType);
    final List<TriggeringRecordMetadataValue> values = new ArrayList<>();

    metadataColumnFamily.whileEqualPrefix(
        this.eventKeyAndValueTypeCompositeKey,
        (key, value) -> {
          return values.add(value);
        });

    return values.stream().findFirst().map(TriggeringRecordMetadata::from);
  }
}
