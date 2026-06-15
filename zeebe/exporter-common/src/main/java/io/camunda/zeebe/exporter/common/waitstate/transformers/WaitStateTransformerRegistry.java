/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class WaitStateTransformerRegistry {

  private WaitStateTransformerRegistry() {}

  /**
   * Returns suppliers for transformers that run on all partitions. Suppliers are used to create
   * fresh instances when needed.
   *
   * @return list of transformer suppliers for all partitions
   */
  public static List<Supplier<WaitStateTransformer<?>>>
      getAllPartitionWaitStateTransformerSuppliers() {
    return List.of(
        JobBasedWaitStateTransformer::new,
        UserTaskBasedWaitStateTransformer::new,
        TimerBasedWaitStateTransformer::new,
        MessageBasedWaitStateTransformer::new);
  }

  /**
   * Creates new instances of all transformers. This is useful for tests that need to verify all
   * transformers are properly configured.
   *
   * @return list of all new transformer instances
   */
  public static List<WaitStateTransformer<?>> createAllTransformers() {
    return getAllPartitionWaitStateTransformerSuppliers().stream()
        .map(Supplier::get)
        .collect(Collectors.toList());
  }
}
