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
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class PersistedDecision extends MsgpackBufferValue<PersistedDecision>
    implements DbValue, ProjectionOf<DecisionRecord, PersistedDecision> {

  private static final long NO_DEPLOYMENT_KEY = -1L;

  @Nullable DirectBuffer decisionId;
  @Nullable DirectBuffer decisionName;
  final RequiredInt version = new RequiredInt();
  final RequiredLong decisionKey = new RequiredLong();
  @Nullable DirectBuffer decisionRequirementsId;
  final RequiredLong decisionRequirementsKey = new RequiredLong();
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = NO_DEPLOYMENT_KEY;
  DirectBuffer versionTag = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public PersistedDecision wrap(final DecisionRecord record) {
    decisionId = BufferUtil.wrapString(record.getDecisionId());
    decisionName = BufferUtil.wrapString(record.getDecisionName());
    version.set(record.getVersion());
    decisionKey.set(record.getDecisionKey());
    decisionRequirementsId = record.getDecisionRequirementsIdBuffer();
    decisionRequirementsKey.set(record.getDecisionRequirementsKey());
    tenantId = BufferUtil.wrapString(record.getTenantId());
    deploymentKey = record.getDeploymentKey();
    versionTag = BufferUtil.wrapString(record.getVersionTag());
    return this;
  }

  public DirectBuffer getDecisionId() {
    return Decoding.required(decisionId, "decisionId");
  }

  public DirectBuffer getDecisionName() {
    return Decoding.required(decisionName, "decisionName");
  }

  public int getVersion() {
    return version.get();
  }

  public long getDecisionKey() {
    return decisionKey.get();
  }

  public DirectBuffer getDecisionRequirementsId() {
    return Decoding.required(decisionRequirementsId, "decisionRequirementsId");
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey.get();
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

  public boolean hasDeploymentKey() {
    return deploymentKey != NO_DEPLOYMENT_KEY;
  }

  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  @Override
  protected MsgpackCodec<PersistedDecision> codec() {
    return PersistedDecisionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedDecision> ops() {
    return PersistedDecisionOps.INSTANCE;
  }
}
