/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.error;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import org.agrona.DirectBuffer;

public final class ErrorRecord extends UnifiedRecordValue implements ErrorRecordValue {

  private static final String NULL_MESSAGE = "Without exception message.";

  private final StringProperty exceptionMessageProp = new StringProperty("exceptionMessage");
  private final StringProperty stacktraceProp = new StringProperty("stacktrace", "");
  private final LongProperty errorEventPositionProp = new LongProperty("errorEventPosition");

  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");

  public ErrorRecord() {
    declareProperty(exceptionMessageProp)
        .declareProperty(stacktraceProp)
        .declareProperty(errorEventPositionProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(tenantIdProp);
  }

  public void initErrorRecord(final Throwable throwable, final long position) {
    Objects.requireNonNull(throwable);
    reset();

    final StringWriter stringWriter = new StringWriter();
    final PrintWriter pw = new PrintWriter(stringWriter);
    throwable.printStackTrace(pw);

    stacktraceProp.setValue(stringWriter.toString());
    final String exceptionMessage = throwable.getMessage();
    exceptionMessageProp.setValue(exceptionMessage == null ? NULL_MESSAGE : exceptionMessage);
    errorEventPositionProp.setValue(position);
  }

  @JsonIgnore
  public DirectBuffer getExceptionMessageBuffer() {
    return exceptionMessageProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getStacktraceBuffer() {
    return stacktraceProp.getValue();
  }

  @Override
  public String getExceptionMessage() {
    return BufferUtil.bufferAsString(exceptionMessageProp.getValue());
  }

  @Override
  public String getStacktrace() {
    return BufferUtil.bufferAsString(stacktraceProp.getValue());
  }

  @Override
  public long getErrorEventPosition() {
    return errorEventPositionProp.getValue();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public ErrorRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }
}
