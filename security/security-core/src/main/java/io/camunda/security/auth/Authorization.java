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
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Represents an authorization rule that controls access to resources within the Camunda platform.
 *
 * <p>An authorization specifies which operations (permission types) can be performed on which
 * resources (resource types and IDs) by an authenticated principal. The authorization system
 * supports advanced features including conditional and transitive authorizations.
 *
 * <h2>Core Components</h2>
 *
 * <ul>
 *   <li><b>Resource Type:</b> The type of resource being protected (e.g., {@code
 *       PROCESS_DEFINITION}, {@code USER_TASK})
 *   <li><b>Permission Type:</b> The operation being authorized (e.g., {@code READ}, {@code
 *       CREATE_PROCESS_INSTANCE})
 *   <li><b>Resource IDs:</b> List of resource IDs the user has permission to access. Populated
 *       during pre-filtering from user's permissions and used to filter search queries
 *   <li><b>Resource ID Supplier:</b> Function to extract the resource ID from a document during
 *       post-filtering. Used to determine which resource ID to check against user's permissions
 *   <li><b>Condition:</b> An optional predicate that determines whether this authorization applies
 *       to a specific document
 *   <li><b>Transitive Flag:</b> Controls whether wildcard permissions disable authorization checks
 *       (used for cross-resource authorization patterns)
 * </ul>
 *
 * <h2>Resource IDs vs Resource ID Supplier</h2>
 *
 * <p>These fields serve distinct purposes:
 *
 * <ul>
 *   <li><b>resourceIds:</b> Used in pre-filtering (searches). Contains the list of resource IDs the
 *       user has permission to access. Populated by querying the user's permissions and passed to
 *       the search backend to filter queries.
 *   <li><b>resourceIdSupplier:</b> Used in post-filtering (gets). Extracts the resource ID from the
 *       fetched document so it can be compared against the user's permitted resource IDs.
 * </ul>
 *
 * <h2>Conditional Authorizations</h2>
 *
 * <p>Conditional authorizations allow authorization rules to only apply when certain runtime
 * conditions are met. This enables context-aware access control where the applicability of an
 * authorization depends on the properties of the resource being accessed.
 *
 * <p><b>Conditions are only evaluated during post-filtering (get operations).</b> During
 * pre-filtering (searches), conditions are not evaluated because documents don't exist yet - only
 * query criteria. The condition predicate requires an actual document to test against.
 *
 * <p>Example: Grant access to audit logs only when they have a process definition ID:
 *
 * <pre>{@code
 * Authorization<AuditLogEntity> auth = Authorization.of(
 *     a -> a.processDefinition().readProcessInstance())
 *     .with(AuditLogEntity::processDefinitionId)
 *     .withCondition(al -> al.processDefinitionId() != null);
 * }</pre>
 *
 * <p>The condition is evaluated via {@link #appliesTo(Object)} during post-filtering. In
 * pre-filtering (searches), the condition is not evaluated since documents don't exist yet.
 *
 * <h2>Transitive Authorizations</h2>
 *
 * <p>The transitive flag is a technical optimization control that determines how wildcard
 * permissions are handled during search operations. It does <b>not</b> cascade or extend
 * permissions from one resource to another.
 *
 * <p><b>What it does:</b> Controls whether wildcard ({@code *}) permissions disable authorization
 * checks. This enables cross-resource authorization patterns where one resource type's permissions
 * (e.g., process definitions) are used to filter queries on related resource types (e.g., audit
 * logs).
 *
 * <p>Example: Use process definition permissions to filter audit log queries:
 *
 * <pre>{@code
 * Authorization<AuditLogEntity> transitiveAuth = Authorization.of(
 *     a -> a.processDefinition().readProcessInstance().transitive());
 * }</pre>
 *
 * <p>The transitive flag affects wildcard optimization:
 *
 * <ul>
 *   <li><b>Without transitive flag:</b> Wildcard ({@code *}) disables authorization checks
 *       (standard optimization)
 *   <li><b>With transitive flag:</b> Wildcard keeps authorization checks enabled, preventing the
 *       optimization from bypassing filtering in cross-resource scenarios
 * </ul>
 *
 * <p><b>Important:</b> The authorization still uses the specified resource IDs (e.g., process
 * definition IDs) for filtering. The transitive flag only controls whether wildcard permissions
 * short-circuit the authorization check.
 *
 * <h2>Combining Conditional and Transitive Authorizations</h2>
 *
 * <p>These features can be combined for sophisticated access control patterns:
 *
 * <pre>{@code
 * Authorization<AuditLogEntity> combined = Authorization.of(
 *     a -> a.processDefinition().readUserTask().transitive())
 *     .with(AuditLogEntity::processDefinitionId)
 *     .withCondition(
 *         al -> al.processDefinitionId() != null
 *             && al.category() == AuditLogOperationCategory.USER_TASKS);
 * }</pre>
 *
 * @param <T> the type of document this authorization applies to
 * @param resourceType the type of resource being protected
 * @param permissionType the operation being authorized
 * @param resourceIds list of resource IDs the user has permission to access (populated during
 *     pre-filtering from user's permissions, used to filter search queries)
 * @param resourceIdSupplier function to extract the resource ID from a document during
 *     post-filtering (used to determine which ID to check against user's permissions)
 * @param condition optional predicate to determine if this authorization applies to a document
 *     (only evaluated during post-filtering when documents are available)
 * @param transitive whether wildcard permissions should keep authorization checks enabled
 * @see io.camunda.security.auth.condition.AuthorizationCondition
 * @see io.camunda.security.auth.condition.AuthorizationConditions
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record Authorization<T>(
    @JsonProperty("resource_type") AuthorizationResourceType resourceType,
    @JsonProperty("permission_type") PermissionType permissionType,
    @JsonProperty("resource_ids") List<String> resourceIds,
    @JsonIgnore Function<T, String> resourceIdSupplier,
    @JsonIgnore Predicate<T> condition,
    @JsonProperty("transitive") boolean transitive) {

  /**
   * Returns {@code true} if this authorization has any resource IDs specified.
   *
   * @return {@code true} if resource IDs are non-null and non-empty, {@code false} otherwise
   */
  public boolean hasAnyResourceIds() {
    return resourceIds != null && !resourceIds.isEmpty();
  }

  public static <T> Authorization<T> withAuthorization(
      final Authorization<T> authorization, final String resourceId) {
    return authorization.with(resourceId);
  }

  public static <T> Authorization<T> withAuthorization(
      final Authorization<T> authorization, final Function<T, String> resourceIdSupplier) {
    return authorization.with(resourceIdSupplier);
  }

  /**
   * Creates a new {@code Authorization} with a fixed resource ID.
   *
   * <p>This is typically used during authorization resolution to populate the authorization with
   * the user's permitted resource IDs for pre-filtering.
   *
   * @param resourceId the resource ID to add to this authorization
   * @return a new {@code Authorization} instance with the specified resource ID
   */
  public Authorization<T> with(final String resourceId) {
    return new Authorization<>(
        resourceType(),
        permissionType(),
        List.of(resourceId),
        resourceIdSupplier(),
        condition(),
        transitive());
  }

  /**
   * Creates a new {@code Authorization} with a list of fixed resource IDs.
   *
   * <p>This is typically used during authorization resolution to populate the authorization with
   * the user's permitted resource IDs for pre-filtering.
   *
   * @param resourceIds the list of resource IDs to add to this authorization
   * @return a new {@code Authorization} instance with the specified resource IDs
   */
  public Authorization<T> with(final List<String> resourceIds) {
    return new Authorization<>(
        resourceType(),
        permissionType(),
        List.copyOf(resourceIds),
        resourceIdSupplier(),
        condition(),
        transitive());
  }

  /**
   * Creates a new {@code Authorization} with a resource ID supplier function.
   *
   * <p>The supplier is used during post-filtering to extract the resource ID from a fetched
   * document so it can be compared against the user's permitted resource IDs.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Authorization.of(a -> a.processDefinition().readProcessInstance())
   *     .with(AuditLogEntity::processDefinitionId)
   * // Extracts processDefinitionId from fetched AuditLogEntity for permission checking
   * }</pre>
   *
   * @param resourceIdSupplier function to extract the resource ID from a document
   * @return a new {@code Authorization} instance with the specified resource ID supplier
   */
  public Authorization<T> with(final Function<T, String> resourceIdSupplier) {
    return new Authorization<>(
        resourceType(),
        permissionType(),
        resourceIds(),
        resourceIdSupplier,
        condition(),
        transitive());
  }

  /**
   * Creates a new {@code Authorization} with the specified condition predicate.
   *
   * <p>The condition determines whether this authorization applies to a specific document. During
   * authorization checks, only authorizations where {@code condition.test(document)} returns {@code
   * true} will be evaluated for access control.
   *
   * <p>This is particularly useful for implementing context-aware access control where the same
   * permission type may need to apply only to certain categories or types of resources.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Authorization<AuditLogEntity> auth = Authorization.of(
   *     a -> a.processDefinition().readProcessInstance())
   *     .with(AuditLogEntity::processDefinitionId)
   *     .withCondition(al -> al.processDefinitionId() != null);
   * }</pre>
   *
   * @param condition the predicate to test whether this authorization applies to a document
   * @return a new {@code Authorization} instance with the specified condition
   * @see #appliesTo(Object)
   */
  public Authorization<T> withCondition(final Predicate<T> condition) {
    return new Authorization<>(
        resourceType(),
        permissionType(),
        resourceIds(),
        resourceIdSupplier(),
        condition,
        transitive());
  }

  public static <T> Authorization<T> of(final Function<Builder<T>, Builder<T>> builderFunction) {
    return builderFunction.apply(new Builder<>()).build();
  }

  /**
   * Tests whether this authorization applies to the given document.
   *
   * <p>This method evaluates the authorization's condition predicate (if present) to determine if
   * the authorization is applicable to the specified document. If no condition is set, the
   * authorization applies to all documents.
   *
   * <p>This method is used during:
   *
   * <ul>
   *   <li><b>Post-filtering (get operations):</b> To determine which authorizations from an {@code
   *       AnyOfAuthorizationCondition} should be evaluated for a retrieved resource
   *   <li><b>Conditional authorization logic:</b> To filter out non-applicable authorizations
   *       before performing resource access checks
   * </ul>
   *
   * @param document the document to test
   * @return {@code true} if the authorization applies to the document (condition is null or
   *     evaluates to true), {@code false} otherwise
   * @see #withCondition(Predicate)
   * @see
   *     io.camunda.security.auth.condition.AnyOfAuthorizationCondition#applicableAuthorizations(Object)
   */
  public boolean appliesTo(final T document) {
    return condition == null || condition.test(document);
  }

  /**
   * Returns {@code true} if this authorization contains a wildcard resource ID.
   *
   * <p>A wildcard resource ID ({@code "*"}) indicates that the authorization applies to all
   * resources of the specified type. The behavior of wildcard authorizations depends on the {@link
   * #transitive()} flag:
   *
   * <ul>
   *   <li><b>Non-transitive wildcard:</b> Authorization checks are disabled entirely (no filtering)
   *   <li><b>Transitive wildcard:</b> Authorization checks remain enabled to properly filter
   *       related resources
   * </ul>
   *
   * @return {@code true} if any resource ID matches the wildcard constant, {@code false} otherwise
   * @see io.camunda.zeebe.protocol.record.value.AuthorizationScope#WILDCARD
   */
  @JsonIgnore
  public boolean isWildcard() {
    return resourceIds != null
        && resourceIds.stream().anyMatch(id -> WILDCARD.getResourceId().equals(id));
  }

  /**
   * Builder for constructing {@code Authorization} instances with a fluent API.
   *
   * <p>The builder provides convenient methods for setting resource types and permission types
   * using domain-specific terminology (e.g., {@code processDefinition()}, {@code readUserTask()}).
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * Authorization<Entity> auth = Authorization.of(
   *     a -> a.processDefinition()
   *           .readProcessInstance()
   *           .transitive()
   *           .resourceId("process-123"));
   * }</pre>
   *
   * @param <T> the type of document this authorization applies to
   */
  public static class Builder<T> {
    private AuthorizationResourceType resourceType;
    private PermissionType permissionType;
    private List<String> resourceIds;
    private Function<T, String> resourceIdSupplier;
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

    /**
     * Sets a condition predicate for this authorization.
     *
     * <p>The condition determines whether the authorization applies to a specific document. Only
     * documents for which {@code condition.test(document)} returns {@code true} will be subject to
     * this authorization.
     *
     * @param condition the predicate to test document applicability
     * @return this builder for method chaining
     * @see Authorization#withCondition(Predicate)
     */
    public Builder<T> condition(final Predicate<T> condition) {
      this.condition = condition;
      return this;
    }

    /**
     * Marks this authorization as transitive.
     *
     * <p>The transitive flag controls wildcard permission optimization behavior during search
     * operations. It does <b>not</b> cascade or extend permissions from one resource to another.
     *
     * <p><b>What it does:</b> Prevents wildcard ({@code *}) permissions from disabling
     * authorization checks. This is used for cross-resource authorization patterns where one
     * resource type's permissions filter queries on a related resource type.
     *
     * <p>Behavioral difference:
     *
     * <ul>
     *   <li><b>Without transitive:</b> Wildcard permissions disable authorization checks (standard
     *       optimization - user has access to all resources of that type)
     *   <li><b>With transitive:</b> Wildcard permissions keep authorization checks enabled,
     *       ensuring filtering still applies in cross-resource scenarios
     * </ul>
     *
     * <p>Example: Use process definition permissions to filter audit log queries:
     *
     * <pre>{@code
     * Authorization<AuditLogEntity> auth = Authorization.of(
     *     a -> a.processDefinition()
     *           .readProcessInstance()
     *           .transitive());
     * // Uses process definition IDs to filter audit logs
     * // Wildcard process permissions won't disable this check
     * }</pre>
     *
     * @return this builder for method chaining
     * @see Authorization#transitive()
     */
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
      return new Authorization<>(
          resourceType, permissionType, resourceIds, resourceIdSupplier, condition, transitive);
    }
  }
}
