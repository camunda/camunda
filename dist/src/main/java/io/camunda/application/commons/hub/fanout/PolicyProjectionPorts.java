/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import io.camunda.security.core.port.out.PolicyProjectionPort;

/**
 * Resolves the CSL projection port for one physical tenant. {@link PolicyProjectionPort#apply}
 * carries no tenant, so the tenant is bound when the port is looked up.
 */
@FunctionalInterface
public interface PolicyProjectionPorts {

  PolicyProjectionPort forPhysicalTenant(String physicalTenantId);
}
