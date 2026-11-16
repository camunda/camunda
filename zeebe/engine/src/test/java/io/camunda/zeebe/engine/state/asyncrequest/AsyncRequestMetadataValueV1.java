/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.asyncrequest;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.RecordObjectValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.collection.Tuple;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class AsyncRequestMetadataValueV1 extends UnpackedObject
    implements DbValue, ProjectionOf<Tuple<AsyncRequestRecord, Long>, AsyncRequestMetadataValueV1> {

  private final LongProperty asyncRequestKeyProperty = new LongProperty("asyncRequestKey", -1);

  private final ObjectProperty<RecordObjectValue<AsyncRequestRecord>> recordProp =
      new ObjectProperty<>("asyncRequestRecord", new RecordObjectValue<>(new AsyncRequestRecord()));

  public AsyncRequestMetadataValueV1() {
    super(2);
    declareProperty(asyncRequestKeyProperty).declareProperty(recordProp);
  }

  public void wrap(final long asyncRequestKey, final AsyncRequestRecord record) {
    asyncRequestKeyProperty.setValue(asyncRequestKey);
    recordProp.getValue().get().wrap(record);
  }

  public AsyncRequestRecord getRecord() {
    return recordProp.getValue().get();
  }

  public long getAsyncRequestKey() {
    return asyncRequestKeyProperty.getValue();
  }

  @Override
  public AsyncRequestMetadataValueV1 wrap(final Tuple<AsyncRequestRecord, Long> source) {
    wrap(source.getRight(), source.getLeft());
    return this;
  }
}
