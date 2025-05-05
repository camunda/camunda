/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.ClusterOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;

abstract class AbstractApplierTest {
  protected ClusterConfiguration runApplier(
      final ClusterOperationApplier applier, final ClusterConfiguration initialConfiguration) {
    final var initializedConfiguration =
        applier.init(initialConfiguration).get().apply(initialConfiguration);
    final var updater = applier.apply().join();
    return updater.apply(initializedConfiguration);
  }
}
