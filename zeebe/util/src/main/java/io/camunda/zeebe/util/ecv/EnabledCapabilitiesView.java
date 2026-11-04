/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.ecv;

import java.util.function.Consumer;

/**
 * The local, read-only view of the cluster's enabled capabilities (design section 8). The view only
 * grows. It can be behind the system partition, but it never shows a capability that is not
 * committed.
 */
public interface EnabledCapabilitiesView {

  /** A view that never has an enabled capability; for components that run without a cluster. */
  EnabledCapabilitiesView EMPTY =
      new EnabledCapabilitiesView() {
        @Override
        public CapabilitySet enabled() {
          return CapabilitySet.EMPTY;
        }

        @Override
        public void addListener(final Consumer<CapabilitySet> listener) {}

        @Override
        public void removeListener(final Consumer<CapabilitySet> listener) {}
      };

  CapabilitySet enabled();

  /**
   * Adds a listener that is called with the new enabled set each time the view grows. It is called
   * on an arbitrary thread and must not block.
   */
  void addListener(Consumer<CapabilitySet> listener);

  void removeListener(Consumer<CapabilitySet> listener);
}
