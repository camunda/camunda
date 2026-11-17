/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessInstanceCreationRecord
    extends MsgpackBufferValue<ProcessInstanceCreationRecord>
    implements MsgpackRecordValue, ProcessInstanceCreationRecordValue {

  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  long processDefinitionKey = -1L;
  long processInstanceKey = -1L;
  int version = -1;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  final List<DirectBuffer> fetchVariables = new ArrayList<>();
  final List<ProcessInstanceCreationStartInstruction> startInstructions = new ArrayList<>();
  final List<ProcessInstanceCreationRuntimeInstruction> runtimeInstructions = new ArrayList<>();
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  final List<DirectBuffer> tags = new ArrayList<>();
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer businessId = Decoding.EMPTY;
  DirectBuffer versionTag = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessId);
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceCreationRecord setProcessDefinitionKey(final long key) {
    processDefinitionKey = key;
    return this;
  }

  @Override
  public List<ProcessInstanceCreationStartInstructionValue> getStartInstructions() {
    final var copies = new ArrayList<ProcessInstanceCreationStartInstructionValue>();
    for (final var element : startInstructions) {
      final var elementCopy = new ProcessInstanceCreationStartInstruction();
      elementCopy.copy(element);
      copies.add(elementCopy);
    }
    return copies;
  }

  @Override
  public List<ProcessInstanceCreationRuntimeInstructionValue> getRuntimeInstructions() {
    final var copies = new ArrayList<ProcessInstanceCreationRuntimeInstructionValue>();
    for (final var element : runtimeInstructions) {
      final var elementCopy = ProcessInstanceCreationRuntimeInstruction.createInstruction();
      elementCopy.copy(element);
      copies.add(elementCopy);
    }
    return copies;
  }

  @Override
  public Set<String> getTags() {
    return tags.stream().map(BufferUtil::bufferAsString).collect(Collectors.toSet());
  }

  public ProcessInstanceCreationRecord setTags(final Set<String> tags) {
    this.tags.clear();
    if (tags != null) {
      tags.forEach(tag -> this.tags.add(BufferUtil.wrapString(tag)));
    }
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public ProcessInstanceCreationRecord setRootProcessInstanceKey(
      final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public ProcessInstanceCreationRecord setBusinessId(final String businessId) {
    this.businessId = wrapString(businessId);
    return this;
  }

  public ProcessInstanceCreationRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  public ProcessInstanceCreationRecord setVersionTag(final String versionTag) {
    this.versionTag = wrapString(versionTag);
    return this;
  }

  public ProcessInstanceCreationRecord setVersion(final int version) {
    this.version = version;
    return this;
  }

  public ProcessInstanceCreationRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = wrapString(bpmnProcessId);
    return this;
  }

  public ProcessInstanceCreationRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public boolean hasBusinessId() {
    return businessId.capacity() > 0;
  }

  @JsonIgnore
  public boolean hasStartInstructions() {
    return !startInstructions.isEmpty();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public ProcessInstanceCreationRecord setProcessInstanceKey(final long instanceKey) {
    processInstanceKey = instanceKey;
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public ProcessInstanceCreationRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  public List<DirectBuffer> fetchVariables() {
    return fetchVariables;
  }

  public ProcessInstanceCreationRecord setFetchVariables(final List<String> fetchVariables) {
    fetchVariables.forEach(variable -> this.fetchVariables.add(wrapString(variable)));
    return this;
  }

  public List<ProcessInstanceCreationStartInstruction> startInstructions() {
    return startInstructions;
  }

  public List<ProcessInstanceCreationRuntimeInstruction> runtimeInstructions() {
    return runtimeInstructions;
  }

  public ProcessInstanceCreationRecord addStartInstructions(
      final List<ProcessInstanceCreationStartInstruction> startInstructions) {
    startInstructions.forEach(this::addStartInstruction);
    return this;
  }

  public ProcessInstanceCreationRecord addStartInstruction(
      final ProcessInstanceCreationStartInstruction startInstruction) {
    final var copy = new ProcessInstanceCreationStartInstruction();
    copy.copy(startInstruction);
    startInstructions.add(copy);
    return this;
  }

  public ProcessInstanceCreationRecord addRuntimeInstructions(
      final List<ProcessInstanceCreationRuntimeInstruction> runtimeInstructions) {
    if (runtimeInstructions == null) {
      this.runtimeInstructions.clear();
      return this;
    }
    runtimeInstructions.forEach(this::addRuntimeInstruction);
    return this;
  }

  public ProcessInstanceCreationRecord addRuntimeInstruction(
      final ProcessInstanceCreationRuntimeInstruction runtimeInstruction) {
    final var copy = new ProcessInstanceCreationRuntimeInstruction();
    copy.copy(runtimeInstruction);
    runtimeInstructions.add(copy);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ProcessInstanceCreationRecord setTenantId(final String tenantId) {
    this.tenantId = wrapString(tenantId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ProcessInstanceCreationRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessInstanceCreationRecord> codec() {
    return ProcessInstanceCreationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceCreationRecord> ops() {
    return ProcessInstanceCreationRecordOps.INSTANCE;
  }
}
