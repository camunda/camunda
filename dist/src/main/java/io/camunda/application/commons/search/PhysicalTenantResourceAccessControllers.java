/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.security.core.authz.ResourceAccessController;
import java.util.Map;

/**
 * Holds the {@link ResourceAccessController} for every physical tenant, keyed by physical tenant
 * id.
 *
 * <p>A typed wrapper is required because injecting a bare {@code Map<String,
 * ResourceAccessController>} would trigger Spring's collection-injection semantics.
 */
public record PhysicalTenantResourceAccessControllers(
    Map<String, ResourceAccessController> controllersByPhysicalTenant) {}
