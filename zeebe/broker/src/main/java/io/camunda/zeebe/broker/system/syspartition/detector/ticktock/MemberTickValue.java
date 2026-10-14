/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

/** The latest tick a broker is known to have seen, identified by its member id. */
public final class MemberTickValue extends ObjectValue {
  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final LongProperty tickIdProp = new LongProperty("tickId", 0L);

  public MemberTickValue() {
    super(2);
    declareProperty(memberIdProp).declareProperty(tickIdProp);
  }

  public String getMemberId() {
    return BufferUtil.bufferAsString(memberIdProp.getValue());
  }

  public MemberTickValue setMemberId(final String memberId) {
    memberIdProp.setValue(memberId);
    return this;
  }

  public long getTickId() {
    return tickIdProp.getValue();
  }

  public MemberTickValue setTickId(final long tickId) {
    tickIdProp.setValue(tickId);
    return this;
  }
}
