/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The value of {@link SystemPartitionKeyType#TICK}: the latest tick committed to the system
 * partition, the leader which put it, and the tocks that leader received since its previous tick.
 */
public final class TickValue extends UnpackedObject {
  private final LongProperty tickIdProp = new LongProperty("tickId", 0L);
  private final StringProperty leaderIdProp = new StringProperty("leaderId", "");
  private final ArrayProperty<MemberTickValue> tocksProp =
      new ArrayProperty<>("tocks", MemberTickValue::new);

  public TickValue() {
    super(3);
    declareProperty(tickIdProp).declareProperty(leaderIdProp).declareProperty(tocksProp);
  }

  public long getTickId() {
    return tickIdProp.getValue();
  }

  public TickValue setTickId(final long tickId) {
    tickIdProp.setValue(tickId);
    return this;
  }

  public String getLeaderId() {
    return BufferUtil.bufferAsString(leaderIdProp.getValue());
  }

  public TickValue setLeaderId(final String leaderId) {
    leaderIdProp.setValue(leaderId);
    return this;
  }

  /** Returns the latest tick each other broker acknowledged since the previous tick. */
  public SortedMap<String, Long> getTocks() {
    final var tocks = new TreeMap<String, Long>();
    tocksProp.forEach(tock -> tocks.put(tock.getMemberId(), tock.getTickId()));
    return tocks;
  }

  /** Replaces the tocks, written in member id order. */
  public TickValue setTocks(final Map<String, Long> tocks) {
    tocksProp.reset();
    new TreeMap<>(tocks)
        .forEach((memberId, tickId) -> tocksProp.add().setMemberId(memberId).setTickId(tickId));
    return this;
  }
}
