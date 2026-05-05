/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;
import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;

import io.camunda.authentication.ConditionalOnCamundaGroupsEnabled;
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
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.validation.GroupValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenant;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.EntityType;
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

  private final GroupServices groupServices;
  private final MappingRuleServices mappingRuleServices;
  private final RoleServices roleServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GroupMapper groupMapper;

  public GroupController(
      final GroupServices groupServices,
      final MappingRuleServices mappingRuleServices,
      final RoleServices roleServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.groupServices = groupServices;
    this.mappingRuleServices = mappingRuleServices;
    this.roleServices = roleServices;
    this.authenticationProvider = authenticationProvider;
    groupMapper =
        new GroupMapper(new GroupRequestValidator(new GroupValidator(identifierValidator)));
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createGroup(
      @RequestBody final GroupCreateRequest createGroupRequest,
      @PhysicalTenant final String physicalTenantId) {
    return groupMapper
        .toGroupCreateRequest(createGroupRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> createGroup(req, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> updateGroup(
      @PathVariable final String groupId,
      @RequestBody final GroupUpdateRequest groupUpdateRequest,
      @PhysicalTenant final String physicalTenantId) {
    return groupMapper
        .toGroupUpdateRequest(groupUpdateRequest, groupId)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> updateGroup(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> deleteGroup(
      @PathVariable final String groupId, @PhysicalTenant final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> groupServices.deleteGroup(groupId, authentication, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{groupId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> assignUserToGroup(
      @PathVariable final String groupId,
      @PathVariable final String username,
      @PhysicalTenant final String physicalTenantId) {
    return groupMapper
        .toGroupMemberRequest(groupId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> assignMember(req, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{groupId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> assignClientToGroup(
      @PathVariable final String groupId,
      @PathVariable final String clientId,
      @PhysicalTenant final String physicalTenantId) {
    return groupMapper
        .toGroupMemberRequest(groupId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> assignMember(req, physicalTenantId));
  }

  @CamundaPutMapping(path = "/{groupId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> assignMappingRuleToGroup(
      @PathVariable final String groupId,
      @PathVariable final String mappingRuleId,
      @PhysicalTenant final String physicalTenantId) {
    return groupMapper
        .toGroupMemberRequest(groupId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> assignMember(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{groupId}/users/{username}")
  public CompletableFuture<ResponseEntity<Object>> unassignUserFromGroup(
      @PathVariable final String groupId,
      @PathVariable final String username,
      @PhysicalTenant final String physicalTenantId) {
    return groupMapper
        .toGroupMemberRequest(groupId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> unassignMember(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{groupId}/clients/{clientId}")
  public CompletableFuture<ResponseEntity<Object>> unassignClientFromGroup(
      @PathVariable final String groupId,
      @PathVariable final String clientId,
      @PhysicalTenant final String physicalTenantId) {
    return groupMapper
        .toGroupMemberRequest(groupId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> unassignMember(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{groupId}/mapping-rules/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> unassignMappingRuleFromGroup(
      @PathVariable final String groupId,
      @PathVariable final String mappingRuleId,
      @PhysicalTenant final String physicalTenantId) {
    return groupMapper
        .toGroupMemberRequest(groupId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> unassignMember(req, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{groupId}/users/search")
  public ResponseEntity<GroupUserSearchResult> usersByGroup(
      @PathVariable final String groupId,
      @RequestBody(required = false) final GroupUserSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toGroupMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            groupQuery -> searchUsersInGroup(groupId, groupQuery, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{groupId}/mapping-rules/search")
  public ResponseEntity<MappingRuleSearchQueryResult> mappingRulesByGroup(
      @PathVariable final String groupId,
      @RequestBody(required = false) final MappingRuleSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toMappingRuleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mappingRuleQuery -> searchMappingsInGroup(groupId, mappingRuleQuery, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{groupId}/roles/search")
  public ResponseEntity<RoleSearchQueryResult> rolesByGroup(
      @PathVariable final String groupId,
      @RequestBody(required = false) final RoleSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            userQuery -> searchRolesInGroup(groupId, userQuery, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{groupId}/clients/search")
  public ResponseEntity<GroupClientSearchResult> clientsByGroup(
      @PathVariable final String groupId,
      @RequestBody(required = false) final GroupClientSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toGroupMemberQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            groupMemberQuery -> searchClientsInGroup(groupId, groupMemberQuery, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{groupId}")
  public ResponseEntity<Object> getGroup(
      @PathVariable final String groupId, @PhysicalTenant final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toGroup(
                  groupServices.getGroup(groupId, authentication, physicalTenantId)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<GroupSearchQueryResult> searchGroups(
      @RequestBody(required = false) final GroupSearchQueryRequest query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toGroupQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(q, physicalTenantId));
  }

  private ResponseEntity<GroupSearchQueryResult> search(
      final GroupQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = groupServices.search(query, authentication, physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> createGroup(
      final GroupDTO groupDTO, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> groupServices.createGroup(groupDTO, authentication, physicalTenantId),
        ResponseMapper::toGroupCreateResponse,
        HttpStatus.CREATED);
  }

  public CompletableFuture<ResponseEntity<Object>> updateGroup(
      final GroupDTO updateGroupRequest, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            groupServices.updateGroup(
                updateGroupRequest.groupId(),
                updateGroupRequest.name(),
                updateGroupRequest.description(),
                authentication,
                physicalTenantId),
        ResponseMapper::toGroupUpdateResponse,
        HttpStatus.OK);
  }

  public CompletableFuture<ResponseEntity<Object>> assignMember(
      final GroupMemberDTO request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> groupServices.assignMember(request, authentication, physicalTenantId));
  }

  private ResponseEntity<GroupUserSearchResult> searchUsersInGroup(
      final String groupId,
      final GroupMemberQuery groupMemberQuery,
      final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          groupServices.searchMembers(
              buildGroupMemberQuery(groupId, EntityType.USER, groupMemberQuery),
              authentication,
              physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<GroupClientSearchResult> searchClientsInGroup(
      final String groupId,
      final GroupMemberQuery groupMemberQuery,
      final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result =
          groupServices.searchMembers(
              buildGroupMemberQuery(groupId, EntityType.CLIENT, groupMemberQuery),
              authentication,
              physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toGroupClientSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<MappingRuleSearchQueryResult> searchMappingsInGroup(
      final String groupId,
      final MappingRuleQuery mappingRuleQuery,
      final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedMappingQuery = buildMappingQuery(groupId, mappingRuleQuery);
      final var result =
          mappingRuleServices.search(composedMappingQuery, authentication, physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<RoleSearchQueryResult> searchRolesInGroup(
      final String groupId, final RoleQuery roleQuery, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var composedRoleQuery = buildRoleQuery(groupId, roleQuery);
      final var result = roleServices.search(composedRoleQuery, authentication, physicalTenantId);
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
      final GroupMemberDTO request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> groupServices.removeMember(request, authentication, physicalTenantId));
  }
}
