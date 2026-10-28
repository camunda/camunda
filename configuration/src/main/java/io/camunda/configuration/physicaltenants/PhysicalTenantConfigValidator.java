/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Checks a change of a physical tenant's configuration before the cluster accepts it, on top of the
 * boot validations. Every validator registered with the lifecycle runs on every change, from the
 * management API and from the file alike; a change is rejected if any of them reports a violation.
 */
@NullMarked
@FunctionalInterface
public interface PhysicalTenantConfigValidator {

  /**
   * @return why the change is rejected, one entry per violation; empty if the change is acceptable
   */
  List<String> validate(PhysicalTenantConfigChange change);
}
