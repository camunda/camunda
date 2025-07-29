/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.BATCH;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.GROUP;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.ROLE;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.TENANT;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.USAGE_METRIC;
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.function.Function;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record Authorization<T>(
    @JsonProperty("resource_type") AuthorizationResourceType resourceType,
    @JsonProperty("permission_type") PermissionType permissionType,
    @JsonProperty("resource_ids") List<String> resourceIds,
    @JsonIgnore Function<T, String> resourceIdSupplier) {

  public static final String WILDCARD = "*";

  public static <T> Authorization<T> withAuthorization(
      final Authorization<T> authorization, final String resourceId) {
    return of(
        b ->
            b.resourceType(authorization.resourceType())
                .permissionType(authorization.permissionType())
                .resourceIds(List.of(resourceId)));
  }

  public static <T> Authorization<T> withAuthorization(
      final Authorization<T> authorization, final Function<T, String> resourceIdSupplier) {
    return of(
        b ->
            b.resourceType(authorization.resourceType())
                .permissionType(authorization.permissionType())
                .resourceIdSupplier(resourceIdSupplier));
  }

  public static <T> Authorization<T> of(final Function<Builder<T>, Builder<T>> builderFunction) {
    return builderFunction.apply(new Builder<>()).build();
  }

  public static class Builder<T> {
    private AuthorizationResourceType resourceType;
    private PermissionType permissionType;
    private List<String> resourceIds;
    private Function<T, String> resourceIdSupplier;

    public Builder<T> resourceType(final AuthorizationResourceType resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder<T> permissionType(final PermissionType permissionType) {
      this.permissionType = permissionType;
      return this;
    }

    public Builder<T> processDefinition() {
      return resourceType(PROCESS_DEFINITION);
    }

    public Builder<T> decisionDefinition() {
      return resourceType(DECISION_DEFINITION);
    }

    public Builder<T> decisionRequirementsDefinition() {
      return resourceType(DECISION_REQUIREMENTS_DEFINITION);
    }

    public Builder<T> mappingRule() {
      return resourceType(MAPPING_RULE);
    }

    public Builder<T> role() {
      return resourceType(ROLE);
    }

    public Builder<T> group() {
      return resourceType(GROUP);
    }

    public Builder<T> tenant() {
      return resourceType(TENANT);
    }

    public Builder<T> authorization() {
      return resourceType(AUTHORIZATION);
    }

    public Builder<T> user() {
      return resourceType(USER);
    }

    public Builder<T> read() {
      return permissionType(READ);
    }

    public Builder<T> readProcessDefinition() {
      return permissionType(READ_PROCESS_DEFINITION);
    }

    public Builder<T> readDecisionDefinition() {
      return permissionType(READ_DECISION_DEFINITION);
    }

    public Builder<T> readProcessInstance() {
      return permissionType(READ_PROCESS_INSTANCE);
    }

    public Builder<T> createProcessInstance() {
      return permissionType(CREATE_PROCESS_INSTANCE);
    }

    public Builder<T> updateProcessInstance() {
      return permissionType(UPDATE_PROCESS_INSTANCE);
    }

    public Builder<T> readUserTask() {
      return permissionType(READ_USER_TASK);
    }

    public Builder<T> updateUserTask() {
      return permissionType(UPDATE_USER_TASK);
    }

    public Builder<T> readDecisionInstance() {
      return permissionType(READ_DECISION_INSTANCE);
    }

    public Builder<T> batchOperation() {
      return resourceType(BATCH);
    }

    public Builder<T> usageMetric() {
      return resourceType(USAGE_METRIC);
    }

    public Builder<T> resourceId(final String resourceId) {
      return resourceIds(List.of(resourceId));
    }

    public Builder<T> resourceIds(final List<String> resourceIds) {
      this.resourceIds = resourceIds;
      return this;
    }

    public Builder<T> resourceIdSupplier(final Function<T, String> resourceIdSupplier) {
      this.resourceIdSupplier = resourceIdSupplier;
      return this;
    }

    public Authorization<T> build() {
      return new Authorization<>(resourceType, permissionType, resourceIds, resourceIdSupplier);
    }
  }
}
