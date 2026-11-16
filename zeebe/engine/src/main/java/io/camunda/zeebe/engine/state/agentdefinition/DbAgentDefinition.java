/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agentdefinition;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecord;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class DbAgentDefinition extends MsgpackBufferValue<DbAgentDefinition>
    implements DbValue {

  final AgentDefinitionRecord agentDefinitionRecord = new AgentDefinitionRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public AgentDefinitionRecord getRecord() {
    return agentDefinitionRecord;
  }

  public void setRecord(final AgentDefinitionRecord record) {
    agentDefinitionRecord.copyFrom(record);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] bytes = new byte[length];
    final UnsafeBuffer mutableBuffer = new UnsafeBuffer(bytes);
    buffer.getBytes(offset, bytes, 0, length);
    super.wrap(mutableBuffer, 0, length);
  }

  @Override
  protected MsgpackCodec<DbAgentDefinition> codec() {
    return DbAgentDefinitionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DbAgentDefinition> ops() {
    return DbAgentDefinitionOps.INSTANCE;
  }
}
