/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.BPMN_PROCESS_ID_KEY;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROCESS_DEFINITION_KEY_KEY;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.VERSION_KEY;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * This class is used in the DeploymentRecord, only to send the process meta information back to the
 * user. It is similar to {@link ProcessRecord} except that it doesn't contain the actual resources.
 */
public final class ProcessMetadata extends UnifiedRecordValue implements ProcessMetadataValue {
  private final StringProperty bpmnProcessIdProp = new StringProperty(BPMN_PROCESS_ID_KEY);
  private final IntegerProperty versionProp = new IntegerProperty(VERSION_KEY);
  private final LongProperty keyProp = new LongProperty(PROCESS_DEFINITION_KEY_KEY);
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum");

  // should be set to true if the process was already deployed - property should not be exported
  private final BooleanProperty isDuplicateProp = new BooleanProperty("isDuplicate", false);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1);
  private final StringProperty versionTagProp = new StringProperty("versionTag", "");

  public ProcessMetadata() {
    super(9);
    declareProperty(bpmnProcessIdProp)
        .declareProperty(versionProp)
        .declareProperty(keyProp)
        .declareProperty(resourceNameProp)
        .declareProperty(checksumProp)
        .declareProperty(isDuplicateProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp)
        .declareProperty(versionTagProp);
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  public ProcessMetadata setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTagProp.getValue());
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

  public ProcessMetadata setChecksum(final DirectBuffer checksumBuffer) {
    checksumProp.setValue(checksumBuffer);
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicateProp.getValue();
  }

  public ProcessMetadata setDuplicate(final boolean isDuplicate) {
    isDuplicateProp.setValue(isDuplicate);
    return this;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public ProcessMetadata setDeploymentKey(final long deploymentKey) {
    deploymentKeyProp.setValue(deploymentKey);
    return this;
  }

  public ProcessMetadata setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public ProcessMetadata setResourceName(final DirectBuffer resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public ProcessMetadata setVersionTag(final String versionTag) {
    versionTagProp.setValue(versionTag);
    return this;
  }

  public ProcessMetadata setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public ProcessMetadata setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }

  @JsonIgnore
  public long getKey() {
    return keyProp.getValue();
  }

  public ProcessMetadata setKey(final long key) {
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

  public ProcessMetadata setBpmnProcessId(
      final DirectBuffer bpmnProcessId, final int offset, final int length) {
    bpmnProcessIdProp.setValue(bpmnProcessId, offset, length);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public ProcessMetadata setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
