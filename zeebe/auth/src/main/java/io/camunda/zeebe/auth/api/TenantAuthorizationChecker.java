/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.auth.api;

import java.util.List;

public interface TenantAuthorizationChecker {

  /**
   * Verifies if data can be accessed for the tenant whose identifier was provided.
   *
   * @param tenantId the tenant identifier to be checked
   * @return <code>true</code>
   */
  Boolean isAuthorized(String tenantId);

  /**
   * Verifies if data can be accessed for the list of tenants whose identifiers were provided.
   *
   * @param tenantId the tenant identifier to be checked
   * @return <code>true</code>
   */
  Boolean isFullyAuthorized(List<String> tenantId);
}
