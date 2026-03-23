/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleClientSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleCreateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleGroupSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleUserSearchQueryRequestStrictContract;
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
public class GeneratedRoleController {

  private final RoleServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedRoleController(
      final RoleServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/roles",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> createRole(
      @RequestBody final GeneratedRoleCreateRequestStrictContract roleCreateRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createRole(roleCreateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/roles/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchRoles(
      @RequestBody(required = false) final GeneratedRoleSearchQueryRequestStrictContract roleSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchRoles(roleSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/roles/{roleId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getRole(
      @PathVariable("roleId") final String roleId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getRole(roleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/roles/{roleId}",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> updateRole(
      @PathVariable("roleId") final String roleId,
      @RequestBody final GeneratedRoleUpdateRequestStrictContract roleUpdateRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateRole(roleId, roleUpdateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/roles/{roleId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> deleteRole(
      @PathVariable("roleId") final String roleId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteRole(roleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/roles/{roleId}/users/{username}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> assignRoleToUser(
      @PathVariable("roleId") final String roleId,
      @PathVariable("username") final String username
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignRoleToUser(roleId, username, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/roles/{roleId}/users/{username}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> unassignRoleFromUser(
      @PathVariable("roleId") final String roleId,
      @PathVariable("username") final String username
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignRoleFromUser(roleId, username, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/roles/{roleId}/users/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchUsersForRole(
      @PathVariable("roleId") final String roleId,
      @RequestBody(required = false) final GeneratedRoleUserSearchQueryRequestStrictContract roleUserSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchUsersForRole(roleId, roleUserSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/roles/{roleId}/clients/{clientId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> assignRoleToClient(
      @PathVariable("roleId") final String roleId,
      @PathVariable("clientId") final String clientId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignRoleToClient(roleId, clientId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/roles/{roleId}/clients/{clientId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> unassignRoleFromClient(
      @PathVariable("roleId") final String roleId,
      @PathVariable("clientId") final String clientId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignRoleFromClient(roleId, clientId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/roles/{roleId}/clients/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchClientsForRole(
      @PathVariable("roleId") final String roleId,
      @RequestBody(required = false) final GeneratedRoleClientSearchQueryRequestStrictContract roleClientSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchClientsForRole(roleId, roleClientSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/roles/{roleId}/groups/{groupId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> assignRoleToGroup(
      @PathVariable("roleId") final String roleId,
      @PathVariable("groupId") final String groupId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignRoleToGroup(roleId, groupId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/roles/{roleId}/groups/{groupId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> unassignRoleFromGroup(
      @PathVariable("roleId") final String roleId,
      @PathVariable("groupId") final String groupId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignRoleFromGroup(roleId, groupId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/roles/{roleId}/groups/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchGroupsForRole(
      @PathVariable("roleId") final String roleId,
      @RequestBody(required = false) final GeneratedRoleGroupSearchQueryRequestStrictContract roleGroupSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchGroupsForRole(roleId, roleGroupSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/roles/{roleId}/mapping-rules/{mappingRuleId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> assignRoleToMappingRule(
      @PathVariable("roleId") final String roleId,
      @PathVariable("mappingRuleId") final String mappingRuleId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignRoleToMappingRule(roleId, mappingRuleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/roles/{roleId}/mapping-rules/{mappingRuleId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> unassignRoleFromMappingRule(
      @PathVariable("roleId") final String roleId,
      @PathVariable("mappingRuleId") final String mappingRuleId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignRoleFromMappingRule(roleId, mappingRuleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/roles/{roleId}/mapping-rules/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchMappingRulesForRole(
      @PathVariable("roleId") final String roleId,
      @RequestBody(required = false) final GeneratedMappingRuleSearchQueryRequestStrictContract mappingRuleSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchMappingRulesForRole(roleId, mappingRuleSearchQueryRequest, authentication);
  }
}
