/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.ecv;

import org.jspecify.annotations.Nullable;

/**
 * What the engine of one partition needs from the broker for the ECV commit (design sections 3, 4
 * and 6), and how it reports its progress back.
 */
public interface PartitionCapabilities {

  /** For engines that run without a broker: everything is supported, nothing is enabled. */
  PartitionCapabilities NOOP =
      new PartitionCapabilities() {
        @Override
        public CapabilitySet supported() {
          return Capability.all();
        }

        @Override
        public EnabledCapabilitiesView view() {
          return EnabledCapabilitiesView.EMPTY;
        }

        @Override
        public void onProgress(final Progress progress) {}
      };

  /** The capabilities that this broker supports; the engine guard refuses any other. */
  CapabilitySet supported();

  /** The cluster's enabled capabilities, which the partition leader pulls into the engine. */
  EnabledCapabilitiesView view();

  /** Called by the partition leader each time its applied set or its last rejection changes. */
  void onProgress(Progress progress);

  /** Called when the partition stops being the leader, so the broker stops answering for it. */
  default void onLeadershipLost() {}

  /**
   * The progress of the partition (design section 4).
   *
   * @param applied the enabled capabilities in the engine state of the partition
   * @param rejected the last set that the engine rejected, or null
   * @param rejectionReason why it rejected that set, or null
   */
  record Progress(
      CapabilitySet applied, @Nullable CapabilitySet rejected, @Nullable String rejectionReason) {}
}
