/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class DecisionRequirementsMetadataRecord
    extends MsgpackBufferValue<DecisionRequirementsMetadataRecord>
    implements MsgpackRecordValue, DecisionRequirementsMetadataValue {

  DirectBuffer decisionRequirementsId = Decoding.EMPTY;
  DirectBuffer decisionRequirementsName = Decoding.EMPTY;
  int decisionRequirementsVersion = -1;
  long decisionRequirementsKey = -1L;
  DirectBuffer namespace = Decoding.EMPTY;
  DirectBuffer resourceName = Decoding.EMPTY;

  @MsgpackProperty(binary = true)
  DirectBuffer checksum = Decoding.EMPTY;

  boolean isDuplicate = false;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getDecisionRequirementsId() {
    return bufferAsString(decisionRequirementsId);
  }

  @Override
  public String getDecisionRequirementsName() {
    return bufferAsString(decisionRequirementsName);
  }

  @Override
  public int getDecisionRequirementsVersion() {
    return decisionRequirementsVersion;
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  @Override
  public String getNamespace() {
    return bufferAsString(namespace);
  }

  @Override
  public String getResourceName() {
    return bufferAsString(resourceName);
  }

  @Override
  public byte[] getChecksum() {
    return bufferAsArray(checksum);
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicate;
  }

  public DecisionRequirementsMetadataRecord setDuplicate(final boolean duplicate) {
    isDuplicate = duplicate;
    return this;
  }

  public DecisionRequirementsMetadataRecord setChecksum(final DirectBuffer checksum) {
    this.checksum = new UnsafeBuffer(checksum, 0, checksum.capacity());
    return this;
  }

  public DecisionRequirementsMetadataRecord setResourceName(final String resourceName) {
    this.resourceName = BufferUtil.wrapString(resourceName);
    return this;
  }

  public DecisionRequirementsMetadataRecord setNamespace(final String namespace) {
    this.namespace = BufferUtil.wrapString(namespace);
    return this;
  }

  public DecisionRequirementsMetadataRecord setDecisionRequirementsKey(
      final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public DecisionRequirementsMetadataRecord setDecisionRequirementsVersion(
      final int decisionRequirementsVersion) {
    this.decisionRequirementsVersion = decisionRequirementsVersion;
    return this;
  }

  public DecisionRequirementsMetadataRecord setDecisionRequirementsName(
      final String decisionRequirementsName) {
    this.decisionRequirementsName = BufferUtil.wrapString(decisionRequirementsName);
    return this;
  }

  public DecisionRequirementsMetadataRecord setDecisionRequirementsId(
      final String decisionRequirementsId) {
    this.decisionRequirementsId = BufferUtil.wrapString(decisionRequirementsId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getDecisionRequirementsIdBuffer() {
    return decisionRequirementsId;
  }

  @JsonIgnore
  public DirectBuffer getDecisionRequirementsNameBuffer() {
    return decisionRequirementsName;
  }

  @JsonIgnore
  public DirectBuffer getNamespaceBuffer() {
    return namespace;
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceName;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksum;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public DecisionRequirementsMetadataRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public DecisionRequirementsMetadataRecord setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  @Override
  protected MsgpackCodec<DecisionRequirementsMetadataRecord> codec() {
    return DecisionRequirementsMetadataRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DecisionRequirementsMetadataRecord> ops() {
    return DecisionRequirementsMetadataRecordOps.INSTANCE;
  }
}
