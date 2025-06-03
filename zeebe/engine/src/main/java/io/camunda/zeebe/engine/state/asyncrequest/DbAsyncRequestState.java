/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.asyncrequest;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableAsyncRequestState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;

public class DbAsyncRequestState implements MutableAsyncRequestState {

  private final DbLong scopeKey;
  private final AsyncRequestMetadataKey asyncRequestMetadataKey;

  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1916
  private final AsyncRequestMetadataValue asyncRequestMetadataValueToRead =
      new AsyncRequestMetadataValue();
  private final AsyncRequestMetadataValue asyncRequestMetadataValueToWrite =
      new AsyncRequestMetadataValue();
  private final ColumnFamily<AsyncRequestMetadataKey, AsyncRequestMetadataValue>
      asyncRequestMetadataColumnFamily;

  public DbAsyncRequestState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    scopeKey = new DbLong();
    asyncRequestMetadataKey = new AsyncRequestMetadataKey();
    asyncRequestMetadataColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ASYNC_REQUEST_METADATA,
            transactionContext,
            asyncRequestMetadataKey,
            asyncRequestMetadataValueToRead);
  }

  @Override
  public void storeRequest(final long asyncRequestKey, final AsyncRequestRecord record) {
    asyncRequestMetadataKey.setAll(record);
    asyncRequestMetadataValueToWrite.wrap(asyncRequestKey, record);

    asyncRequestMetadataColumnFamily.insert(
        asyncRequestMetadataKey, asyncRequestMetadataValueToWrite);
  }

  @Override
  public void deleteRequest(final AsyncRequestRecord record) {
    deleteRequest(record.getScopeKey(), record.getValueType(), record.getIntent());
  }

  @Override
  public void deleteRequest(final long scopeKey, final ValueType valueType, final Intent intent) {
    asyncRequestMetadataKey.setAll(scopeKey, valueType, intent);

    asyncRequestMetadataColumnFamily.deleteIfExists(asyncRequestMetadataKey);
  }

  @Override
  public Optional<AsyncRequest> findRequest(
      final long scopeKey, final ValueType valueType, final Intent intent) {
    asyncRequestMetadataKey.setAll(scopeKey, valueType, intent);

    return Optional.ofNullable(asyncRequestMetadataColumnFamily.get(asyncRequestMetadataKey))
        .map(AsyncRequest::new);
  }

  @Override
  public Stream<AsyncRequest> findAllRequestsByScopeKey(final long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);

    final var values = new ArrayList<AsyncRequestMetadataValue>();
    asyncRequestMetadataColumnFamily.whileEqualPrefix(
        this.scopeKey,
        (key, value) -> {
          values.add(value);
        });

    return values.stream().map(AsyncRequest::new);
  }

  private static final class ScopeKeyAndValueTypeValue
      extends DbCompositeKey<DbLong, DbEnumValue<ValueType>> {

    public ScopeKeyAndValueTypeValue() {
      super(new DbLong(), new DbEnumValue<>(ValueType.class));
    }

    public void setAll(final long scopeKey, final ValueType valueType) {
      first().wrapLong(scopeKey);
      second().setValue(valueType);
    }

    public long scopeKey() {
      return first().getValue();
    }

    public ValueType valueType() {
      return second().getValue();
    }
  }

  private static final class AsyncRequestMetadataKey
      extends DbCompositeKey<ScopeKeyAndValueTypeValue, DbString> {

    public AsyncRequestMetadataKey() {
      super(new ScopeKeyAndValueTypeValue(), new DbString());
    }

    public void setAll(final AsyncRequestRecord record) {
      setAll(record.getScopeKey(), record.getValueType(), record.getIntent());
    }

    public void setAll(final long scopeKey, final ValueType valueType, final Intent intent) {
      first().setAll(scopeKey, valueType);
      second().wrapString(intent.name());
    }

    public long scopeKey() {
      return first().scopeKey();
    }

    public ValueType valueType() {
      return first().valueType();
    }

    public Intent intent() {
      return Intent.fromProtocolValue(valueType(), second().toString());
    }

    @Override
    public String toString() {
      return "AsyncRequestMetadataKey{"
          + "scopeKey= "
          + scopeKey()
          + ", valueType= "
          + valueType()
          + ", intent= "
          + intent()
          + '}';
    }
  }
}
