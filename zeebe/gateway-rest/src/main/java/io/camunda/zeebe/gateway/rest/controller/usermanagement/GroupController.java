/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.security.api.model.authz.EntityType.GROUP;
import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.GroupMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.GroupRequestValidator;
import io.camunda.gateway.protocol.model.GroupClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupClientSearchResult;
import io.camunda.gateway.protocol.model.GroupCreateRequest;
import io.camunda.gateway.protocol.model.GroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupSearchQueryResult;
import io.camunda.gateway.protocol.model.GroupUpdateRequest;
import io.camunda.gateway.protocol.model.GroupUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupUserSearchResult;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryRequest;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryResult;
import io.camunda.gateway.protocol.model.RoleSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleSearchQueryResult;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.spring.annotation.ConditionalOnCamundaGroupsEnabled;
import io.camunda.security.validation.GroupValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/groups")
@ConditionalOnCamundaGroupsEnabled
public class GroupController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GroupMapper groupMapper;

  public GroupController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    groupMapper =
        new GroupMapper(new GroupRequestValidator(new GroupValidator(identifierValidator)));
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createGroup(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final GroupCreateRequest createGroupRequest) {
    return groupMapper
        .toGroupCreateRequest(createGroupRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> createGroup(serviceRegistry.groupServices(physicalTenantId), request));
  }

  @CamundaPutMapping(path = "/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> updateGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @RequestBody final GroupUpdateRequest groupUpdateRequest) {
    return groupMapper
        .toGroupUpdateRequest(groupUpdateRequest, groupId)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> updateGroup(serviceRegistry.groupServices(physicalTenantId), request));
  }

  @CamundaDeleteMapping(path = "/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> deleteGroup(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final String groupId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> serviceRegistry.groupServices(physicalTenantId).deleteGroup(groupId, authentication));
  }

  @CamundaPutMapping(path = "/{groupId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> assignUserToGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @PathVariable final String username) {
    return groupMapper
        .toGroupMemberRequest(groupId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> assignMember(serviceRegistry.groupServices(physicalTenantId), request));
  }

  @CamundaPutMapping(path = "/{groupId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> assignClientToGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @PathVariable final String clientId) {
    return groupMapper
        .toGroupMemberRequest(groupId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> assignMember(serviceRegistry.groupServices(physicalTenantId), request));
  }

  @CamundaPutMapping(path = "/{groupId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> assignMappingRuleToGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @PathVariable final String mappingRuleId) {
    return groupMapper
        .toGroupMemberRequest(groupId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> assignMember(serviceRegistry.groupServices(physicalTenantId), request));
  }

  @CamundaDeleteMapping(path = "/{groupId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> unassignUserFromGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @PathVariable final String username) {
    return groupMapper
        .toGroupMemberRequest(groupId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> unassignMember(serviceRegistry.groupServices(physicalTenantId), request));
  }

  @CamundaDeleteMapping(path = "/{groupId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> unassignClientFromGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @PathVariable final String clientId) {
    return groupMapper
        .toGroupMemberRequest(groupId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> unassignMember(serviceRegistry.groupServices(physicalTenantId), request));
  }

  @CamundaDeleteMapping(path = "/{groupId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignMappingRuleFromGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @PathVariable final String mappingRuleId) {
    return groupMapper
        .toGroupMemberRequest(groupId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> unassignMember(serviceRegistry.groupServices(physicalTenantId), request));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{groupId}/users/search")
  public ResponseEntity<GroupUserSearchResult> usersByGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @RequestBody(required = false) final GroupUserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toGroupMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            groupQuery ->
                searchUsersInGroup(
                    serviceRegistry.groupServices(physicalTenantId), groupId, groupQuery));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{groupId}/mapping-rules/search")
  public ResponseEntity<MappingRuleSearchQueryResult> mappingRulesByGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @RequestBody(required = false) final MappingRuleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingRuleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingRuleQuery ->
                searchMappingsInGroup(
                    serviceRegistry.mappingRuleServices(physicalTenantId),
                    groupId,
                    mappingRuleQuery));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{groupId}/roles/search")
  public ResponseEntity<RoleSearchQueryResult> rolesByGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @RequestBody(required = false) final RoleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            userQuery ->
                searchRolesInGroup(
                    serviceRegistry.roleServices(physicalTenantId), groupId, userQuery));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{groupId}/clients/search")
  public ResponseEntity<GroupClientSearchResult> clientsByGroup(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String groupId,
      @RequestBody(required = false) final GroupClientSearchQueryRequest query) {
    return SearchQueryRequestMapper.toGroupMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            groupMemberQuery ->
                searchClientsInGroup(
                    serviceRegistry.groupServices(physicalTenantId), groupId, groupMemberQuery));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{groupId}")
  public ResponseEntity<Object> getGroup(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final String groupId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toGroup(
                  serviceRegistry
                      .groupServices(physicalTenantId)
                      .getGroup(groupId, authentication)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<GroupSearchQueryResult> searchGroups(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final GroupSearchQueryRequest query) {
    return SearchQueryRequestMapper.toGroupQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(serviceRegistry.groupServices(physicalTenantId), q));
  }

  private ResponseEntity<GroupSearchQueryResult> search(
      final GroupServices groupServices, final GroupQuery query) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = groupServices.search(query, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> createGroup(
      final GroupServices groupServices, final GroupDTO groupDTO) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> groupServices.createGroup(groupDTO, authentication),
        ResponseMapper::toGroupCreateResponse,
        HttpStatus.CREATED);
  }

  public CompletableFuture<ResponseEntity<Object>> updateGroup(
      final GroupServices groupServices, final GroupDTO updateGroupRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            groupServices.updateGroup(
                updateGroupRequest.groupId(),
                updateGroupRequest.name(),
                updateGroupRequest.description(),
                authentication),
        ResponseMapper::toGroupUpdateResponse,
        HttpStatus.OK);
  }

  public CompletableFuture<ResponseEntity<Object>> assignMember(
      final GroupServices groupServices, final GroupMemberDTO request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> groupServices.assignMember(request, authentication));
  }

  private ResponseEntity<GroupUserSearchResult> searchUsersInGroup(
      final GroupServices groupServices,
      final String groupId,
      final GroupMemberQuery groupMemberQuery) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          groupServices.searchMembers(
              buildGroupMemberQuery(groupId, EntityType.USER, groupMemberQuery), authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<GroupClientSearchResult> searchClientsInGroup(
      final GroupServices groupServices,
      final String groupId,
      final GroupMemberQuery groupMemberQuery) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          groupServices.searchMembers(
              buildGroupMemberQuery(groupId, EntityType.CLIENT, groupMemberQuery), authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupClientSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<MappingRuleSearchQueryResult> searchMappingsInGroup(
      final MappingRuleServices mappingRuleServices,
      final String groupId,
      final MappingRuleQuery mappingRuleQuery) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedMappingQuery = buildMappingQuery(groupId, mappingRuleQuery);
      final var result = mappingRuleServices.search(composedMappingQuery, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<RoleSearchQueryResult> searchRolesInGroup(
      final RoleServices roleServices, final String groupId, final RoleQuery roleQuery) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedRoleQuery = buildRoleQuery(groupId, roleQuery);
      final var result = roleServices.search(composedRoleQuery, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private GroupMemberQuery buildGroupMemberQuery(
      final String groupId, final EntityType memberType, final GroupMemberQuery groupMemberQuery) {
    return groupMemberQuery.toBuilder()
        .filter(
            groupMemberQuery.filter().toBuilder().groupId(groupId).memberType(memberType).build())
        .build();
  }

  private MappingRuleQuery buildMappingQuery(
      final String groupId, final MappingRuleQuery mappingRuleQuery) {
    return mappingRuleQuery.toBuilder()
        .filter(mappingRuleQuery.filter().toBuilder().groupId(groupId).build())
        .build();
  }

  private RoleQuery buildRoleQuery(final String groupId, final RoleQuery roleQuery) {
    return roleQuery.toBuilder()
        .filter(roleQuery.filter().toBuilder().memberId(groupId).childMemberType(GROUP).build())
        .build();
  }

  public CompletableFuture<ResponseEntity<Object>> unassignMember(
      final GroupServices groupServices, final GroupMemberDTO request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> groupServices.removeMember(request, authentication));
  }
}
