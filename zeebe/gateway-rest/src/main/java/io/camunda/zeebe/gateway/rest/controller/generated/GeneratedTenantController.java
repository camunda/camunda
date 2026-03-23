/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantClientSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantCreateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantGroupSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantUserSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedTenantController {

  private final TenantServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedTenantController(
      final TenantServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/tenants",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> createTenant(
      @RequestBody final GeneratedTenantCreateRequestStrictContract tenantCreateRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createTenant(tenantCreateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/tenants/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchTenants(
      @RequestBody(required = false)
          final GeneratedTenantSearchQueryRequestStrictContract tenantSearchQueryRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchTenants(tenantSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/tenants/{tenantId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getTenant(@PathVariable("tenantId") final String tenantId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getTenant(tenantId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/tenants/{tenantId}",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> updateTenant(
      @PathVariable("tenantId") final String tenantId,
      @RequestBody final GeneratedTenantUpdateRequestStrictContract tenantUpdateRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateTenant(tenantId, tenantUpdateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/tenants/{tenantId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> deleteTenant(@PathVariable("tenantId") final String tenantId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteTenant(tenantId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/tenants/{tenantId}/users/{username}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> assignUserToTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("username") final String username) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignUserToTenant(tenantId, username, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/tenants/{tenantId}/users/{username}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> unassignUserFromTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("username") final String username) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignUserFromTenant(tenantId, username, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/tenants/{tenantId}/users/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchUsersForTenant(
      @PathVariable("tenantId") final String tenantId,
      @RequestBody(required = false)
          final GeneratedTenantUserSearchQueryRequestStrictContract tenantUserSearchQueryRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchUsersForTenant(
        tenantId, tenantUserSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/tenants/{tenantId}/clients/{clientId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> assignClientToTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("clientId") final String clientId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignClientToTenant(tenantId, clientId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/tenants/{tenantId}/clients/{clientId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> unassignClientFromTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("clientId") final String clientId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignClientFromTenant(tenantId, clientId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/tenants/{tenantId}/clients/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchClientsForTenant(
      @PathVariable("tenantId") final String tenantId,
      @RequestBody(required = false)
          final GeneratedTenantClientSearchQueryRequestStrictContract
              tenantClientSearchQueryRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchClientsForTenant(
        tenantId, tenantClientSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/tenants/{tenantId}/groups/{groupId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> assignGroupToTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("groupId") final String groupId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignGroupToTenant(tenantId, groupId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/tenants/{tenantId}/groups/{groupId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> unassignGroupFromTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("groupId") final String groupId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignGroupFromTenant(tenantId, groupId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/tenants/{tenantId}/groups/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchGroupIdsForTenant(
      @PathVariable("tenantId") final String tenantId,
      @RequestBody(required = false)
          final GeneratedTenantGroupSearchQueryRequestStrictContract
              tenantGroupSearchQueryRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchGroupIdsForTenant(
        tenantId, tenantGroupSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/tenants/{tenantId}/roles/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchRolesForTenant(
      @PathVariable("tenantId") final String tenantId,
      @RequestBody(required = false)
          final GeneratedRoleSearchQueryRequestStrictContract roleSearchQueryRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchRolesForTenant(tenantId, roleSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/tenants/{tenantId}/roles/{roleId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> assignRoleToTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("roleId") final String roleId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignRoleToTenant(tenantId, roleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/tenants/{tenantId}/roles/{roleId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> unassignRoleFromTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("roleId") final String roleId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignRoleFromTenant(tenantId, roleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/tenants/{tenantId}/mapping-rules/{mappingRuleId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> assignMappingRuleToTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("mappingRuleId") final String mappingRuleId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignMappingRuleToTenant(tenantId, mappingRuleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/tenants/{tenantId}/mapping-rules/{mappingRuleId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> unassignMappingRuleFromTenant(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("mappingRuleId") final String mappingRuleId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignMappingRuleFromTenant(tenantId, mappingRuleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/tenants/{tenantId}/mapping-rules/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchMappingRulesForTenant(
      @PathVariable("tenantId") final String tenantId,
      @RequestBody(required = false)
          final GeneratedMappingRuleSearchQueryRequestStrictContract
              mappingRuleSearchQueryRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchMappingRulesForTenant(
        tenantId, mappingRuleSearchQueryRequest, authentication);
  }
}
