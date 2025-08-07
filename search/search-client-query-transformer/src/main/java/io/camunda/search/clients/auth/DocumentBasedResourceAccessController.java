/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.security.reader.TenantAccessProvider;

public class DocumentBasedResourceAccessController extends AbstractResourceAccessController {

  private final ResourceAccessProvider resourceAccessProvider;
  private final TenantAccessProvider tenantAccessProvider;

  public DocumentBasedResourceAccessController(
      final ResourceAccessProvider resourceAccessProvider,
      final TenantAccessProvider tenantAccessProvider) {
    this.resourceAccessProvider = resourceAccessProvider;
    this.tenantAccessProvider = tenantAccessProvider;
  }

  @Override
  protected ResourceAccessProvider getResourceAccessProvider() {
    return resourceAccessProvider;
  }

  @Override
  protected TenantAccessProvider getTenantAccessProvider() {
    return tenantAccessProvider;
  }
}
