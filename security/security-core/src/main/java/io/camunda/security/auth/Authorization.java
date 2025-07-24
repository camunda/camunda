/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.BATCH_OPERATION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.GROUP;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.ROLE;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.TENANT;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.USER;
import static io.camunda.zeebe.protocol.record.value.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_DECISION_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_DECISION_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_USER_TASK;
import static io.camunda.zeebe.protocol.record.value.PermissionType.UPDATE_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.UPDATE_USER_TASK;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.function.Function;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record Authorization(
    @JsonProperty("resource_type") AuthorizationResourceType resourceType,
    @JsonProperty("permission_type") PermissionType permissionType,
    @JsonProperty("resource_ids") List<String> resourceIds) {

  public static final String WILDCARD = "*";

  public static Authorization withResourceIds(
      final Authorization authorization, final List<String> resourceIds) {
    return of(
        b ->
            b.resourceType(authorization.resourceType())
                .permissionType(authorization.permissionType())
                .resourceIds(resourceIds));
  }

  public static Authorization of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static class Builder {
    private AuthorizationResourceType resourceType;
    private PermissionType permissionType;
    private List<String> resourceIds;

    public Builder resourceType(final AuthorizationResourceType resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder permissionType(final PermissionType permissionType) {
      this.permissionType = permissionType;
      return this;
    }

    public Builder processDefinition() {
      return resourceType(PROCESS_DEFINITION);
    }

    public Builder decisionDefinition() {
      return resourceType(DECISION_DEFINITION);
    }

    public Builder decisionRequirementsDefinition() {
      return resourceType(DECISION_REQUIREMENTS_DEFINITION);
    }

    public Builder mappingRule() {
      return resourceType(MAPPING_RULE);
    }

    public Builder role() {
      return resourceType(ROLE);
    }

    public Builder group() {
      return resourceType(GROUP);
    }

    public Builder tenant() {
      return resourceType(TENANT);
    }

    public Builder authorization() {
      return resourceType(AUTHORIZATION);
    }

    public Builder user() {
      return resourceType(USER);
    }

    public Builder read() {
      return permissionType(READ);
    }

    public Builder readProcessDefinition() {
      return permissionType(READ_PROCESS_DEFINITION);
    }

    public Builder readDecisionDefinition() {
      return permissionType(READ_DECISION_DEFINITION);
    }

    public Builder readProcessInstance() {
      return permissionType(READ_PROCESS_INSTANCE);
    }

    public Builder createProcessInstance() {
      return permissionType(CREATE_PROCESS_INSTANCE);
    }

    public Builder updateProcessInstance() {
      return permissionType(UPDATE_PROCESS_INSTANCE);
    }

    public Builder readUserTask() {
      return permissionType(READ_USER_TASK);
    }

    public Builder updateUserTask() {
      return permissionType(UPDATE_USER_TASK);
    }

    public Builder readDecisionInstance() {
      return permissionType(READ_DECISION_INSTANCE);
    }

    public Builder batchOperation() {
      return resourceType(BATCH_OPERATION);
    }

    public Builder resourceId(final String resourceId) {
      return resourceIds(List.of(resourceId));
    }

    public Builder resourceIds(final List<String> resourceIds) {
      this.resourceIds = resourceIds;
      return this;
    }

    public Builder authorizationScopes(final List<AuthorizationScope> authorizationScopes) {
      resourceIds = authorizationScopes.stream().map(AuthorizationScope::resourceId).toList();
      return this;
    }

    public Authorization build() {
      return new Authorization(resourceType, permissionType, resourceIds);
    }
  }
}
