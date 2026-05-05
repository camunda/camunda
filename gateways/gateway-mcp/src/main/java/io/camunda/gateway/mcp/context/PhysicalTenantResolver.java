/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.context;

/**
 * Resolves whether a given physical tenant id is configured/known.
 *
 * <p>Mirrors {@code io.camunda.zeebe.gateway.rest.util.PhysicalTenantResolver} but kept local to
 * this module to avoid a {@code zeebe-gateway-rest} dependency. If no bean is supplied, the MCP
 * tenant filter falls back to one that only accepts the {@code "default"} tenant id (see {@link
 * PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID}).
 */
@FunctionalInterface
public interface PhysicalTenantResolver {

  /**
   * @return {@code true} when {@code physicalTenantId} is a known/configured physical tenant.
   */
  boolean exists(String physicalTenantId);
}
