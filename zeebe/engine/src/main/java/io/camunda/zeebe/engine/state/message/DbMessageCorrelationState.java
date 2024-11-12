/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.mutable.MutableMessageCorrelationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public class DbMessageCorrelationState implements MutableMessageCorrelationState {

  private final DbLong messageKey;
  private final RequestData requestData;

  /** Message key -> Request data */
  private final ColumnFamily<DbLong, RequestData> messageCorrelationColumnFamily;

  public DbMessageCorrelationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    messageKey = new DbLong();
    requestData = new RequestData();
    messageCorrelationColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_CORRELATION, transactionContext, messageKey, requestData);
  }

  @Override
  public void removeMessageCorrelation(final long messageKey) {
    this.messageKey.wrapLong(messageKey);
    messageCorrelationColumnFamily.deleteExisting(this.messageKey);
  }

  @Override
  public void putMessageCorrelation(
      final long messageKey, final long requestId, final int requestStreamId) {
    this.messageKey.wrapLong(messageKey);
    requestData.setRequestIdProp(requestId).setRequestStreamIdProp(requestStreamId);
    messageCorrelationColumnFamily.insert(this.messageKey, requestData);
  }

  @Override
  public RequestData getRequestData(final long messageKey) {
    this.messageKey.wrapLong(messageKey);
    return messageCorrelationColumnFamily.get(this.messageKey).copy();
  }

  @Override
  public boolean existsRequestDataForMessageKey(final long messageKey) {
    this.messageKey.wrapLong(messageKey);
    return messageCorrelationColumnFamily.exists(this.messageKey);
  }
}
