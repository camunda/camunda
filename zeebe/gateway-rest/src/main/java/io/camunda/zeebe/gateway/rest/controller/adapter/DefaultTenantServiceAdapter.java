/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.TenantMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.TenantRequestValidator;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantCreateRequest;
import io.camunda.gateway.protocol.model.TenantGroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantUpdateRequest;
import io.camunda.gateway.protocol.model.TenantUserSearchQueryRequest;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.TenantValidator;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.gateway.rest.controller.generated.TenantServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultTenantServiceAdapter implements TenantServiceAdapter {

  private final TenantServices tenantServices;
  private final MappingRuleServices mappingRuleServices;
  private final RoleServices roleServices;
  private final TenantMapper tenantMapper;

  public DefaultTenantServiceAdapter(
      final TenantServices tenantServices,
      final MappingRuleServices mappingRuleServices,
      final RoleServices roleServices,
      final IdentifierValidator identifierValidator) {
    this.tenantServices = tenantServices;
    this.mappingRuleServices = mappingRuleServices;
    this.roleServices = roleServices;
    tenantMapper =
        new TenantMapper(new TenantRequestValidator(new TenantValidator(identifierValidator)));
  }

  @Override
  public ResponseEntity<Object> createTenant(
      final TenantCreateRequest tenantCreateRequest, final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantCreateDto(tenantCreateRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.createTenant(request, authentication),
                    ResponseMapper::toTenantCreateResponse,
                    HttpStatus.CREATED));
  }

  @Override
  public ResponseEntity<Object> searchTenants(
      final TenantSearchQueryRequest tenantSearchQueryRequest,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toTenantQuery(tenantSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = tenantServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toTenantSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getTenant(
      final String tenantId, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toTenant(tenantServices.getById(tenantId, authentication)));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> updateTenant(
      final String tenantId,
      final TenantUpdateRequest tenantUpdateRequest,
      final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantUpdateDto(tenantId, tenantUpdateRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.updateTenant(request, authentication),
                    ResponseMapper::toTenantUpdateResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> deleteTenant(
      final String tenantId, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(() -> tenantServices.deleteTenant(tenantId, authentication));
  }

  @Override
  public ResponseEntity<Void> assignUserToTenant(
      final String tenantId, final String username, final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.addMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignUserFromTenant(
      final String tenantId, final String username, final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, username, EntityType.USER)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchUsersForTenant(
      final String tenantId,
      final TenantUserSearchQueryRequest tenantUserSearchQueryRequest,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toTenantMemberQuery(tenantUserSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    tenantServices.searchMembers(
                        buildTenantMemberQuery(tenantId, EntityType.USER, query), authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toTenantUserSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> assignClientToTenant(
      final String tenantId, final String clientId, final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.addMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignClientFromTenant(
      final String tenantId, final String clientId, final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, clientId, EntityType.CLIENT)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchClientsForTenant(
      final String tenantId,
      final TenantClientSearchQueryRequest tenantClientSearchQueryRequest,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toTenantMemberQuery(tenantClientSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    tenantServices.searchMembers(
                        buildTenantMemberQuery(tenantId, EntityType.CLIENT, query), authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toTenantClientSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> assignGroupToTenant(
      final String tenantId, final String groupId, final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.addMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignGroupFromTenant(
      final String tenantId, final String groupId, final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, groupId, EntityType.GROUP)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchGroupIdsForTenant(
      final String tenantId,
      final TenantGroupSearchQueryRequest tenantGroupSearchQueryRequest,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toTenantMemberQuery(tenantGroupSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    tenantServices.searchMembers(
                        buildTenantMemberQuery(tenantId, EntityType.GROUP, query), authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toTenantGroupSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> searchRolesForTenant(
      final String tenantId,
      final RoleSearchQueryRequest roleSearchQueryRequest,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toRoleQuery(roleSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var composedQuery = buildRoleQuery(tenantId, query);
                final var result = roleServices.search(composedQuery, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> assignRoleToTenant(
      final String tenantId, final String roleId, final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, roleId, EntityType.ROLE)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.addMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignRoleFromTenant(
      final String tenantId, final String roleId, final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, roleId, EntityType.ROLE)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> assignMappingRuleToTenant(
      final String tenantId,
      final String mappingRuleId,
      final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.addMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Void> unassignMappingRuleFromTenant(
      final String tenantId,
      final String mappingRuleId,
      final CamundaAuthentication authentication) {
    return tenantMapper
        .toTenantMemberRequest(tenantId, mappingRuleId, EntityType.MAPPING_RULE)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> tenantServices.removeMember(request, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchMappingRulesForTenant(
      final String tenantId,
      final MappingRuleSearchQueryRequest mappingRuleSearchQueryRequest,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toMappingRuleQuery(mappingRuleSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var composedQuery = buildMappingRuleQuery(tenantId, query);
                final var result = mappingRuleServices.search(composedQuery, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  private TenantMemberQuery buildTenantMemberQuery(
      final String tenantId, final EntityType memberType, final TenantMemberQuery query) {
    return query.toBuilder()
        .filter(query.filter().toBuilder().tenantId(tenantId).memberType(memberType).build())
        .build();
  }

  private MappingRuleQuery buildMappingRuleQuery(
      final String tenantId, final MappingRuleQuery mappingRuleQuery) {
    return mappingRuleQuery.toBuilder()
        .filter(mappingRuleQuery.filter().toBuilder().tenantId(tenantId).build())
        .build();
  }

  private RoleQuery buildRoleQuery(final String tenantId, final RoleQuery roleQuery) {
    return roleQuery.toBuilder()
        .filter(roleQuery.filter().toBuilder().tenantId(tenantId).build())
        .build();
  }
}
