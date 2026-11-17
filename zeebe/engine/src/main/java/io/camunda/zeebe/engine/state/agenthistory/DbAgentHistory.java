/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agenthistory;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class DbAgentHistory extends MsgpackBufferValue<DbAgentHistory> implements DbValue {

  final AgentHistoryRecord agentHistoryRecord = new AgentHistoryRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public AgentHistoryRecord getRecord() {
    return agentHistoryRecord;
  }

  public void setRecord(final AgentHistoryRecord record) {
    agentHistoryRecord.copyFrom(record);
  }

  @Override
  protected MsgpackCodec<DbAgentHistory> codec() {
    return DbAgentHistoryCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DbAgentHistory> ops() {
    return DbAgentHistoryOps.INSTANCE;
  }
}
