/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({"encodedLength", "empty", "length"})
@MsgpackPojo
@NullMarked
public final class PolicyMembershipEntry extends MsgpackBufferValue<PolicyMembershipEntry>
    implements PolicyMembershipEntryValue {

  PolicyUpdateEntityType targetType = PolicyUpdateEntityType.UNSPECIFIED;
  DirectBuffer targetId = Decoding.EMPTY;
  EntityType memberType = EntityType.UNSPECIFIED;
  DirectBuffer memberId = Decoding.EMPTY;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public PolicyUpdateEntityType getTargetType() {
    return targetType;
  }

  public PolicyMembershipEntry setTargetType(final PolicyUpdateEntityType targetType) {
    this.targetType = targetType;
    return this;
  }

  @Override
  public String getTargetId() {
    return BufferUtil.bufferAsString(targetId);
  }

  public PolicyMembershipEntry setTargetId(final String targetId) {
    this.targetId = BufferUtil.wrapString(targetId);
    return this;
  }

  @Override
  public EntityType getMemberType() {
    return memberType;
  }

  public PolicyMembershipEntry setMemberType(final EntityType memberType) {
    this.memberType = memberType;
    return this;
  }

  @Override
  public String getMemberId() {
    return BufferUtil.bufferAsString(memberId);
  }

  public PolicyMembershipEntry setMemberId(final String memberId) {
    this.memberId = BufferUtil.wrapString(memberId);
    return this;
  }

  @Override
  protected MsgpackCodec<PolicyMembershipEntry> codec() {
    return PolicyMembershipEntryCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PolicyMembershipEntry> ops() {
    return PolicyMembershipEntryOps.INSTANCE;
  }
}
