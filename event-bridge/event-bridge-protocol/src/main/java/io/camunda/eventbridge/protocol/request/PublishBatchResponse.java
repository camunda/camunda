/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.eventbridge.protocol.PublishResponseStatus;
import io.camunda.eventbridge.protocol.RejectionReason;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

public class PublishBatchResponse extends UnpackedObject {

  private final EnumProperty<PublishResponseStatus> statusProp =
      new EnumProperty<>("status", PublishResponseStatus.class, PublishResponseStatus.NULL_VAL);
  private final EnumProperty<RejectionReason> rejectionReasonProp =
      new EnumProperty<>("rejectionReason", RejectionReason.class, RejectionReason.UNKNOWN);
  private final StringProperty rejectionMessageProp = new StringProperty("rejectionMessage", "");
  private final LongProperty firstPositionProp = new LongProperty("firstPosition", -1L);
  private final LongProperty lastPositionProp = new LongProperty("lastPosition", -1L);

  public PublishBatchResponse() {
    super(5);
    declareProperty(statusProp)
        .declareProperty(rejectionReasonProp)
        .declareProperty(rejectionMessageProp)
        .declareProperty(firstPositionProp)
        .declareProperty(lastPositionProp);
  }

  public PublishResponseStatus getStatus() {
    return statusProp.getValue();
  }

  public PublishBatchResponse setStatus(final PublishResponseStatus status) {
    statusProp.setValue(status);
    return this;
  }

  public RejectionReason getRejectionReason() {
    return rejectionReasonProp.getValue();
  }

  public PublishBatchResponse setRejectionReason(final RejectionReason rejectionReason) {
    rejectionReasonProp.setValue(rejectionReason);
    return this;
  }

  public String getRejectionMessage() {
    return bufferAsString(rejectionMessageProp.getValue());
  }

  public PublishBatchResponse setRejectionMessage(final String rejectionMessage) {
    rejectionMessageProp.setValue(rejectionMessage);
    return this;
  }

  public long getFirstPosition() {
    return firstPositionProp.getValue();
  }

  public PublishBatchResponse setFirstPosition(final long firstPosition) {
    firstPositionProp.setValue(firstPosition);
    return this;
  }

  public long getLastPosition() {
    return lastPositionProp.getValue();
  }

  public PublishBatchResponse setLastPosition(final long lastPosition) {
    lastPositionProp.setValue(lastPosition);
    return this;
  }

  public void wrapPublishResponse(final PublishBatchResponse response) {
    reset();
    setRejectionMessage(response.getRejectionMessage())
        .setRejectionReason(response.getRejectionReason())
        .setStatus(response.getStatus())
        .setFirstPosition(response.getFirstPosition())
        .setLastPosition(response.getLastPosition());
  }
}
