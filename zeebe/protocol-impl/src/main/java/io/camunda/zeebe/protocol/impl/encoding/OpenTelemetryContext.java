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
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.opentelemetry.context.Context;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class OpenTelemetryContext extends UnpackedObject {
  private final BinaryProperty contextProp = new BinaryProperty("context");

  public OpenTelemetryContext() {
    super(1);
    declareProperty(contextProp);
  }

  public DirectBuffer getContextBuffer() {
    return contextProp.getValue();
  }

  public Context getContext() {
    return MsgPackConverter.convertToObject(contextProp.getValue(), Context.class);
  }

  public OpenTelemetryContext setContext(final Context context) {
    contextProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(context)));
    return this;
  }

  @Override
  public void reset() {
    contextProp.setValue(new UnsafeBuffer());
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  @Override
  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty();
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
}
