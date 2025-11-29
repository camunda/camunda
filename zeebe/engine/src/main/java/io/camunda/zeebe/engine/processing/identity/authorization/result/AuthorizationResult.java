/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.result;

/**
 * Represents the intermediate result of an authorization check containing both tenant and resource
 * access status.
 *
 * <p>Used internally during authorization evaluation to track partial results before producing a
 * final rejection or approval.
 *
 * @param hasTenantAccess whether the entity has access to the tenant
 * @param hasResourceAccess whether the entity has permission for the resource
 */
public record AuthorizationResult(boolean hasTenantAccess, boolean hasResourceAccess) {

  /**
   * @return true if both tenant and resource access are granted
   */
  public boolean hasBothAccess() {
    return hasTenantAccess && hasResourceAccess;
  }
}
