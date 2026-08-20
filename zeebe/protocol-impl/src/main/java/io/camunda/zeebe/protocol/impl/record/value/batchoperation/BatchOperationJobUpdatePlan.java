/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationJobUpdatePlanValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class BatchOperationJobUpdatePlan extends ObjectValue
    implements BatchOperationJobUpdatePlanValue {

  private static final String RETRIES = "retries";
  private static final String TIMEOUT = "timeout";
  private static final String PRIORITY = "priority";

  private final IntegerProperty retriesProp = new IntegerProperty(RETRIES, 0);
  private final LongProperty timeoutProp = new LongProperty(TIMEOUT, 0L);
  private final IntegerProperty priorityProp = new IntegerProperty(PRIORITY, 0);
  private final ArrayProperty<StringValue> changedAttributesProp =
      new ArrayProperty<>("changedAttributes", StringValue::new);

  public BatchOperationJobUpdatePlan() {
    super(4);
    declareProperty(retriesProp)
        .declareProperty(timeoutProp)
        .declareProperty(priorityProp)
        .declareProperty(changedAttributesProp);
  }

  @Override
  public Integer getRetries() {
    return isChanged(RETRIES) ? retriesProp.getValue() : null;
  }

  public BatchOperationJobUpdatePlan setRetries(final Integer retries) {
    if (retries != null) {
      retriesProp.setValue(retries);
      markChanged(RETRIES);
    }
    return this;
  }

  @Override
  public Long getTimeout() {
    return isChanged(TIMEOUT) ? timeoutProp.getValue() : null;
  }

  public BatchOperationJobUpdatePlan setTimeout(final Long timeout) {
    if (timeout != null) {
      timeoutProp.setValue(timeout);
      markChanged(TIMEOUT);
    }
    return this;
  }

  @Override
  public Integer getPriority() {
    return isChanged(PRIORITY) ? priorityProp.getValue() : null;
  }

  public BatchOperationJobUpdatePlan setPriority(final Integer priority) {
    if (priority != null) {
      priorityProp.setValue(priority);
      markChanged(PRIORITY);
    }
    return this;
  }

  public BatchOperationJobUpdatePlan wrap(final BatchOperationJobUpdatePlanValue record) {
    changedAttributesProp.reset();
    retriesProp.reset();
    timeoutProp.reset();
    priorityProp.reset();
    setRetries(record.getRetries());
    setTimeout(record.getTimeout());
    setPriority(record.getPriority());
    return this;
  }

  public Set<String> getChangedAttributes() {
    return StreamSupport.stream(changedAttributesProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toSet());
  }

  private boolean isChanged(final String attribute) {
    return StreamSupport.stream(changedAttributesProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .anyMatch(attribute::equals);
  }

  private void markChanged(final String attribute) {
    if (!isChanged(attribute)) {
      changedAttributesProp.add().wrap(BufferUtil.wrapString(attribute));
    }
  }
}
