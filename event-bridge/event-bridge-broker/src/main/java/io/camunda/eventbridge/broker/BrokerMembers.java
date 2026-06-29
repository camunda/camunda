/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker;

import io.atomix.cluster.MemberId;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The naming convention mapping a numeric broker node id to its SWIM {@link MemberId} ({@code n →
 * broker-<n>}) and back. The {@code broker-<n>} form is the cluster-wide member id used for Raft
 * membership, leadership reports, and the change-coordinator's command routing, so every broker
 * must agree on the string — hence it lives in one place rather than being re-derived ad hoc.
 */
public final class BrokerMembers {

  /** Prefix for a broker's SWIM member id. */
  public static final String MEMBER_ID_PREFIX = "broker-";

  private BrokerMembers() {}

  /** The SWIM member id for a numeric node id, e.g. {@code 3 → broker-3}. */
  public static MemberId memberId(final int nodeId) {
    return MemberId.from(MEMBER_ID_PREFIX + nodeId);
  }

  /**
   * The numeric node id encoded in a {@code broker-<n>} member id, or {@code -1} if it carries no
   * digits. Node ids are always non-negative, so {@code -1} is an unambiguous "unparseable" marker.
   */
  public static int nodeId(final MemberId memberId) {
    return nodeId(memberId.id());
  }

  /** The numeric node id encoded in a {@code broker-<n>} member id string. See {@link #nodeId}. */
  public static int nodeId(final String memberId) {
    try {
      return Integer.parseInt(memberId.replaceAll("[^0-9]", ""));
    } catch (final NumberFormatException e) {
      return -1;
    }
  }

  /** The sorted member ids of a cluster of the given size: {@code broker-0 … broker-(size-1)}. */
  public static List<MemberId> all(final int clusterSize) {
    return IntStream.range(0, clusterSize).mapToObj(BrokerMembers::memberId).sorted().toList();
  }
}
