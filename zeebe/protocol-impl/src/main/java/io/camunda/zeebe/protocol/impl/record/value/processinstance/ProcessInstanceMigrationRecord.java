/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessInstanceMigrationRecord
    extends MsgpackBufferValue<ProcessInstanceMigrationRecord>
    implements MsgpackRecordValue, ProcessInstanceMigrationRecordValue {

  public static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  final RequiredLong processInstanceKey = new RequiredLong();
  final RequiredLong targetProcessDefinitionKey = new RequiredLong();
  final List<ProcessInstanceMigrationMappingInstruction> mappingInstructions = new ArrayList<>();
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  long processDefinitionKey = -1L;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey.get();
  }

  public ProcessInstanceMigrationRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey.set(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceMigrationRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public long getTargetProcessDefinitionKey() {
    return targetProcessDefinitionKey.get();
  }

  public ProcessInstanceMigrationRecord setTargetProcessDefinitionKey(
      final long targetProcessDefinitionKey) {
    this.targetProcessDefinitionKey.set(targetProcessDefinitionKey);
    return this;
  }

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasMappingInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceMigrationMappingInstructionValue> getMappingInstructions() {
    return mappingInstructions.stream()
        .map(
            element -> {
              final var elementCopy = new ProcessInstanceMigrationMappingInstruction();
              elementCopy.copy(element);
              return (ProcessInstanceMigrationMappingInstructionValue) elementCopy;
            })
        .toList();
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public ProcessInstanceMigrationRecord setRootProcessInstanceKey(
      final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  public ProcessInstanceMigrationRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  /** Returns true if this record has mapping instructions, otherwise false. */
  @JsonIgnore
  public boolean hasMappingInstructions() {
    return !mappingInstructions.isEmpty();
  }

  public ProcessInstanceMigrationRecord addMappingInstruction(
      final ProcessInstanceMigrationMappingInstruction mappingInstruction) {
    final var instruction = new ProcessInstanceMigrationMappingInstruction();
    instruction.copy(mappingInstruction);
    mappingInstructions.add(instruction);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ProcessInstanceMigrationRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ProcessInstanceMigrationRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessInstanceMigrationRecord> codec() {
    return ProcessInstanceMigrationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceMigrationRecord> ops() {
    return ProcessInstanceMigrationRecordOps.INSTANCE;
  }
}
