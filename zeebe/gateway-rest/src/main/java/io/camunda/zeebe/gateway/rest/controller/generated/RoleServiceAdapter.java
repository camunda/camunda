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
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for Role operations.
 * Implements request mapping, service delegation, and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface RoleServiceAdapter {

  ResponseEntity<Object> createRole(
      GeneratedRoleCreateRequestStrictContract roleCreateRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchRoles(
      GeneratedRoleSearchQueryRequestStrictContract roleSearchQueryRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> getRole(
      String roleId,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> updateRole(
      String roleId,
      GeneratedRoleUpdateRequestStrictContract roleUpdateRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> deleteRole(
      String roleId,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> assignRoleToUser(
      String roleId,
      String username,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> unassignRoleFromUser(
      String roleId,
      String username,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchUsersForRole(
      String roleId,
      GeneratedRoleUserSearchQueryRequestStrictContract roleUserSearchQueryRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> assignRoleToClient(
      String roleId,
      String clientId,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> unassignRoleFromClient(
      String roleId,
      String clientId,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchClientsForRole(
      String roleId,
      GeneratedRoleClientSearchQueryRequestStrictContract roleClientSearchQueryRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> assignRoleToGroup(
      String roleId,
      String groupId,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> unassignRoleFromGroup(
      String roleId,
      String groupId,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchGroupsForRole(
      String roleId,
      GeneratedRoleGroupSearchQueryRequestStrictContract roleGroupSearchQueryRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> assignRoleToMappingRule(
      String roleId,
      String mappingRuleId,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> unassignRoleFromMappingRule(
      String roleId,
      String mappingRuleId,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchMappingRulesForRole(
      String roleId,
      GeneratedMappingRuleSearchQueryRequestStrictContract mappingRuleSearchQueryRequest,
      CamundaAuthentication authentication
  );
}
