/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobbatch;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;

public final class PendingJobBatchDelivery extends UnpackedObject implements DbValue {

  private static final StringValue TYPE_KEY = new StringValue("type");
  private static final StringValue DELIVERY_DEADLINE_KEY = new StringValue("deliveryDeadline");
  private static final StringValue JOB_KEYS_KEY = new StringValue("jobKeys");

  private final StringProperty typeProp = new StringProperty(TYPE_KEY, "");
  private final LongProperty deliveryDeadlineProp = new LongProperty(DELIVERY_DEADLINE_KEY, 0L);
  private final ArrayProperty<LongValue> jobKeysProp =
      new ArrayProperty<>(JOB_KEYS_KEY, LongValue::new);

  public PendingJobBatchDelivery() {
    super(3);
    declareProperty(typeProp).declareProperty(deliveryDeadlineProp).declareProperty(jobKeysProp);
  }

  public PendingJobBatchDelivery wrap(
      final String type, final long deliveryDeadline, final List<Long> jobKeys) {
    typeProp.setValue(type);
    deliveryDeadlineProp.setValue(deliveryDeadline);
    jobKeysProp.reset();
    for (final long jobKey : jobKeys) {
      jobKeysProp.add().setValue(jobKey);
    }
    return this;
  }

  public String getType() {
    return BufferUtil.bufferAsString(typeProp.getValue());
  }

  public DirectBuffer getTypeBuffer() {
    return typeProp.getValue();
  }

  public long getDeliveryDeadline() {
    return deliveryDeadlineProp.getValue();
  }

  public List<Long> getJobKeys() {
    final var keys = new ArrayList<Long>();
    for (final LongValue jobKey : jobKeysProp) {
      keys.add(jobKey.getValue());
    }
    return keys;
  }
}
