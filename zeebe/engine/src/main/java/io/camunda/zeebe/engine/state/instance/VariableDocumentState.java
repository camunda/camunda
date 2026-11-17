/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class VariableDocumentState extends MsgpackBufferValue<VariableDocumentState>
    implements DbValue {

  long variableDocumentKey = -1L;
  final VariableDocumentRecord variableDocumentRecord = new VariableDocumentRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getKey() {
    return variableDocumentKey;
  }

  public VariableDocumentState setKey(final long variableDocumentKey) {
    this.variableDocumentKey = variableDocumentKey;
    return this;
  }

  public VariableDocumentRecord getRecord() {
    return variableDocumentRecord;
  }

  public VariableDocumentState setRecord(final VariableDocumentRecord record) {
    variableDocumentRecord.wrap(record);
    return this;
  }

  @Override
  protected MsgpackCodec<VariableDocumentState> codec() {
    return VariableDocumentStateCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<VariableDocumentState> ops() {
    return VariableDocumentStateOps.INSTANCE;
  }
}
