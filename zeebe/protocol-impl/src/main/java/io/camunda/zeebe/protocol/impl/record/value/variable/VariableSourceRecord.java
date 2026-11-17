/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableSourceValue;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class VariableSourceRecord extends MsgpackBufferValue<VariableSourceRecord>
    implements MsgpackRecordValue, VariableSourceValue {

  //  public static final VariableSourceRecord API =
  //      new VariableSourceRecord().setType(VariableOperationType.API);
  //  public static final VariableSourceRecord NONE =
  //      new VariableSourceRecord().setType(VariableOperationType.UNKNOWN);

  // possible enhancements documented in https://github.com/camunda/camunda/issues/46970
  VariableOperationType type = VariableOperationType.UNKNOWN;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public VariableOperationType getType() {
    return type;
  }

  public VariableSourceRecord setType(final VariableOperationType variableSourceType) {
    type = variableSourceType;
    return this;
  }

  public void wrap(final VariableSourceRecord variableSourceRecord) {
    setType(variableSourceRecord.getType());
  }

  public static VariableSourceRecord api() {
    return new VariableSourceRecord().setType(VariableOperationType.API);
  }

  public static VariableSourceRecord userTaskCompletion() {
    return new VariableSourceRecord().setType(VariableOperationType.USER_TASK_COMPLETION);
  }

  public static VariableSourceRecord none() {
    return new VariableSourceRecord().setType(VariableOperationType.UNKNOWN);
  }

  @Override
  protected MsgpackCodec<VariableSourceRecord> codec() {
    return VariableSourceRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<VariableSourceRecord> ops() {
    return VariableSourceRecordOps.INSTANCE;
  }
}
