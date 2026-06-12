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
 * Provides the set of known physical tenant IDs. Implemented by the application layer and injected
 * into gateway components that need to validate tenant IDs without coupling to the configuration
 * module.
 */
@FunctionalInterface
public interface PhysicalTenantIds {
  Set<String> known();
}
