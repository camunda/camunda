/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import java.util.List;

/**
 * An instance of {@link AnonymouslyAuthorizedTenants} indicates that a command is executed
 * anonymously without any authentication.
 */
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
    throw new UnsupportedOperationException(
        "Retrieval of authorized tenants is not supported when authenticated anonymously");
  }

  @Override
  public boolean isAnonymous() {
    return true;
  }
}
