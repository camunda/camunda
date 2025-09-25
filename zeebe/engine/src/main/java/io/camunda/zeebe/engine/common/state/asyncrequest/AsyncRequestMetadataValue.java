/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.asyncrequest;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;

public class AsyncRequestMetadataValue extends UnpackedObject implements DbValue {

  private final LongProperty asyncRequestKeyProperty = new LongProperty("asyncRequestKey", -1);

  private final ObjectProperty<AsyncRequestRecord> recordProp =
      new ObjectProperty<>("asyncRequestRecord", new AsyncRequestRecord());

  public AsyncRequestMetadataValue() {
    super(2);
    declareProperty(asyncRequestKeyProperty).declareProperty(recordProp);
  }

  public void wrap(final long asyncRequestKey, final AsyncRequestRecord record) {
    asyncRequestKeyProperty.setValue(asyncRequestKey);
    recordProp.getValue().wrap(record);
  }

  public AsyncRequestRecord getRecord() {
    return recordProp.getValue();
  }

  public long getAsyncRequestKey() {
    return asyncRequestKeyProperty.getValue();
  }
}
