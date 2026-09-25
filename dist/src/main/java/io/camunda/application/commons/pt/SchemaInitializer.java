/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import org.jspecify.annotations.NullMarked;

/** Applies one physical tenant's secondary-storage schema immediately. */
@NullMarked
public interface SchemaInitializer {

  /** Applies the schema of the given physical tenant without using a startup retry loop. */
  void initializeNow(String physicalTenantId);
}
