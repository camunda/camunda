/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class DecisionRecord extends MsgpackBufferValue<DecisionRecord>
    implements MsgpackRecordValue, DecisionRecordValue {

  DirectBuffer decisionId = Decoding.EMPTY;
  DirectBuffer decisionName = Decoding.EMPTY;
  int version = -1;
  long decisionKey = -1L;
  DirectBuffer decisionRequirementsId = Decoding.EMPTY;
  long decisionRequirementsKey = -1L;
  boolean isDuplicate = false;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  DirectBuffer versionTag = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getDecisionId() {
    return bufferAsString(decisionId);
  }

  @Override
  public String getDecisionName() {
    return bufferAsString(decisionName);
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  @Override
  public long getDecisionKey() {
    return decisionKey;
  }

  @Override
  public String getDecisionRequirementsId() {
    return bufferAsString(decisionRequirementsId);
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicate;
  }

  public DecisionRecord setDuplicate(final boolean duplicate) {
    isDuplicate = duplicate;
    return this;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public DecisionRecord setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public DecisionRecord setDecisionRequirementsKey(final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public DecisionRecord setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = BufferUtil.wrapString(decisionRequirementsId);
    return this;
  }

  public DecisionRecord setDecisionKey(final long decisionKey) {
    this.decisionKey = decisionKey;
    return this;
  }

  public DecisionRecord setVersionTag(final String versionTag) {
    this.versionTag = BufferUtil.wrapString(versionTag);
    return this;
  }

  public DecisionRecord setVersion(final int version) {
    this.version = version;
    return this;
  }

  public DecisionRecord setDecisionName(final String decisionName) {
    this.decisionName = BufferUtil.wrapString(decisionName);
    return this;
  }

  public DecisionRecord setDecisionId(final String decisionId) {
    this.decisionId = BufferUtil.wrapString(decisionId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getDecisionIdBuffer() {
    return decisionId;
  }

  @JsonIgnore
  public DirectBuffer getDecisionNameBuffer() {
    return decisionName;
  }

  @JsonIgnore
  public DirectBuffer getDecisionRequirementsIdBuffer() {
    return decisionRequirementsId;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public DecisionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<DecisionRecord> codec() {
    return DecisionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DecisionRecord> ops() {
    return DecisionRecordOps.INSTANCE;
  }
}
