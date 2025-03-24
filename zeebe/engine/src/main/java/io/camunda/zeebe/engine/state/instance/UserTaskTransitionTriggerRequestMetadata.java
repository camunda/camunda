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
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Represents metadata for tracking the request that triggered a user task transition.
 *
 * <p>This metadata is persisted to ensure that after the user task transition (e.g., assignment,
 * update, or completion) is fully processed, the original request can be properly finalized and
 * responded to.
 *
 * <p>The trigger for a user task transition can be:
 *
 * <ul>
 *   <li>A direct **user task command** (e.g., `ASSIGN`, `CLAIM`, `UPDATE`, `COMPLETE` etc.).
 *   <li>A **variable update** (`VARIABLE_DOCUMENT.UPDATE` command) that affects the user task.
 * </ul>
 *
 * <p>The metadata includes:
 *
 * <ul>
 *   <li>{@code triggerType}: The source type of the request (e.g., `USER_TASK` or
 *       `VARIABLE_DOCUMENT`).
 *   <li>{@code intent}: The intent that initiated the transition.
 *   <li>{@code requestId}: The identifier of the original request.
 *   <li>{@code requestStreamId}: The stream ID associated with the original request.
 * </ul>
 *
 * <p>This metadata is used to correctly finalize and respond to the original request once the user
 * task transition is completed.
 */
public class UserTaskTransitionTriggerRequestMetadata extends UnpackedObject implements DbValue {

  private final EnumProperty<ValueType> triggerTypeProperty =
      new EnumProperty<>("triggerType", ValueType.class);
  private final StringProperty intentProperty = new StringProperty("intent");
  private final LongProperty requestIdProperty = new LongProperty("requestId", -1);
  private final IntegerProperty requestStreamIdProperty =
      new IntegerProperty("requestStreamId", -1);

  public UserTaskTransitionTriggerRequestMetadata() {
    super(4);
    declareProperty(triggerTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(requestIdProperty)
        .declareProperty(requestStreamIdProperty);
  }

  /**
   * Returns the type of request that triggered the user task transition.
   *
   * @return The trigger type (for instance: `USER_TASK` or `VARIABLE_DOCUMENT`).
   */
  public ValueType getTriggerType() {
    return triggerTypeProperty.getValue();
  }

  /**
   * Sets the type of request that triggered the user task transition.
   *
   * @param triggerType The trigger type (`USER_TASK` or `VARIABLE_DOCUMENT`).
   * @return this metadata instance.
   */
  public UserTaskTransitionTriggerRequestMetadata setTriggerType(final ValueType triggerType) {
    triggerTypeProperty.setValue(triggerType);
    return this;
  }

  /**
   * Returns the user task command intent associated with this transition.
   *
   * @return The user task intent (e.g., `ASSIGN`, `CLAIM`, `UPDATE`, `COMPLETE` etc.).
   */
  public Intent getIntent() {
    return Intent.fromProtocolValue(
        triggerTypeProperty.getValue(), BufferUtil.bufferAsString(intentProperty.getValue()));
  }

  /**
   * Sets the intent that initiated the transition.
   *
   * @param intent The intent of the original command.
   * @return this metadata instance.
   */
  public UserTaskTransitionTriggerRequestMetadata setIntent(final Intent intent) {
    intentProperty.setValue(intent.name());
    return this;
  }

  /**
   * Returns the request ID of the original request that triggered the user task transition.
   *
   * @return The request ID.
   */
  public long getRequestId() {
    return requestIdProperty.getValue();
  }

  /**
   * Sets the request ID of the original request.
   *
   * @param requestId The request ID.
   * @return this metadata instance.
   */
  public UserTaskTransitionTriggerRequestMetadata setRequestId(final long requestId) {
    requestIdProperty.setValue(requestId);
    return this;
  }

  /**
   * Returns the request stream ID of the original request.
   *
   * @return The request stream ID.
   */
  public int getRequestStreamId() {
    return requestStreamIdProperty.getValue();
  }

  /**
   * Sets the request stream ID of the original request.
   *
   * @param requestStreamId The request stream ID.
   * @return this metadata instance.
   */
  public UserTaskTransitionTriggerRequestMetadata setRequestStreamId(final int requestStreamId) {
    requestStreamIdProperty.setValue(requestStreamId);
    return this;
  }
}
