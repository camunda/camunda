/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processdefinition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties({
  /* encodedLength and empty were inherited from ObjectValue, length is inherited from
  MsgpackBufferValue; they have no purpose in exported JSON records*/
  "encodedLength",
  "empty",
  "length"
})
@MsgpackPojo
@NullMarked
public final class ProcessDefinitionSuspensionResolvedVersion
    extends MsgpackBufferValue<ProcessDefinitionSuspensionResolvedVersion>
    implements ProcessDefinitionSuspensionResolvedVersionValue {

  long processDefinitionKey = -1L;
  int version = -1;
  @Nullable ProcessDefinitionSuspensionOutcome outcome;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessDefinitionSuspensionResolvedVersion setProcessDefinitionKey(
      final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public ProcessDefinitionSuspensionResolvedVersion setVersion(final int version) {
    this.version = version;
    return this;
  }

  @Override
  public ProcessDefinitionSuspensionOutcome getOutcome() {
    return Decoding.required(outcome, "outcome");
  }

  public ProcessDefinitionSuspensionResolvedVersion setOutcome(
      final ProcessDefinitionSuspensionOutcome outcome) {
    this.outcome = outcome;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessDefinitionSuspensionResolvedVersion> codec() {
    return ProcessDefinitionSuspensionResolvedVersionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessDefinitionSuspensionResolvedVersion> ops() {
    return ProcessDefinitionSuspensionResolvedVersionOps.INSTANCE;
  }
}
