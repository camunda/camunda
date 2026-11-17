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
 * <p>This wrapper record is not strictly required — a dedicated bean of type {@code Map<String,
 * ResourceAccessController>} would work just as well. It is used to keep the injection point
 * explicit and unambiguous: it avoids any confusion with Spring's bean-collection autowiring, which
 * for a bare {@code Map<String, ResourceAccessController>} injection point keys the map by bean
 * name rather than by physical tenant id.
 */
public record PhysicalTenantResourceAccessControllers(
    Map<String, ResourceAccessController> controllersByPhysicalTenant) {}
