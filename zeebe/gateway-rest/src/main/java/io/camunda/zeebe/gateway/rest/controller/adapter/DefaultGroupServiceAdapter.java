/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;

import io.camunda.authentication.ConditionalOnCamundaGroupsEnabled;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.GroupMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.GroupRequestValidator;
import io.camunda.gateway.protocol.model.GroupClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupCreateRequest;
import io.camunda.gateway.protocol.model.GroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupUpdateRequest;
import io.camunda.gateway.protocol.model.GroupUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleSearchQueryRequest;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.validation.GroupValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.gateway.rest.controller.generated.GroupServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnCamundaGroupsEnabled
public class DefaultGroupServiceAdapter implements GroupServiceAdapter {

  private final GroupServices groupServices;
  private final MappingRuleServices mappingRuleServices;
  private final RoleServices roleServices;
  private final GroupMapper groupMapper;

  public DefaultGroupServiceAdapter(
      final GroupServices groupServices,
      final MappingRuleServices mappingRuleServices,
      final RoleServices roleServices,
      final IdentifierValidator identifierValidator) {
    this.groupServices = groupServices;
    this.mappingRuleServices = mappingRuleServices;
    this.roleServices = roleServices;
    groupMapper =
        new GroupMapper(new GroupRequestValidator(new GroupValidator(identifierValidator)));
  }

  @Override
  public ResponseEntity<Object> createGroup(
      final GroupCreateRequest groupCreateRequestStrict,
      final CamundaAuthentication authentication) {
    return groupMapper
        .toGroupCreateRequest(groupCreateRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            dto ->
                RequestExecutor.executeSync(
                    () -> groupServices.createGroup(dto, authentication),
                    ResponseMapper::toGroupCreateResponse,
                    HttpStatus.CREATED));
  }

  @Override
  public ResponseEntity<Object> searchGroups(
      final GroupSearchQueryRequest groupSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toGroupQueryStrict(groupSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = groupServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toGroupSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getGroup(
      final String groupId, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toGroup(groupServices.getGroup(groupId, authentication)));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> updateGroup(
      final String groupId,
      final GroupUpdateRequest groupUpdateRequestStrict,
      final CamundaAuthentication authentication) {
    return groupMapper
        .toGroupUpdateRequest(groupUpdateRequestStrict, groupId)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            dto ->
                RequestExecutor.executeSync(
                    () ->
                        groupServices.updateGroup(
                            dto.groupId(), dto.name(), dto.description(), authentication),
                    ResponseMapper::toGroupUpdateResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> deleteGroup(
      final String groupId, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(() -> groupServices.deleteGroup(groupId, authentication));
  }

  @Override
  public ResponseEntity<Void> assignUserToGroup(
      final String groupId, final String username, final CamundaAuthentication authentication) {
    return groupMapper
        .toGroupMemberRequest(groupId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> groupServices.assignMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignUserFromGroup(
      final String groupId, final String username, final CamundaAuthentication authentication) {
    return groupMapper
        .toGroupMemberRequest(groupId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> groupServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchUsersForGroup(
      final String groupId,
      final GroupUserSearchQueryRequest groupUserSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toGroupUserQueryStrict(groupUserSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    groupServices.searchMembers(
                        buildGroupMemberQuery(groupId, EntityType.USER, query), authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toGroupUserSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> assignClientToGroup(
      final String groupId, final String clientId, final CamundaAuthentication authentication) {
    return groupMapper
        .toGroupMemberRequest(groupId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> groupServices.assignMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignClientFromGroup(
      final String groupId, final String clientId, final CamundaAuthentication authentication) {
    return groupMapper
        .toGroupMemberRequest(groupId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> groupServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchClientsForGroup(
      final String groupId,
      final GroupClientSearchQueryRequest groupClientSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toGroupClientQueryStrict(groupClientSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    groupServices.searchMembers(
                        buildGroupMemberQuery(groupId, EntityType.CLIENT, query), authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toGroupClientSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> assignMappingRuleToGroup(
      final String groupId,
      final String mappingRuleId,
      final CamundaAuthentication authentication) {
    return groupMapper
        .toGroupMemberRequest(groupId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> groupServices.assignMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignMappingRuleFromGroup(
      final String groupId,
      final String mappingRuleId,
      final CamundaAuthentication authentication) {
    return groupMapper
        .toGroupMemberRequest(groupId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> groupServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchMappingRulesForGroup(
      final String groupId,
      final MappingRuleSearchQueryRequest mappingRuleSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toMappingRuleQueryStrict(mappingRuleSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var composedQuery = buildMappingQuery(groupId, query);
                final var result = mappingRuleServices.search(composedQuery, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> searchRolesForGroup(
      final String groupId,
      final RoleSearchQueryRequest roleSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toRoleQueryStrict(roleSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var composedQuery = buildRoleQuery(groupId, query);
                final var result = roleServices.search(composedQuery, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
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
}
