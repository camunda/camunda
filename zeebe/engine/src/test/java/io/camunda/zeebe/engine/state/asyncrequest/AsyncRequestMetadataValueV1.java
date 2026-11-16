/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.asyncrequest;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecordV1;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.collection.Tuple;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class AsyncRequestMetadataValueV1 extends UnpackedObject
    implements DbValue,
        ProjectionOf<Tuple<AsyncRequestRecordV1, Long>, AsyncRequestMetadataValueV1> {

  private final LongProperty asyncRequestKeyProperty = new LongProperty("asyncRequestKey", -1);

  private final ObjectProperty<AsyncRequestRecordV1> recordProp =
      new ObjectProperty<>("asyncRequestRecord", new AsyncRequestRecordV1());

  public AsyncRequestMetadataValueV1() {
    super(2);
    declareProperty(asyncRequestKeyProperty).declareProperty(recordProp);
  }

  public void wrap(final long asyncRequestKey, final AsyncRequestRecordV1 record) {
    asyncRequestKeyProperty.setValue(asyncRequestKey);
    recordProp.getValue().wrap(record);
  }

  public AsyncRequestRecordV1 getRecord() {
    return recordProp.getValue();
  }

  public long getAsyncRequestKey() {
    return asyncRequestKeyProperty.getValue();
  }

  @Override
  public AsyncRequestMetadataValueV1 wrap(final Tuple<AsyncRequestRecordV1, Long> source) {
    wrap(source.getRight(), source.getLeft());
    return this;
  }
}
