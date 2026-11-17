/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.error;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class ErrorRecord extends MsgpackBufferValue<ErrorRecord>
    implements MsgpackRecordValue, ErrorRecordValue {

  private static final String NULL_MESSAGE = "Without exception message.";

  @Nullable DirectBuffer exceptionMessage;
  DirectBuffer stacktrace = Decoding.EMPTY;
  final RequiredLong errorEventPosition = new RequiredLong();
  long processInstanceKey = -1L;
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void initErrorRecord(final Throwable throwable, final long position) {
    Objects.requireNonNull(throwable);
    reset();

    final StringWriter stringWriter = new StringWriter();
    final PrintWriter pw = new PrintWriter(stringWriter);
    throwable.printStackTrace(pw);

    stacktrace = BufferUtil.wrapString(stringWriter.toString());
    final String message = throwable.getMessage();
    exceptionMessage = BufferUtil.wrapString(message == null ? NULL_MESSAGE : message);
    errorEventPosition.set(position);
  }

  @JsonIgnore
  public DirectBuffer getExceptionMessageBuffer() {
    return Decoding.required(exceptionMessage, "exceptionMessage");
  }

  @JsonIgnore
  public DirectBuffer getStacktraceBuffer() {
    return stacktrace;
  }

  @Override
  public String getExceptionMessage() {
    return BufferUtil.bufferAsString(Decoding.required(exceptionMessage, "exceptionMessage"));
  }

  @Override
  public String getStacktrace() {
    return BufferUtil.bufferAsString(stacktrace);
  }

  @Override
  public long getErrorEventPosition() {
    return errorEventPosition.get();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public ErrorRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ErrorRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ErrorRecord> codec() {
    return ErrorRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ErrorRecord> ops() {
    return ErrorRecordOps.INSTANCE;
  }
}
