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
 * The value of {@link SystemPartitionKeyType#REGION_FAILOVER}: the command to fail over from a
 * failed region to a surviving one, which initiates the failover procedure. It records the evidence
 * the decision was made on: the tick and the tocks, i.e. the latest tick each broker was seen at,
 * from which every broker derives the same states.
 */
public final class RegionFailoverValue extends UnpackedObject {
  private final StringProperty failedRegionProp = new StringProperty("failedRegion", "");
  private final StringProperty targetRegionProp = new StringProperty("targetRegion", "");
  private final LongProperty tickIdProp = new LongProperty("tickId", 0L);
  private final StringProperty leaderIdProp = new StringProperty("leaderId", "");
  private final ArrayProperty<MemberTickValue> tocksProp =
      new ArrayProperty<>("tocks", MemberTickValue::new);

  public RegionFailoverValue() {
    super(5);
    declareProperty(failedRegionProp)
        .declareProperty(targetRegionProp)
        .declareProperty(tickIdProp)
        .declareProperty(leaderIdProp)
        .declareProperty(tocksProp);
  }

  public String getFailedRegion() {
    return BufferUtil.bufferAsString(failedRegionProp.getValue());
  }

  public RegionFailoverValue setFailedRegion(final String failedRegion) {
    failedRegionProp.setValue(failedRegion);
    return this;
  }

  public String getTargetRegion() {
    return BufferUtil.bufferAsString(targetRegionProp.getValue());
  }

  public RegionFailoverValue setTargetRegion(final String targetRegion) {
    targetRegionProp.setValue(targetRegion);
    return this;
  }

  /** Returns the tick the failover was decided on. */
  public long getTickId() {
    return tickIdProp.getValue();
  }

  public RegionFailoverValue setTickId(final long tickId) {
    tickIdProp.setValue(tickId);
    return this;
  }

  public String getLeaderId() {
    return BufferUtil.bufferAsString(leaderIdProp.getValue());
  }

  public RegionFailoverValue setLeaderId(final String leaderId) {
    leaderIdProp.setValue(leaderId);
    return this;
  }

  /** Returns the latest tick each broker was seen at when the failover was decided. */
  public SortedMap<String, Long> getTocks() {
    final var tocks = new TreeMap<String, Long>();
    tocksProp.forEach(tock -> tocks.put(tock.getMemberId(), tock.getTickId()));
    return tocks;
  }

  /** Replaces the tocks, written in member id order. */
  public RegionFailoverValue setTocks(final Map<String, Long> tocks) {
    tocksProp.reset();
    new TreeMap<>(tocks)
        .forEach((memberId, tickId) -> tocksProp.add().setMemberId(memberId).setTickId(tickId));
    return this;
  }
}
