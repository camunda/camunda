/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({
  /* encodedLength and empty were inherited from ObjectValue, length is inherited from
  MsgpackBufferValue; they have no purpose in exported JSON records*/
  "encodedLength",
  "empty",
  "length"
})
@MsgpackPojo
@NullMarked
public final class ProcessInstanceMigrationMappingInstruction
    extends MsgpackBufferValue<ProcessInstanceMigrationMappingInstruction>
    implements ProcessInstanceMigrationMappingInstructionValue {

  DirectBuffer sourceElementId = Decoding.EMPTY;
  DirectBuffer targetElementId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getSourceElementId() {
    return BufferUtil.bufferAsString(sourceElementId);
  }

  public ProcessInstanceMigrationMappingInstruction setSourceElementId(
      final String sourceElementId) {
    this.sourceElementId = BufferUtil.wrapString(sourceElementId);
    return this;
  }

  public ProcessInstanceMigrationMappingInstruction setSourceElementId(
      final DirectBuffer sourceElementId) {
    this.sourceElementId = sourceElementId;
    return this;
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(targetElementId);
  }

  public ProcessInstanceMigrationMappingInstruction setTargetElementId(
      final String targetElementId) {
    this.targetElementId = BufferUtil.wrapString(targetElementId);
    return this;
  }

  public ProcessInstanceMigrationMappingInstruction setTargetElementId(
      final DirectBuffer targetElementId) {
    this.targetElementId = targetElementId;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getSourceElementIdBuffer() {
    return sourceElementId;
  }

  @JsonIgnore
  public DirectBuffer getTargetElementIdBuffer() {
    return targetElementId;
  }

  public void copy(final ProcessInstanceMigrationMappingInstruction other) {
    sourceElementId = other.getSourceElementIdBuffer();
    targetElementId = other.getTargetElementIdBuffer();
  }

  @Override
  protected MsgpackCodec<ProcessInstanceMigrationMappingInstruction> codec() {
    return ProcessInstanceMigrationMappingInstructionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceMigrationMappingInstruction> ops() {
    return ProcessInstanceMigrationMappingInstructionOps.INSTANCE;
  }
}
