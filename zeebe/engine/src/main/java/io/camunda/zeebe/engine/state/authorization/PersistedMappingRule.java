/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.security.core.auth.MappingRuleMatcher;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class PersistedMappingRule extends MsgpackBufferValue<PersistedMappingRule>
    implements DbValue, MappingRuleMatcher.MappingRule {

  long mappingRuleKey = -1L;
  DirectBuffer claimName = Decoding.EMPTY;
  DirectBuffer claimValue = Decoding.EMPTY;
  DirectBuffer name = Decoding.EMPTY;
  DirectBuffer mappingRuleId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PersistedMappingRule copy() {
    final var copy = new PersistedMappingRule();
    copy.copyFrom(this);
    return copy;
  }

  public long getMappingRuleKey() {
    return mappingRuleKey;
  }

  public PersistedMappingRule setMappingRuleKey(final long mappingRuleKey) {
    this.mappingRuleKey = mappingRuleKey;
    return this;
  }

  public String getClaimName() {
    return BufferUtil.bufferAsString(claimName);
  }

  public PersistedMappingRule setClaimName(final String claimName) {
    this.claimName = BufferUtil.wrapString(claimName);
    return this;
  }

  public String getClaimValue() {
    return BufferUtil.bufferAsString(claimValue);
  }

  public PersistedMappingRule setClaimValue(final String claimValue) {
    this.claimValue = BufferUtil.wrapString(claimValue);
    return this;
  }

  public String getName() {
    return BufferUtil.bufferAsString(name);
  }

  public PersistedMappingRule setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  public String getMappingRuleId() {
    return BufferUtil.bufferAsString(mappingRuleId);
  }

  public PersistedMappingRule setMappingRuleId(final String mappingRuleId) {
    this.mappingRuleId = BufferUtil.wrapString(mappingRuleId);
    return this;
  }

  @Override
  public String mappingRuleId() {
    return getMappingRuleId();
  }

  @Override
  public String claimName() {
    return getClaimName();
  }

  @Override
  public String claimValue() {
    return getClaimValue();
  }

  @Override
  protected MsgpackCodec<PersistedMappingRule> codec() {
    return PersistedMappingRuleCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedMappingRule> ops() {
    return PersistedMappingRuleOps.INSTANCE;
  }
}
