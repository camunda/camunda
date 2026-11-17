/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class PersistedDecisionRequirements
    extends MsgpackBufferValue<PersistedDecisionRequirements>
    implements DbValue, ProjectionOf<DecisionRequirementsRecord, PersistedDecisionRequirements> {

  @Nullable DirectBuffer decisionRequirementsId;
  @Nullable DirectBuffer decisionRequirementsName;
  final RequiredInt decisionRequirementsVersion = new RequiredInt();
  final RequiredLong decisionRequirementsKey = new RequiredLong();
  @Nullable DirectBuffer resourceName;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer checksum;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer resource;

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public PersistedDecisionRequirements wrap(final DecisionRequirementsRecord record) {
    decisionRequirementsId = record.getDecisionRequirementsIdBuffer();
    decisionRequirementsName = record.getDecisionRequirementsNameBuffer();
    decisionRequirementsVersion.set(record.getDecisionRequirementsVersion());
    decisionRequirementsKey.set(record.getDecisionRequirementsKey());
    resourceName = record.getResourceNameBuffer();
    checksum = record.getChecksumBuffer();
    resource = record.getResourceBuffer();
    tenantId = BufferUtil.wrapString(record.getTenantId());
    deploymentKey = record.getDeploymentKey();
    return this;
  }

  public PersistedDecisionRequirements copy() {
    final var copy = new PersistedDecisionRequirements();
    copy.decisionRequirementsId = BufferUtil.cloneBuffer(getDecisionRequirementsId());
    copy.decisionRequirementsName = BufferUtil.cloneBuffer(getDecisionRequirementsName());
    copy.decisionRequirementsVersion.set(getDecisionRequirementsVersion());
    copy.decisionRequirementsKey.set(getDecisionRequirementsKey());
    copy.resourceName = BufferUtil.cloneBuffer(getResourceName());
    copy.checksum = BufferUtil.cloneBuffer(getChecksum());
    copy.resource = BufferUtil.cloneBuffer(getResource());
    copy.tenantId = BufferUtil.wrapString(getTenantId());
    copy.deploymentKey = getDeploymentKey();
    return copy;
  }

  public DirectBuffer getDecisionRequirementsId() {
    return Decoding.required(decisionRequirementsId, "decisionRequirementsId");
  }

  public DirectBuffer getDecisionRequirementsName() {
    return Decoding.required(decisionRequirementsName, "decisionRequirementsName");
  }

  public int getDecisionRequirementsVersion() {
    return decisionRequirementsVersion.get();
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey.get();
  }

  public DirectBuffer getResourceName() {
    return Decoding.required(resourceName, "resourceName");
  }

  public DirectBuffer getChecksum() {
    return Decoding.required(checksum, "checksum");
  }

  public DirectBuffer getResource() {
    return Decoding.required(resource, "resource");
  }

  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
  }

  public long getDeploymentKey() {
    return deploymentKey;
  }

  public void setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
  }

  @Override
  protected MsgpackCodec<PersistedDecisionRequirements> codec() {
    return PersistedDecisionRequirementsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedDecisionRequirements> ops() {
    return PersistedDecisionRequirementsOps.INSTANCE;
  }
}
