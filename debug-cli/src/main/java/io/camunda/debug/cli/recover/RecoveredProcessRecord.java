/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.recover;

import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A synthetic {@link Record} of a process deployment, reconstructed from a {@link PersistedProcess}
 * read out of primary storage (RocksDB). It carries a {@link ProcessIntent#CREATED} intent and a
 * {@link ProcessRecord} value so that it can drive the real {@code ProcessHandler} and {@code
 * EmbeddedFormHandler} exactly as a freshly-exported deployment would. This guarantees the
 * recovered secondary-storage documents are identical to normally-exported ones and inherits any
 * future handler changes for free.
 *
 * <p>Only {@link #getIntent()} and {@link #getValue()} are consumed by those handlers; the
 * remaining record metadata is not persisted by them and therefore throw
 * UnsupportedOperationException if accessed. It emulates an event record, but position, timestamp
 * and similar metadata are not recoverable — they were never persisted in primary storage and any
 * original record has long been compacted.
 */
final class RecoveredProcessRecord implements Record<Process> {

  private final ProcessRecord value;

  private RecoveredProcessRecord(final ProcessRecord value) {
    this.value = value;
  }

  /**
   * Reconstructs a synthetic {@code CREATED} process record from a persisted process. The resource
   * bytes are copied into a fresh buffer so the record stays valid after the (reused) {@link
   * PersistedProcess} instance is advanced to the next entry during iteration.
   */
  static RecoveredProcessRecord from(final PersistedProcess process) {
    final var record =
        new ProcessRecord()
            .setKey(process.getKey())
            .setBpmnProcessId(BufferUtil.cloneBuffer(process.getBpmnProcessId()))
            .setResourceName(BufferUtil.cloneBuffer(process.getResourceName()))
            .setResource(new UnsafeBuffer(BufferUtil.bufferAsArray(process.getResource())))
            .setVersion(process.getVersion())
            .setVersionTag(process.getVersionTag())
            .setTenantId(process.getTenantId())
            .setDeploymentKey(process.getDeploymentKey());
    return new RecoveredProcessRecord(record);
  }

  @Override
  public String toJson() {
    return value.toJson();
  }

  @Override
  public long getPosition() {
    throw new UnsupportedOperationException(
        "getPosition() is not supported for RecoveredProcessRecord");
  }

  @Override
  public long getSourceRecordPosition() {
    throw new UnsupportedOperationException(
        "getSourceRecordPosition() is not supported for RecoveredProcessRecord");
  }

  @Override
  public long getKey() {
    return value.getProcessDefinitionKey();
  }

  @Override
  public long getTimestamp() {
    throw new UnsupportedOperationException(
        "getTimestamp() is not supported for RecoveredProcessRecord");
  }

  @Override
  public Intent getIntent() {
    return ProcessIntent.CREATED;
  }

  @Override
  public int getPartitionId() {
    return Protocol.decodePartitionId(getKey());
  }

  @Override
  public RecordType getRecordType() {
    return RecordType.EVENT;
  }

  @Override
  public RejectionType getRejectionType() {
    throw new UnsupportedOperationException(
        "getRejectionType() is not supported for RecoveredProcessRecord");
  }

  @Override
  public String getRejectionReason() {
    throw new UnsupportedOperationException(
        "getRejectionReason() is not supported for RecoveredProcessRecord");
  }

  @Override
  public String getBrokerVersion() {
    return VersionUtil.getVersion();
  }

  @Override
  public Map<String, Object> getAuthorizations() {
    throw new UnsupportedOperationException(
        "getAuthorizations() is not supported for RecoveredProcessRecord");
  }

  @Override
  public Agent getAgent() {
    throw new UnsupportedOperationException(
        "getAgent() is not supported for RecoveredProcessRecord");
  }

  @Override
  public int getRecordVersion() {
    throw new UnsupportedOperationException(
        "getRecordVersion() is not supported for RecoveredProcessRecord");
  }

  @Override
  public ValueType getValueType() {
    return ValueType.PROCESS;
  }

  @Override
  public Process getValue() {
    return value;
  }

  @Override
  public long getOperationReference() {
    throw new UnsupportedOperationException(
        "getOperationReference() is not supported for RecoveredProcessRecord");
  }

  @Override
  public Record<Process> copyOf() {
    throw new UnsupportedOperationException("copyOf() is not supported for RecoveredProcessRecord");
  }
}
