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
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The value of {@link SystemPartitionKeyType#CLUSTER_VIEW}: the latest tick each broker is known to
 * have seen. The leader puts it before each tick, so it is a committed fact every broker reads
 * alike, and replaying the log rebuilds it.
 */
public final class ClusterViewValue extends UnpackedObject {
  private final ArrayProperty<MemberTickValue> membersProp =
      new ArrayProperty<>("members", MemberTickValue::new);

  public ClusterViewValue() {
    super(1);
    declareProperty(membersProp);
  }

  /** Returns the latest tick seen by each member, by member id. */
  public SortedMap<String, Long> lastSeenTicks() {
    final var ticks = new TreeMap<String, Long>();
    membersProp.forEach(member -> ticks.put(member.getMemberId(), member.getTickId()));
    return ticks;
  }

  /**
   * Replaces the members with the given ones. They are written in member id order, so the same view
   * always serializes to the same bytes on every broker.
   */
  public ClusterViewValue setLastSeenTicks(final Map<String, Long> ticks) {
    membersProp.reset();
    new TreeMap<>(ticks)
        .forEach((memberId, tickId) -> membersProp.add().setMemberId(memberId).setTickId(tickId));
    return this;
  }
}
