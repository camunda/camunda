/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

/**
 * Resolves whether a given physical tenant id is configured/known.
 *
 * <p>If no bean is supplied, {@link
 * io.camunda.zeebe.gateway.rest.config.PhysicalTenantWebMvcConfig} falls back to one that only
 * accepts the {@code "default"} tenant.
 */
@FunctionalInterface
public interface PhysicalTenantResolver {

  /**
   * @return {@code true} when {@code physicalTenantId} is a known/configured physical tenant.
   */
  boolean exists(String physicalTenantId);
}
