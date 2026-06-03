/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.record.RequestSource;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RequestSourceInfo extends UnpackedObject implements RequestSource {

  private final StringProperty channelTypeProp = new StringProperty("channelType", "");
  private final StringProperty toolNameProp = new StringProperty("toolName", "");

  public RequestSourceInfo() {
    super(2);
    declareProperty(channelTypeProp);
    declareProperty(toolNameProp);
  }

  @Override
  public String getChannelType() {
    return BufferUtil.bufferAsString(channelTypeProp.getValue());
  }

  public RequestSourceInfo setChannelType(final String channelType) {
    channelTypeProp.setValue(channelType);
    return this;
  }

  @Override
  public String getToolName() {
    return BufferUtil.bufferAsString(toolNameProp.getValue());
  }

  public RequestSourceInfo setToolName(final String toolName) {
    toolNameProp.setValue(toolName);
    return this;
  }

  @Override
  public void reset() {
    channelTypeProp.setValue("");
    toolNameProp.setValue("");
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  @Override
  @JsonIgnore
  public boolean isEmpty() {
    return getChannelType().isEmpty();
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  public DirectBuffer toDirectBuffer() {
    final var bytes = new byte[getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);
    return buffer;
  }

  /**
   * Creates a non-null copy of {@code requestSource}. Returns an empty instance when {@code
   * requestSource} is {@code null} or already empty.
   */
  public static RequestSourceInfo of(final RequestSource requestSource) {
    if (requestSource == null) {
      return null;
    }
    return new RequestSourceInfo()
        .setChannelType(requestSource.getChannelType())
        .setToolName(requestSource.getToolName());
  }
}
