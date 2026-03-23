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
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantClientSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantCreateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantGroupSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantUserSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for Tenant operations. Implements request mapping, service delegation, and
 * response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface TenantServiceAdapter {

  ResponseEntity<Object> createTenant(
      GeneratedTenantCreateRequestStrictContract tenantCreateRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> searchTenants(
      GeneratedTenantSearchQueryRequestStrictContract tenantSearchQueryRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getTenant(String tenantId, CamundaAuthentication authentication);

  ResponseEntity<Object> updateTenant(
      String tenantId,
      GeneratedTenantUpdateRequestStrictContract tenantUpdateRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> deleteTenant(String tenantId, CamundaAuthentication authentication);

  ResponseEntity<Void> assignUserToTenant(
      String tenantId, String username, CamundaAuthentication authentication);

  ResponseEntity<Void> unassignUserFromTenant(
      String tenantId, String username, CamundaAuthentication authentication);

  ResponseEntity<Object> searchUsersForTenant(
      String tenantId,
      GeneratedTenantUserSearchQueryRequestStrictContract tenantUserSearchQueryRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> assignClientToTenant(
      String tenantId, String clientId, CamundaAuthentication authentication);

  ResponseEntity<Void> unassignClientFromTenant(
      String tenantId, String clientId, CamundaAuthentication authentication);

  ResponseEntity<Object> searchClientsForTenant(
      String tenantId,
      GeneratedTenantClientSearchQueryRequestStrictContract tenantClientSearchQueryRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> assignGroupToTenant(
      String tenantId, String groupId, CamundaAuthentication authentication);

  ResponseEntity<Void> unassignGroupFromTenant(
      String tenantId, String groupId, CamundaAuthentication authentication);

  ResponseEntity<Object> searchGroupIdsForTenant(
      String tenantId,
      GeneratedTenantGroupSearchQueryRequestStrictContract tenantGroupSearchQueryRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> searchRolesForTenant(
      String tenantId,
      GeneratedRoleSearchQueryRequestStrictContract roleSearchQueryRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> assignRoleToTenant(
      String tenantId, String roleId, CamundaAuthentication authentication);

  ResponseEntity<Void> unassignRoleFromTenant(
      String tenantId, String roleId, CamundaAuthentication authentication);

  ResponseEntity<Void> assignMappingRuleToTenant(
      String tenantId, String mappingRuleId, CamundaAuthentication authentication);

  ResponseEntity<Void> unassignMappingRuleFromTenant(
      String tenantId, String mappingRuleId, CamundaAuthentication authentication);

  ResponseEntity<Object> searchMappingRulesForTenant(
      String tenantId,
      GeneratedMappingRuleSearchQueryRequestStrictContract mappingRuleSearchQueryRequest,
      CamundaAuthentication authentication);
}
