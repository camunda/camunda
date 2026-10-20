/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class PolicyMembershipEntry extends ObjectValue implements PolicyMembershipEntryValue {

  private final EnumProperty<PolicyUpdateEntityType> targetTypeProp =
      new EnumProperty<>(
          "targetType", PolicyUpdateEntityType.class, PolicyUpdateEntityType.UNSPECIFIED);
  private final StringProperty targetIdProp = new StringProperty("targetId", "");
  private final EnumProperty<EntityType> memberTypeProp =
      new EnumProperty<>("memberType", EntityType.class, EntityType.UNSPECIFIED);
  private final StringProperty memberIdProp = new StringProperty("memberId", "");

  public PolicyMembershipEntry() {
    super(4);
    declareProperty(targetTypeProp)
        .declareProperty(targetIdProp)
        .declareProperty(memberTypeProp)
        .declareProperty(memberIdProp);
  }

  @Override
  public PolicyUpdateEntityType getTargetType() {
    return targetTypeProp.getValue();
  }

  public PolicyMembershipEntry setTargetType(final PolicyUpdateEntityType targetType) {
    targetTypeProp.setValue(targetType);
    return this;
  }

  @Override
  public String getTargetId() {
    return BufferUtil.bufferAsString(targetIdProp.getValue());
  }

  public PolicyMembershipEntry setTargetId(final String targetId) {
    targetIdProp.setValue(targetId);
    return this;
  }

  @Override
  public EntityType getMemberType() {
    return memberTypeProp.getValue();
  }

  public PolicyMembershipEntry setMemberType(final EntityType memberType) {
    memberTypeProp.setValue(memberType);
    return this;
  }

  @Override
  public String getMemberId() {
    return BufferUtil.bufferAsString(memberIdProp.getValue());
  }

  public PolicyMembershipEntry setMemberId(final String memberId) {
    memberIdProp.setValue(memberId);
    return this;
  }
}
