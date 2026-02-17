/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.AUDIT_LOG;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.BATCH;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.CLUSTER_VARIABLE;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DOCUMENT;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.GLOBAL_LISTENER;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.GROUP;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.ROLE;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.SYSTEM;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.TENANT;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.USER;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.USER_TASK;
import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static io.camunda.zeebe.protocol.record.value.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.DELETE_DECISION_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.DELETE_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_DECISION_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_DECISION_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_JOB_METRIC;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_USAGE_METRIC;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_USER_TASK;
import static io.camunda.zeebe.protocol.record.value.PermissionType.UPDATE_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.UPDATE_USER_TASK;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record Authorization<T>(
    @JsonProperty("resource_type") AuthorizationResourceType resourceType,
    @JsonProperty("permission_type") PermissionType permissionType,
    @JsonProperty("resource_ids") List<String> resourceIds,
    @JsonIgnore Function<T, String> resourceIdSupplier,
    @JsonProperty("resource_property_names") Set<String> resourcePropertyNames,
    @JsonIgnore Predicate<T> condition,
    @JsonProperty("transitive") boolean transitive) {

  // USER TASK property names
  public static final String PROP_ASSIGNEE = "assignee";
  public static final String PROP_CANDIDATE_USERS = "candidateUsers";
  public static final String PROP_CANDIDATE_GROUPS = "candidateGroups";

  public boolean hasAnyResourceIds() {
    return resourceIds != null && !resourceIds.isEmpty();
  }

  public boolean hasAnyResourcePropertyNames() {
    return resourcePropertyNames != null && !resourcePropertyNames.isEmpty();
  }

  public boolean hasAnyResourceAccess() {
    return hasAnyResourceIds() || hasAnyResourcePropertyNames();
  }

  public static <T> Authorization<T> withAuthorization(
      final Authorization<T> authorization, final String resourceId) {
    return authorization.withResourceId(resourceId);
  }

  public static <T> Authorization<T> withAuthorization(
      final Authorization<T> authorization, final Function<T, String> resourceIdSupplier) {
    return authorization.withResourceIdSupplier(resourceIdSupplier);
  }

  public Authorization<T> withResourceId(final String resourceId) {
    return withResourceIds(List.of(resourceId));
  }

  public Authorization<T> withResourceIds(final List<String> resourceIds) {
    return new Authorization<>(
        resourceType(),
        permissionType(),
        List.copyOf(resourceIds),
        resourceIdSupplier(),
        resourcePropertyNames(),
        condition(),
        transitive());
  }

  public Authorization<T> withResourceIdSupplier(final Function<T, String> resourceIdSupplier) {
    return new Authorization<>(
        resourceType(),
        permissionType(),
        resourceIds(),
        resourceIdSupplier,
        resourcePropertyNames(),
        condition(),
        transitive());
  }

  public Authorization<T> withResourcePropertyNames(final Set<String> resourcePropertyNames) {
    return new Authorization<>(
        resourceType(),
        permissionType(),
        resourceIds(),
        resourceIdSupplier(),
        resourcePropertyNames,
        condition(),
        transitive());
  }

  public Authorization<T> withCondition(final Predicate<T> condition) {
    return new Authorization<>(
        resourceType(),
        permissionType(),
        resourceIds(),
        resourceIdSupplier(),
        resourcePropertyNames(),
        condition,
        transitive());
  }

  public static <T> Authorization<T> of(final Function<Builder<T>, Builder<T>> builderFunction) {
    return builderFunction.apply(new Builder<>()).build();
  }

  public boolean appliesTo(final T document) {
    return condition == null || condition.test(document);
  }

  @JsonIgnore
  public boolean isWildcard() {
    return resourceIds != null
        && resourceIds.stream().anyMatch(id -> WILDCARD.getResourceId().equals(id));
  }

  public static class Builder<T> {
    private AuthorizationResourceType resourceType;
    private PermissionType permissionType;
    private List<String> resourceIds;
    private Function<T, String> resourceIdSupplier;
    private Set<String> resourcePropertyNames;
    private Predicate<T> condition = null;
    private boolean transitive = false;

    public Builder<T> resourceType(final AuthorizationResourceType resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder<T> permissionType(final PermissionType permissionType) {
      this.permissionType = permissionType;
      return this;
    }

    public Builder<T> condition(final Predicate<T> condition) {
      this.condition = condition;
      return this;
    }

    public Builder<T> transitive() {
      transitive = true;
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

    public Builder<T> userTask() {
      return resourceType(USER_TASK);
    }

    public Builder<T> system() {
      return resourceType(SYSTEM);
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

    public Builder<T> readUsageMetric() {
      return permissionType(READ_USAGE_METRIC);
    }

    public Builder<T> readJobMetric() {
      return permissionType(READ_JOB_METRIC);
    }

    public Builder<T> deleteProcessInstance() {
      return permissionType(DELETE_PROCESS_INSTANCE);
    }

    public Builder<T> deleteDecisionInstance() {
      return permissionType(DELETE_DECISION_INSTANCE);
    }

    public Builder<T> batchOperation() {
      return resourceType(BATCH);
    }

    public Builder<T> document() {
      return resourceType(DOCUMENT);
    }

    public Builder<T> auditLog() {
      return resourceType(AUDIT_LOG);
    }

    public Builder<T> clusterVariable() {
      return resourceType(CLUSTER_VARIABLE);
    }

    public Builder<T> globalListener() {
      return resourceType(GLOBAL_LISTENER);
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

    public Builder<T> resourcePropertyNames(final Set<String> resourcePropertyNames) {
      this.resourcePropertyNames = resourcePropertyNames;
      return this;
    }

    public Builder<T> authorizedByProperty(final String propertyName) {
      if (this.resourcePropertyNames == null) {
        this.resourcePropertyNames = new HashSet<>();
      }
      this.resourcePropertyNames.add(propertyName);
      return this;
    }

    public Builder<T> authorizedByAssignee() {
      return authorizedByProperty(PROP_ASSIGNEE);
    }

    public Builder<T> authorizedByCandidateUsers() {
      return authorizedByProperty(PROP_CANDIDATE_USERS);
    }

    public Builder<T> authorizedByCandidateGroups() {
      return authorizedByProperty(PROP_CANDIDATE_GROUPS);
    }

    /**
     * Fluent connector indicating the next condition is an OR alternative. This is purely for
     * readability.
     */
    public Builder<T> or() {
      return this;
    }

    public Authorization<T> build() {
      return new Authorization<>(
          resourceType,
          permissionType,
          resourceIds,
          resourceIdSupplier,
          resourcePropertyNames,
          condition,
          transitive);
    }
  }
}
