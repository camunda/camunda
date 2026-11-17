/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processdefinition;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessDefinitionSuspensionRecord
    extends MsgpackBufferValue<ProcessDefinitionSuspensionRecord>
    implements MsgpackRecordValue, ProcessDefinitionSuspensionRecordValue {

  long processDefinitionKey = -1L;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  int version = -1;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  boolean includeProcessInstances = false;
  final List<ProcessDefinitionSuspensionResolvedVersion> resolvedVersions = new ArrayList<>();
  long batchOperationKey = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessDefinitionSuspensionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKey = key;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessId);
  }

  public ProcessDefinitionSuspensionRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public ProcessDefinitionSuspensionRecord setVersion(final int version) {
    this.version = version;
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ProcessDefinitionSuspensionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public boolean isIncludeProcessInstances() {
    return includeProcessInstances;
  }

  public ProcessDefinitionSuspensionRecord setIncludeProcessInstances(
      final boolean includeProcessInstances) {
    this.includeProcessInstances = includeProcessInstances;
    return this;
  }

  @Override
  public List<ProcessDefinitionSuspensionResolvedVersionValue> getResolvedVersions() {
    return resolvedVersions.stream().collect(Collectors.toList());
  }

  public ProcessDefinitionSuspensionRecord addResolvedVersion(
      final ProcessDefinitionSuspensionResolvedVersionValue resolvedVersion) {
    final var added = new ProcessDefinitionSuspensionResolvedVersion();
    added
        .setProcessDefinitionKey(resolvedVersion.getProcessDefinitionKey())
        .setVersion(resolvedVersion.getVersion())
        .setOutcome(resolvedVersion.getOutcome());
    resolvedVersions.add(added);
    return this;
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKey;
  }

  public ProcessDefinitionSuspensionRecord setBatchOperationKey(final long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessDefinitionSuspensionRecord> codec() {
    return ProcessDefinitionSuspensionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessDefinitionSuspensionRecord> ops() {
    return ProcessDefinitionSuspensionRecordOps.INSTANCE;
  }
}
