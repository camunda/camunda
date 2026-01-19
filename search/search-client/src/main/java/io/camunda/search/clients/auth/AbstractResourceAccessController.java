/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.exception.TenantAccessDeniedException;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.auth.condition.AnyOfAuthorizationCondition;
import io.camunda.security.auth.condition.AuthorizationConditions;
import io.camunda.security.auth.condition.SingleAuthorizationCondition;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;
import io.camunda.security.reader.TenantCheck;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

/**
 * Abstract base class for implementing resource access control in search operations.
 *
 * <p>This controller enforces authorization rules during both search (pre-filtering) and get
 * (post-filtering) operations. It supports advanced authorization features including conditional
 * and transitive authorizations.
 *
 * <h2>Pre-Filtering (Search Operations)</h2>
 *
 * <p>During search operations ({@link #doSearch}), authorization checks are applied before
 * executing the query:
 *
 * <ul>
 *   <li>Determines applicable authorizations from the security context
 *   <li>Resolves resource access for each authorization
 *   <li>Handles wildcard permissions and transitive flags appropriately
 *   <li>Creates {@link io.camunda.security.reader.AuthorizationCheck} that search backends can
 *       translate into query filters
 * </ul>
 *
 * <h2>Post-Filtering (Get Operations)</h2>
 *
 * <p>During get operations ({@link #doGet}), resources are fetched first, then access is verified:
 *
 * <ul>
 *   <li>Fetches the resource without authorization checks
 *   <li>Evaluates which authorizations apply to the retrieved resource (conditional authorization
 *       filtering)
 *   <li>Verifies at least one applicable authorization grants access
 *   <li>Throws {@link ResourceAccessDeniedException} if access is denied
 * </ul>
 *
 * <h2>Conditional Authorization Support</h2>
 *
 * <p>Conditional authorizations use predicates to determine whether an authorization applies to a
 * specific document.
 *
 * <p><b>Current Implementation Status:</b>
 *
 * <ul>
 *   <li><b>Post-filtering (Get):</b> ✅ Fully implemented - Only authorizations where {@link
 *       Authorization#appliesTo(Object)} returns {@code true} are evaluated
 *   <li><b>Pre-filtering (Search):</b> ❌ Not yet implemented - Conditions are passed to backends
 *       but currently ignored. Backends only use resource IDs for filtering
 * </ul>
 *
 * <p><b>Important:</b> Do not rely on conditions to filter search results. They only work for
 * individual resource retrieval (get operations).
 *
 * <h2>Transitive Authorization Support</h2>
 *
 * <p>Transitive authorizations extend permissions from one resource type to related resources. The
 * key behavioral difference is in wildcard handling:
 *
 * <ul>
 *   <li><b>Non-transitive wildcard:</b> Authorization check is disabled (no filtering)
 *   <li><b>Transitive wildcard:</b> Authorization check remains enabled to properly filter related
 *       resources
 * </ul>
 *
 * <p><b>Example:</b> Consider a user querying audit logs with a transitive process definition
 * authorization:
 *
 * <pre>
 * User has: PROCESS_DEFINITION.READ_PROCESS_INSTANCE = "*" (wildcard)
 * Authorization: AUDIT_LOG with transitive PROCESS_DEFINITION.READ_PROCESS_INSTANCE
 * Result: Query is filtered to only return audit logs for process instances the user can access
 * </pre>
 *
 * <p>Without the transitive flag, the wildcard would disable filtering entirely, returning all
 * audit logs. With the transitive flag, filtering remains active to respect the authorization
 * relationship between audit logs and process definitions.
 *
 * @see Authorization
 * @see io.camunda.security.auth.condition.AuthorizationCondition
 * @see io.camunda.security.auth.condition.AnyOfAuthorizationCondition
 */
public abstract class AbstractResourceAccessController implements ResourceAccessController {

  protected abstract ResourceAccessProvider getResourceAccessProvider();

