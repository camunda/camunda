/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentdefinition;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class AgentDefinitionRecord extends MsgpackBufferValue<AgentDefinitionRecord>
    implements MsgpackRecordValue, AgentDefinitionRecordValue {

  long agentDefinitionKey = -1L;
  AgentDefinitionType agentType = AgentDefinitionType.UNSPECIFIED;
  DirectBuffer name = Decoding.EMPTY;
  DirectBuffer elementId = Decoding.EMPTY;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  long processDefinitionKey = -1L;
  int processDefinitionVersion = -1;
  DirectBuffer processDefinitionVersionTag = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getAgentDefinitionKey() {
    return agentDefinitionKey;
  }

  public AgentDefinitionRecord setAgentDefinitionKey(final long agentDefinitionKey) {
    this.agentDefinitionKey = agentDefinitionKey;
    return this;
  }

  @Override
  public AgentDefinitionType getAgentType() {
    return agentType;
  }

  public AgentDefinitionRecord setAgentType(final AgentDefinitionType agentType) {
    this.agentType = agentType;
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(name);
  }

  public AgentDefinitionRecord setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementId);
  }

  public AgentDefinitionRecord setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessId);
  }

  public AgentDefinitionRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public AgentDefinitionRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public AgentDefinitionRecord setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return BufferUtil.bufferAsString(processDefinitionVersionTag);
  }

  public AgentDefinitionRecord setProcessDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    this.processDefinitionVersionTag = BufferUtil.wrapString(processDefinitionVersionTag);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public AgentDefinitionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<AgentDefinitionRecord> codec() {
    return AgentDefinitionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentDefinitionRecord> ops() {
    return AgentDefinitionRecordOps.INSTANCE;
  }
}
