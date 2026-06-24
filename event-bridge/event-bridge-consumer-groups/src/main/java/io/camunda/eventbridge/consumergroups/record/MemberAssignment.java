/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.record;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

/**
 * One member's target partitions within a {@link RebalanceRecord} — the element type of its {@code
 * members} array. A nested msgpack object so the assignor can carry the whole group's proposed
 * assignment in a single {@code REBALANCE_GROUP} command.
 */
public final class MemberAssignment extends ObjectValue {

  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final ArrayProperty<IntegerValue> partitionsProp =
      new ArrayProperty<>("partitions", IntegerValue::new);

  public MemberAssignment() {
    super(2);
    declareProperty(memberIdProp).declareProperty(partitionsProp);
  }

  public String getMemberId() {
    return BufferUtil.bufferAsString(memberIdProp.getValue());
  }

  public MemberAssignment setMemberId(final String memberId) {
    memberIdProp.setValue(memberId);
    return this;
  }

  public List<Integer> getPartitions() {
    return partitionsProp.stream().map(IntegerValue::getValue).toList();
  }

  public MemberAssignment setPartitions(final List<Integer> partitions) {
    partitionsProp.reset();
    partitions.forEach(p -> partitionsProp.add().setValue(p));
    return this;
  }
}
