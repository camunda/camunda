/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.api.physicaltenants;

import java.util.Set;

/**
 * Provides the set of known physical tenant IDs. Implemented by the configuration layer and
 * injected into components that need to validate tenant IDs without coupling to the configuration
 * module.
 */
@FunctionalInterface
public interface PhysicalTenantIds {

  /** The canonical physical tenant ID used when no explicit tenant is configured. */
  String DEFAULT_PHYSICAL_TENANT_ID = "default";

  Set<String> known();
}
