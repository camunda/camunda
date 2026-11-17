/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationJobUpdatePlanValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties("length")
public final class BatchOperationJobUpdatePlan
    extends MsgpackBufferValue<BatchOperationJobUpdatePlan>
    implements BatchOperationJobUpdatePlanValue {

  private static final String RETRIES = "retries";
  private static final String TIMEOUT = "timeout";
  private static final String PRIORITY = "priority";

  int retries = 0;
  long timeout = 0L;
  int priority = 0;
  final List<DirectBuffer> changedAttributes = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  // the value interface is not @Nullable, but an unchanged value is null
  @Override
  @SuppressWarnings("NullAway")
  public Integer getRetries() {
    return isChanged(RETRIES) ? retries : null;
  }

  public BatchOperationJobUpdatePlan setRetries(final Integer retries) {
    if (retries != null) {
      this.retries = retries;
      markChanged(RETRIES);
    }
    return this;
  }

  // the value interface is not @Nullable, but an unchanged value is null
  @Override
  @SuppressWarnings("NullAway")
  public Long getTimeout() {
    return isChanged(TIMEOUT) ? timeout : null;
  }

  public BatchOperationJobUpdatePlan setTimeout(final Long timeout) {
    if (timeout != null) {
      this.timeout = timeout;
      markChanged(TIMEOUT);
    }
    return this;
  }

  // the value interface is not @Nullable, but an unchanged value is null
  @Override
  @SuppressWarnings("NullAway")
  public Integer getPriority() {
    return isChanged(PRIORITY) ? priority : null;
  }

  public BatchOperationJobUpdatePlan setPriority(final Integer priority) {
    if (priority != null) {
      this.priority = priority;
      markChanged(PRIORITY);
    }
    return this;
  }

  public BatchOperationJobUpdatePlan wrap(final BatchOperationJobUpdatePlanValue record) {
    changedAttributes.clear();
    retries = 0;
    timeout = 0L;
    priority = 0;
    setRetries(record.getRetries());
    setTimeout(record.getTimeout());
    setPriority(record.getPriority());
    return this;
  }

  public Set<String> getChangedAttributes() {
    return changedAttributes.stream().map(BufferUtil::bufferAsString).collect(Collectors.toSet());
  }

  /** Kept so that the exported JSON stays unchanged. */
  public boolean isEmpty() {
    return false;
  }

  /** Kept so that the exported JSON stays unchanged. */
  public int getEncodedLength() {
    return getLength();
  }

  private boolean isChanged(final String attribute) {
    return changedAttributes.stream().map(BufferUtil::bufferAsString).anyMatch(attribute::equals);
  }

  private void markChanged(final String attribute) {
    if (!isChanged(attribute)) {
      changedAttributes.add(BufferUtil.wrapString(attribute));
    }
  }

  @Override
  protected MsgpackCodec<BatchOperationJobUpdatePlan> codec() {
    return BatchOperationJobUpdatePlanCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationJobUpdatePlan> ops() {
    return BatchOperationJobUpdatePlanOps.INSTANCE;
  }
}
