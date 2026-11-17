/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.error;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import org.agrona.DirectBuffer;

public final class ErrorRecord extends UnifiedRecordValue implements ErrorRecordValue {

  private static final String NULL_MESSAGE = "Without exception message.";

  // Static StringValue keys for property names
  private static final StringValue EXCEPTION_MESSAGE_KEY = new StringValue("exceptionMessage");
  private static final StringValue STACKTRACE_KEY = new StringValue("stacktrace");
  private static final StringValue ERROR_EVENT_POSITION_KEY = new StringValue("errorEventPosition");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue STORAGE_ORDINAL_KEY = new StringValue("storageOrdinal");

  private final StringProperty exceptionMessageProp = new StringProperty(EXCEPTION_MESSAGE_KEY);
  private final StringProperty stacktraceProp = new StringProperty(STACKTRACE_KEY, "");
  private final LongProperty errorEventPositionProp = new LongProperty(ERROR_EVENT_POSITION_KEY);

  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1L);

  private final IntegerProperty storageOrdinalProp = new IntegerProperty(STORAGE_ORDINAL_KEY, 0);

  public ErrorRecord() {
    super(5);
    declareProperty(exceptionMessageProp)
        .declareProperty(stacktraceProp)
        .declareProperty(errorEventPositionProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(storageOrdinalProp);
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
  public int getStorageOrdinal() {
    return storageOrdinalProp.getValue();
  }

  public ErrorRecord setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProp.setValue(storageOrdinal);
    return this;
  }
}