  protected abstract TenantAccessProvider getTenantAccessProvider();

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return doPostFiltering(securityContext, resourceChecksApplier);
  }

  @Override
  public <T> T doSearch(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return doPreFiltering(securityContext, resourceChecksApplier);
  }

  @Override
  public boolean supports(final SecurityContext securityContext) {
    return Optional.of(securityContext)
            .filter(c -> c.authentication() != null)
            .filter(c -> c.authorizationCondition() != null)
            .isPresent()
        && !isAnonymousAuthentication(securityContext.authentication());
  }

  protected <T> T doPreFiltering(
      final SecurityContext securityContext, final Function<ResourceAccessChecks, T> applier) {
    final var authorizationCheck = determineAuthorizationCheck(securityContext);
    final var tenantCheck = determineTenantCheck(securityContext.authentication());

    // read with resource access checks
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);
    return applier.apply(resourceAccessChecks);
  }

  protected AuthorizationCheck determineAuthorizationCheck(final SecurityContext securityContext) {
    final var authentication = securityContext.authentication();
    final var condition = securityContext.authorizationCondition();
    return switch (condition) {
      case final SingleAuthorizationCondition single ->
          createSingleAuthorizationCheck(authentication, single);
      case final AnyOfAuthorizationCondition anyOf ->
          createAnyOfAuthorizationCheck(authentication, anyOf);
      default ->
          throw new IllegalStateException(
              "Unsupported AuthorizationCondition type: " + condition.getClass().getSimpleName());
    };
  }

  protected ResourceAccess resolveResourceAccess(
      final CamundaAuthentication authentication, final Authorization<?> authorization) {
    return getResourceAccessProvider().resolveResourceAccess(authentication, authorization);
  }

  /**
   * Creates an authorization check for a single authorization condition.
   *
   * <p>This method implements a key optimization: when a user has wildcard access ({@code *}) to a
   * resource and the authorization is not marked as transitive, authorization checks are disabled
   * entirely. This improves query performance for users with broad permissions.
   *
   * <p>However, if the authorization is transitive, checks remain enabled even for wildcard access.
   * This ensures proper filtering of related resources based on the transitive relationship.
   *
   * @param authentication the authenticated principal
   * @param single the single authorization condition
   * @return an enabled or disabled authorization check
   * @see Authorization#transitive()
   */
  private AuthorizationCheck createSingleAuthorizationCheck(
      final CamundaAuthentication authentication, final SingleAuthorizationCondition single) {
    final var authorization = single.authorization();
    final var resourceAccess = resolveResourceAccess(authentication, authorization);

    if (resourceAccess.wildcard() && !authorization.transitive()) {
      return AuthorizationCheck.disabled();
    }

    return AuthorizationCheck.enabled(resourceAccess.authorization());
  }

  /**
   * Creates an authorization check for an anyOf authorization condition.
   *
   * <p>This method processes multiple authorizations in a disjunctive manner (any satisfied
   * authorization grants access). For each authorization:
   *
   * <ul>
   *   <li>Resolves the resource access for the authenticated principal
   *   <li>If wildcard access is found and the authorization is not transitive, disables all checks
   *       (optimization)
   *   <li>Otherwise, accumulates the authorization for inclusion in the combined check
   * </ul>
   *
   * <p>This supports complex authorization patterns like audit logs being accessible through
   * multiple permission paths (direct audit log permission, transitive process permission, etc.).
   *
   * @param authentication the authenticated principal
   * @param anyOf the anyOf authorization condition
   * @return an enabled or disabled authorization check with all applicable authorizations
   * @see AnyOfAuthorizationCondition
   */
  private AuthorizationCheck createAnyOfAuthorizationCheck(
      final CamundaAuthentication authentication, final AnyOfAuthorizationCondition anyOf) {
    final var resolvedAuthorizations = new ArrayList<Authorization<?>>();
    for (final Authorization authorization : anyOf.authorizations()) {
      final var resourceAccess = resolveResourceAccess(authentication, authorization);

      if (resourceAccess.wildcard() && !authorization.transitive()) {
        return AuthorizationCheck.disabled();
      }

      resolvedAuthorizations.add(resourceAccess.authorization());
    }

    return AuthorizationCheck.enabled(AuthorizationConditions.anyOf(resolvedAuthorizations));
  }

  protected TenantCheck determineTenantCheck(final CamundaAuthentication authentication) {
    final var resourceAccess = resolveTenantAccess(authentication);
    return createTenantCheck(resourceAccess);
  }

  protected TenantAccess resolveTenantAccess(final CamundaAuthentication authentication) {
    return getTenantAccessProvider().resolveTenantAccess(authentication);
  }

  protected TenantCheck createTenantCheck(final TenantAccess tenantAccess) {
    // It may be that the principal is not a member of any tenant,
    // but they query none tenant-owned entities (like authorizations)
    // => even when TenantAccess is denied, enable TenantCheck
    // => the search query will apply accordingly
    return Optional.of(tenantAccess)
        .filter(t -> !t.wildcard())
        .map(a -> TenantCheck.enabled(tenantAccess.tenantIds()))
        .orElseGet(TenantCheck::disabled);
  }

  protected <T> T doPostFiltering(
      final SecurityContext securityContext, final Function<ResourceAccessChecks, T> applier) {
    // read without any resource access check
    final T resource = applier.apply(ResourceAccessChecks.disabled());

    if (resource == null) {
      return null;
    }

    // now ensure access to resource
    ensureTenantAccessOrThrow(securityContext.authentication(), resource);
    ensureResourceAccessOrThrow(securityContext, resource);

    return resource;
  }

  protected <T> void ensureResourceAccessOrThrow(
      final SecurityContext securityContext, final T document) {

    final var condition = securityContext.authorizationCondition();

    switch (condition) {
      case final SingleAuthorizationCondition single ->
          ensureSingleAuthorizationAccessOrThrow(securityContext, document, single);
      case final AnyOfAuthorizationCondition anyOf ->
          ensureAnyOfAuthorizationAccessOrThrow(securityContext, document, anyOf);
      default ->
          throw new IllegalStateException(
              "Unsupported AuthorizationCondition type: " + condition.getClass().getSimpleName());
    }
  }

  private <T> void ensureSingleAuthorizationAccessOrThrow(
      final SecurityContext securityContext,
      final T document,
      final SingleAuthorizationCondition single) {
    final Authorization authorization = single.authorization();

    if (!authorization.appliesTo(document)) {
      throw new ResourceAccessDeniedException(
          authorization,
          "Authorization is not applicable - which does not make sense for single authorizations.");
    }

    final var resourceAccess =
        getResourceAccessProvider()
            .hasResourceAccess(securityContext.authentication(), authorization, document);
    if (resourceAccess.denied()) {
      throw new ResourceAccessDeniedException(authorization);
    }
  }

  private <T> void ensureAnyOfAuthorizationAccessOrThrow(
      final SecurityContext securityContext,
      final T document,
      final AnyOfAuthorizationCondition anyOf) {
    final var authorizations = anyOf.applicableAuthorizations(document);

    for (final Authorization authorization : authorizations) {
      final var resourceAccess =
          getResourceAccessProvider()
              .hasResourceAccess(securityContext.authentication(), authorization, document);
      if (resourceAccess.allowed()) {
        // at least one authorization allowed access, no need to check further
        return;
      }
    }

    // none of the authorizations allowed access
    throw new ResourceAccessDeniedException(authorizations);
  }

  protected <T> void ensureTenantAccessOrThrow(
      final CamundaAuthentication authentication, final T document) {
    final var tenantAccess = getTenantAccessProvider().hasTenantAccess(authentication, document);
    if (tenantAccess.denied()) {
      throw new TenantAccessDeniedException(
          ErrorMessages.ERROR_RESOURCE_ACCESS_CONTROLLER_NO_TENANT_ACCESS);
    }
  }
}
