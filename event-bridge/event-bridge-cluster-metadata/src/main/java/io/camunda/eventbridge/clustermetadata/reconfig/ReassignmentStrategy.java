/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.reconfig;

/**
 * How the change-coordinator replaces a <em>dead</em> (fenced) member of a partition. Both are
 * safe; they trade availability against durability during the transition.
 *
 * <ul>
 *   <li>{@link #GROW_FIRST} — passive-join the replacement, wait for it to catch up, promote it to
 *       voting, then remove the dead member. Keeps the most data copies and survives a
 *       false-positive fence (the maybe-alive member is never dropped before its replacement is
 *       fully in place), at the cost of a briefly larger voting set.
 *   <li>{@link #SHRINK_FIRST} — remove the dead member first, then active-join the replacement into
 *       the clean (smaller) group. Simpler and keeps the smallest voting set, passing briefly
 *       through a bare-quorum configuration.
 * </ul>
 *
 * <p>Only applies to a dead member. A live-member reassignment is always grow-before-shrink
 * (active-join the replacement, then remove the old member) regardless of this setting.
 */
public enum ReassignmentStrategy {
  GROW_FIRST,
  SHRINK_FIRST
}
