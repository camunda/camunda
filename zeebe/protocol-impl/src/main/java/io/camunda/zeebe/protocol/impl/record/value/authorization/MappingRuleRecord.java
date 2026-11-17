/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class MappingRuleRecord extends MsgpackBufferValue<MappingRuleRecord>
    implements MsgpackRecordValue, MappingRuleRecordValue {

  long mappingRuleKey = -1L;
  @Nullable DirectBuffer mappingRuleId;
  DirectBuffer claimName = Decoding.EMPTY;
  DirectBuffer claimValue = Decoding.EMPTY;
  DirectBuffer name = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getMappingRuleKey() {
    return mappingRuleKey;
  }

  public MappingRuleRecord setMappingRuleKey(final long mappingRuleKey) {
    this.mappingRuleKey = mappingRuleKey;
    return this;
  }

  @Override
  public String getClaimName() {
    return BufferUtil.bufferAsString(claimName);
  }

  public MappingRuleRecord setClaimName(final String claimName) {
    this.claimName = BufferUtil.wrapString(claimName);
    return this;
  }

  @Override
  public String getClaimValue() {
    return BufferUtil.bufferAsString(claimValue);
  }

  public MappingRuleRecord setClaimValue(final String claimValue) {
    this.claimValue = BufferUtil.wrapString(claimValue);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(name);
  }

  public MappingRuleRecord setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  @Override
  public String getMappingRuleId() {
    return BufferUtil.bufferAsString(Decoding.required(mappingRuleId, "mappingRuleId"));
  }

  public MappingRuleRecord setMappingRuleId(final String mappingRuleId) {
    this.mappingRuleId = BufferUtil.wrapString(mappingRuleId);
    return this;
  }

  @Override
  protected MsgpackCodec<MappingRuleRecord> codec() {
    return MappingRuleRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MappingRuleRecord> ops() {
    return MappingRuleRecordOps.INSTANCE;
  }
}
