/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.deployment;

import static io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_BPMN_PROCESS_ID;
import static io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_KEY;
import static io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_VERSION;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.deployment.Process;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class ProcessRecord extends UnifiedRecordValue implements Process {
  private final StringProperty bpmnProcessIdProp = new StringProperty(PROP_PROCESS_BPMN_PROCESS_ID);
  private final IntegerProperty versionProp = new IntegerProperty(PROP_PROCESS_VERSION);
  private final LongProperty keyProp = new LongProperty(PROP_PROCESS_KEY);
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum");
  private final BinaryProperty resourceProp = new BinaryProperty("resource");

  public ProcessRecord() {
    declareProperty(bpmnProcessIdProp)
        .declareProperty(versionProp)
        .declareProperty(keyProp)
        .declareProperty(resourceNameProp)
        .declareProperty(checksumProp)
        .declareProperty(resourceProp);
  }

  public ProcessRecord wrap(final ProcessMetadata metadata, final byte[] resource) {
    bpmnProcessIdProp.setValue(metadata.getBpmnProcessIdBuffer());
    versionProp.setValue(metadata.getVersion());
    checksumProp.setValue(metadata.getChecksumBuffer());
    keyProp.setValue(metadata.getKey());
    resourceNameProp.setValue(metadata.getResourceNameBuffer());
    resourceProp.setValue(BufferUtil.wrapArray(resource));
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return getKey();
  }

  @Override
  public String getResourceName() {
    return BufferUtil.bufferAsString(resourceNameProp.getValue());
  }

  @Override
  public byte[] getChecksum() {
    return BufferUtil.bufferAsArray(checksumProp.getValue());
  }

  public ProcessRecord setChecksum(final DirectBuffer checksumBuffer) {
    checksumProp.setValue(checksumBuffer);
    return this;
  }

  public ProcessRecord setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public ProcessRecord setResourceName(final DirectBuffer resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public ProcessRecord setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  public ProcessRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public ProcessRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }

  @Override
  public byte[] getResource() {
    return BufferUtil.bufferAsArray(resourceProp.getValue());
  }

  public ProcessRecord setResource(final DirectBuffer resource) {
    return setResource(resource, 0, resource.capacity());
  }

  @JsonIgnore
  public long getKey() {
    return keyProp.getValue();
  }

  public ProcessRecord setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceNameProp.getValue();
  }

  public ProcessRecord setBpmnProcessId(
      final DirectBuffer bpmnProcessId, final int offset, final int length) {
    bpmnProcessIdProp.setValue(bpmnProcessId, offset, length);
    return this;
  }

  public ProcessRecord setResource(
      final DirectBuffer resource, final int offset, final int length) {
    resourceProp.setValue(resource, offset, length);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getResourceBuffer() {
    return resourceProp.getValue();
  }
}
