/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.util.tenant;

import org.camunda.optimize.dto.optimize.IdentityType;

import java.util.List;

public interface DataSourceTenantAuthorizationService {

  boolean isAuthorizedToSeeAllTenants(final String identityId, final IdentityType identityType,
                                      final List<String> tenantIds);

  boolean isAuthorizedToSeeTenant(final String identityId, final IdentityType identityType, final String tenantId);

  boolean isAuthorizedToSeeTenant(final String identityId, final IdentityType identityType, final String tenantId,
                                  final String dataSourceName);

}
