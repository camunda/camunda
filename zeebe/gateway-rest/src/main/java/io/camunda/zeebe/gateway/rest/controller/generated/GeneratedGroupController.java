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

import io.camunda.authentication.ConditionalOnCamundaGroupsEnabled;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupClientSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupCreateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupUserSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@ConditionalOnCamundaGroupsEnabled
@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedGroupController {

  private final GroupServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedGroupController(
      final GroupServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/groups",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> createGroup(
      @RequestBody final GeneratedGroupCreateRequestStrictContract groupCreateRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createGroup(groupCreateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/groups/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchGroups(
      @RequestBody(required = false) final GeneratedGroupSearchQueryRequestStrictContract groupSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchGroups(groupSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/groups/{groupId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getGroup(
      @PathVariable("groupId") final String groupId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getGroup(groupId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/groups/{groupId}",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> updateGroup(
      @PathVariable("groupId") final String groupId,
      @RequestBody final GeneratedGroupUpdateRequestStrictContract groupUpdateRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateGroup(groupId, groupUpdateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/groups/{groupId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> deleteGroup(
      @PathVariable("groupId") final String groupId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteGroup(groupId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/groups/{groupId}/users/{username}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> assignUserToGroup(
      @PathVariable("groupId") final String groupId,
      @PathVariable("username") final String username
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignUserToGroup(groupId, username, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/groups/{groupId}/users/{username}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> unassignUserFromGroup(
      @PathVariable("groupId") final String groupId,
      @PathVariable("username") final String username
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignUserFromGroup(groupId, username, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/groups/{groupId}/users/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchUsersForGroup(
      @PathVariable("groupId") final String groupId,
      @RequestBody(required = false) final GeneratedGroupUserSearchQueryRequestStrictContract groupUserSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchUsersForGroup(groupId, groupUserSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/groups/{groupId}/clients/{clientId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> assignClientToGroup(
      @PathVariable("groupId") final String groupId,
      @PathVariable("clientId") final String clientId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignClientToGroup(groupId, clientId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/groups/{groupId}/clients/{clientId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> unassignClientFromGroup(
      @PathVariable("groupId") final String groupId,
      @PathVariable("clientId") final String clientId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignClientFromGroup(groupId, clientId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/groups/{groupId}/clients/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchClientsForGroup(
      @PathVariable("groupId") final String groupId,
      @RequestBody(required = false) final GeneratedGroupClientSearchQueryRequestStrictContract groupClientSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchClientsForGroup(groupId, groupClientSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/groups/{groupId}/mapping-rules/{mappingRuleId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> assignMappingRuleToGroup(
      @PathVariable("groupId") final String groupId,
      @PathVariable("mappingRuleId") final String mappingRuleId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.assignMappingRuleToGroup(groupId, mappingRuleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/groups/{groupId}/mapping-rules/{mappingRuleId}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> unassignMappingRuleFromGroup(
      @PathVariable("groupId") final String groupId,
      @PathVariable("mappingRuleId") final String mappingRuleId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.unassignMappingRuleFromGroup(groupId, mappingRuleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/groups/{groupId}/mapping-rules/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchMappingRulesForGroup(
      @PathVariable("groupId") final String groupId,
      @RequestBody(required = false) final GeneratedMappingRuleSearchQueryRequestStrictContract mappingRuleSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchMappingRulesForGroup(groupId, mappingRuleSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/groups/{groupId}/roles/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchRolesForGroup(
      @PathVariable("groupId") final String groupId,
      @RequestBody(required = false) final GeneratedRoleSearchQueryRequestStrictContract roleSearchQueryRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchRolesForGroup(groupId, roleSearchQueryRequest, authentication);
  }
}
