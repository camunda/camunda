/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.configuration.Camunda;
import org.jspecify.annotations.NullMarked;

/**
 * Resources of a physical tenant that live outside its broker module, such as the refresher of its
 * secrets, and follow the tenant through {@link PhysicalTenantLifecycleService}.
 */
@NullMarked
public interface PhysicalTenantResources {

  /**
   * Called after a tenant is added or replaced, before its module starts, with the configuration it
   * now runs with.
   *
   * @throws RuntimeException if the resources cannot be built; the module does not start then
   */
  void apply(String physicalTenantId, Camunda configuration);

  /** Called after the module of a removed tenant stopped. */
  void release(String physicalTenantId);
}
