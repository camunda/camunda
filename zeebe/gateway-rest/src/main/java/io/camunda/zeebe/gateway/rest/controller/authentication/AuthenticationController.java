/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.authentication;

import static io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper.toCamundaUser;

import io.camunda.gateway.protocol.model.CamundaUserResult;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.port.in.CamundaUserPort;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@Profile("consolidated-auth")
@CamundaRestController
@ConditionalOnSecondaryStorageEnabled
@RequestMapping("/v2/authentication")
public class AuthenticationController {
  private final CamundaUserPort camundaUserPort;
  private final ServiceRegistry serviceRegistry;

  public AuthenticationController(
      final CamundaUserPort camundaUserPort, final ServiceRegistry serviceRegistry) {
    this.camundaUserPort = camundaUserPort;
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaGetMapping(path = "/me")
  public ResponseEntity<CamundaUserResult> getCurrentUser() {
    final var authenticatedUser = camundaUserPort.getCurrentUser();
    if (authenticatedUser == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    final var tenants = resolveTenants(authenticatedUser.tenants());
    return ResponseEntity.ok(toCamundaUser(authenticatedUser, tenants));
  }

  /**
   * Resolves {@link TenantEntity} instances for the IDs carried on {@link
   * io.camunda.security.api.model.user.CamundaUserDTO#tenants()}. The CSL DTO holds tenant IDs only
   * — display names and other metadata were dropped to keep the CSL boundary free of OC's
   * search-domain entities — but the {@code /v2/authentication/me} response (per the OpenAPI
   * contract for {@code CamundaUserResult}) still includes those richer tenant objects, so the
   * controller re-enriches at the edge.
   *
   * <p>This is the seam that used to live inside OC's previous {@code OidcCamundaUserService} (now
   * the CSL-default one). The lookup runs with {@link CamundaAuthentication#anonymous()} because
   * tenant visibility for the current user has already been decided by CSL when populating {@code
   * authenticatedTenantIds()}; here we are just fetching display metadata for IDs the caller is
   * already entitled to see.
   */
  private List<TenantEntity> resolveTenants(final List<String> tenantIds) {
    if (tenantIds == null || tenantIds.isEmpty()) {
      return List.of();
    }
    return serviceRegistry
        .tenantServices(
            PhysicalTenantContext
                .DEFAULT_PHYSICAL_TENANT_ID) // TODO replace with contextual physicalTenantId
        .search(
            TenantQuery.of(q -> q.filter(f -> f.tenantIds(tenantIds)).unlimited()),
            CamundaAuthentication.anonymous())
        .items();
  }
}
