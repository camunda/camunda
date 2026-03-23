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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupClientSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupCreateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupUserSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for Group operations. Implements request mapping, service delegation, and
 * response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface GroupServiceAdapter {

  ResponseEntity<Object> createGroup(
      GeneratedGroupCreateRequestStrictContract groupCreateRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> searchGroups(
      GeneratedGroupSearchQueryRequestStrictContract groupSearchQueryRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getGroup(String groupId, CamundaAuthentication authentication);

  ResponseEntity<Object> updateGroup(
      String groupId,
      GeneratedGroupUpdateRequestStrictContract groupUpdateRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> deleteGroup(String groupId, CamundaAuthentication authentication);

  ResponseEntity<Void> assignUserToGroup(
      String groupId, String username, CamundaAuthentication authentication);

  ResponseEntity<Void> unassignUserFromGroup(
      String groupId, String username, CamundaAuthentication authentication);

  ResponseEntity<Object> searchUsersForGroup(
      String groupId,
      GeneratedGroupUserSearchQueryRequestStrictContract groupUserSearchQueryRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> assignClientToGroup(
      String groupId, String clientId, CamundaAuthentication authentication);

  ResponseEntity<Void> unassignClientFromGroup(
      String groupId, String clientId, CamundaAuthentication authentication);

  ResponseEntity<Object> searchClientsForGroup(
      String groupId,
      GeneratedGroupClientSearchQueryRequestStrictContract groupClientSearchQueryRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> assignMappingRuleToGroup(
      String groupId, String mappingRuleId, CamundaAuthentication authentication);

  ResponseEntity<Void> unassignMappingRuleFromGroup(
      String groupId, String mappingRuleId, CamundaAuthentication authentication);

  ResponseEntity<Object> searchMappingRulesForGroup(
      String groupId,
      GeneratedMappingRuleSearchQueryRequestStrictContract mappingRuleSearchQueryRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> searchRolesForGroup(
      String groupId,
      GeneratedRoleSearchQueryRequestStrictContract roleSearchQueryRequest,
      CamundaAuthentication authentication);
}
