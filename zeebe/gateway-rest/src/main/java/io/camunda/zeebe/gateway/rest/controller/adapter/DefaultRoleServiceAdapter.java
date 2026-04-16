/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.RoleMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.RoleRequestValidator;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleCreateRequest;
import io.camunda.gateway.protocol.model.RoleGroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleUpdateRequest;
import io.camunda.gateway.protocol.model.RoleUserSearchQueryRequest;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.RoleValidator;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.gateway.rest.controller.generated.RoleServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultRoleServiceAdapter implements RoleServiceAdapter {

  private final RoleServices roleServices;
  private final MappingRuleServices mappingRuleServices;
  private final RoleMapper roleMapper;

  public DefaultRoleServiceAdapter(
      final RoleServices roleServices,
      final MappingRuleServices mappingRuleServices,
      final IdentifierValidator identifierValidator) {
    this.roleServices = roleServices;
    this.mappingRuleServices = mappingRuleServices;
    roleMapper = new RoleMapper(new RoleRequestValidator(new RoleValidator(identifierValidator)));
  }

  @Override
  public ResponseEntity<Object> createRole(
      final RoleCreateRequest roleCreateRequestStrict, final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleCreateRequest(roleCreateRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> roleServices.createRole(request, authentication),
                    ResponseMapper::toRoleCreateResponse,
                    HttpStatus.CREATED));
  }

  @Override
  public ResponseEntity<Object> searchRoles(
      final RoleSearchQueryRequest roleSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toRoleQueryStrict(roleSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = roleServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getRole(
      final String roleId, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toRole(roleServices.getRole(roleId, authentication)));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> updateRole(
      final String roleId,
      final RoleUpdateRequest roleUpdateRequestStrict,
      final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleUpdateRequest(roleUpdateRequestStrict, roleId)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> roleServices.updateRole(request, authentication),
                    ResponseMapper::toRoleUpdateResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> deleteRole(
      final String roleId, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(() -> roleServices.deleteRole(roleId, authentication));
  }

  @Override
  public ResponseEntity<Void> assignRoleToUser(
      final String roleId, final String username, final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleMemberRequest(roleId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(() -> roleServices.addMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignRoleFromUser(
      final String roleId, final String username, final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleMemberRequest(roleId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> roleServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchUsersForRole(
      final String roleId,
      final RoleUserSearchQueryRequest roleUserSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toRoleUserQueryStrict(roleUserSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    roleServices.searchMembers(
                        buildRoleMemberQuery(roleId, EntityType.USER, query), authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toRoleUserSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> assignRoleToClient(
      final String roleId, final String clientId, final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleMemberRequest(roleId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(() -> roleServices.addMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignRoleFromClient(
      final String roleId, final String clientId, final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleMemberRequest(roleId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> roleServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchClientsForRole(
      final String roleId,
      final RoleClientSearchQueryRequest roleClientSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toRoleClientQueryStrict(roleClientSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    roleServices.searchMembers(
                        buildRoleMemberQuery(roleId, EntityType.CLIENT, query), authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toRoleClientSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> assignRoleToGroup(
      final String roleId, final String groupId, final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleMemberRequest(roleId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(() -> roleServices.addMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignRoleFromGroup(
      final String roleId, final String groupId, final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleMemberRequest(roleId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> roleServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchGroupsForRole(
      final String roleId,
      final RoleGroupSearchQueryRequest roleGroupSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toRoleGroupQueryStrict(roleGroupSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    roleServices.searchMembers(
                        buildRoleMemberQuery(roleId, EntityType.GROUP, query), authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toRoleGroupSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> assignRoleToMappingRule(
      final String roleId, final String mappingRuleId, final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(() -> roleServices.addMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignRoleFromMappingRule(
      final String roleId, final String mappingRuleId, final CamundaAuthentication authentication) {
    return roleMapper
        .toRoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> roleServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchMappingRulesForRole(
      final String roleId,
      final MappingRuleSearchQueryRequest mappingRuleSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toMappingRuleQueryStrict(mappingRuleSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var composedQuery = buildMappingQuery(roleId, query);
                final var result = mappingRuleServices.search(composedQuery, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  private RoleMemberQuery buildRoleMemberQuery(
      final String roleId, final EntityType memberType, final RoleMemberQuery query) {
    return query.toBuilder()
        .filter(query.filter().toBuilder().roleId(roleId).memberType(memberType).build())
        .build();
  }

  private MappingRuleQuery buildMappingQuery(
      final String roleId, final MappingRuleQuery mappingRuleQuery) {
    return mappingRuleQuery.toBuilder()
        .filter(mappingRuleQuery.filter().toBuilder().roleId(roleId).build())
        .build();
  }
}
