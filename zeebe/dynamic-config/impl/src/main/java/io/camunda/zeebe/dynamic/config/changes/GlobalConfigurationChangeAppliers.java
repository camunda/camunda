/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;

/**
 * New-model counterpart of {@code ConfigurationChangeAppliers}, dispatching a {@link
 * GlobalChangeOperation} to its {@link GlobalConfigurationChangeApplier}.
 */
@FunctionalInterface
public interface GlobalConfigurationChangeAppliers {

  GlobalConfigurationChangeApplier getApplier(GlobalChangeOperation operation);
}
