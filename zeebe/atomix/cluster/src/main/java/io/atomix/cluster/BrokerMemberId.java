/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster;

import io.camunda.zeebe.util.VisibleForTesting;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record BrokerMemberId(MemberId memberId) {
  public BrokerMemberId {
    try {
      memberId.nodeIdx();
    } catch (final IllegalStateException e) {
      throw new IllegalStateException(
          "Expected id to represent a broker, but got " + memberId.id(), e);
    }
  }

  public static BrokerMemberId from(@Nullable final String zone, final int nodeIdx) {
    return new BrokerMemberId(MemberId.from(zone, nodeIdx));
  }

  public static BrokerMemberId from(final String id) {
    return new BrokerMemberId(MemberId.from(id));
  }

  public static BrokerMemberId from(final MemberId memberId) {
    return new BrokerMemberId(memberId);
  }

  @VisibleForTesting
  public static BrokerMemberId from(final int nodeIdx) {
    return from(null, nodeIdx);
  }

  public int nodeIdx() {
    return memberId.nodeIdx();
  }

  public @Nullable String zone() {
    return memberId.zone();
  }

  public String id() {
    return memberId.id();
  }

  public boolean isInZone(final @Nullable String zone) {
    return memberId.isInZone(zone);
  }

  @Override
  public String toString() {
    return memberId.toString();
  }
}
