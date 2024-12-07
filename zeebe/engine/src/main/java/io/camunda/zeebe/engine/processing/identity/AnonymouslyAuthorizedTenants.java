/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import java.util.List;

public final class AnonymouslyAuthorizedTenants implements AuthorizedTenants {

  @Override
  public boolean isAuthorizedForTenantId(final String tenantId) {
    return true;
  }

  @Override
  public boolean isAuthorizedForTenantIds(final List<String> tenantIds) {
    return true;
  }

  @Override
  public List<String> getAuthorizedTenantIds() {
    return List.of("*");
  }
}
