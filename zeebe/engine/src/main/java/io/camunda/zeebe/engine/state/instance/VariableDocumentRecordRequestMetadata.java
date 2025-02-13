/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;

/**
 * This class represents the metadata needed to properly finalize the original user task command
 * after task listener execution.
 *
 * <p>It is used to persist the original request's metadata fields such as:
 *
 * <ul>
 *   <li>{@code requestId}: The identifier of the original request that triggered the user task
 *       completion.
 *   <li>{@code requestStreamId}: The stream ID associated with the original request.
 *   <li>{@code intent}: The original command (e.g., {@code COMPLETE} user task command).
 * </ul>
 *
 * <p>This metadata is crucial for ensuring that after all task listener jobs are processed, the
 * engine can respond appropriately to the original command request.
 */
public class VariableDocumentRecordRequestMetadata extends UnpackedObject
    implements DbValue, RecordRequestMetadata {

  private final EnumProperty<VariableDocumentIntent> intentProperty =
      new EnumProperty<>("intent", VariableDocumentIntent.class);
  private final LongProperty requestIdProperty = new LongProperty("requestId", -1);
  private final IntegerProperty requestStreamIdProperty =
      new IntegerProperty("requestStreamId", -1);

  public VariableDocumentRecordRequestMetadata() {
    super(3);
    declareProperty(intentProperty)
        .declareProperty(requestIdProperty)
        .declareProperty(requestStreamIdProperty);
  }

  public VariableDocumentIntent getIntent() {
    return intentProperty.getValue();
  }

  public VariableDocumentRecordRequestMetadata setIntent(final VariableDocumentIntent intent) {
    intentProperty.setValue(intent);
    return this;
  }

  @Override
  public long getRequestId() {
    return requestIdProperty.getValue();
  }

  public VariableDocumentRecordRequestMetadata setRequestId(final long requestId) {
    requestIdProperty.setValue(requestId);
    return this;
  }

  @Override
  public int getRequestStreamId() {
    return requestStreamIdProperty.getValue();
  }

  @Override
  public ValueType responseType() {
    return ValueType.VARIABLE_DOCUMENT;
  }

  public VariableDocumentRecordRequestMetadata setRequestStreamId(final int requestStreamId) {
    requestStreamIdProperty.setValue(requestStreamId);
    return this;
  }
}
