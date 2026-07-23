/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization;

import io.camunda.security.api.context.TokenClaimsAuthenticationResolver;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenantsResolver;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates the shared CSL skip-logic and authorization check used across engine command
 * processors.
 *
 * <p>Every engine command site that checks CSL authorization duplicates the same 15-line block:
 * internal-command skip → anonymous skip → security-disabled skip → no-principal check → claims
 * conversion → {@link AuthorizationCheckPort#check}. This class captures that block in one place.
 *
 * <p>Use {@link #check} for single-check sites (one {@link RequiredAuthorization} per command). Use
 * {@link #resolveForCheck} for multi-check sites (e.g. UserTask processors) that run several CSL
 * checks after the skip-logic resolves the principal.
 */
@NullMarked
public final class CslAuthorizationCheck {

  private static final Logger LOG = LoggerFactory.getLogger(CslAuthorizationCheck.class);

  private final AuthorizationCheckPort authzService;
  private final TokenClaimsAuthenticationResolver claimsConverter;
  private final EngineSecurityConfig securityConfig;

  public CslAuthorizationCheck(
      final AuthorizationCheckPort authzService,
      final TokenClaimsAuthenticationResolver claimsConverter,
      final EngineSecurityConfig securityConfig) {
    this.authzService = authzService;
    this.claimsConverter = claimsConverter;
    this.securityConfig = securityConfig;
  }

  /**
   * Applies skip-logic and resolves the principal for a downstream CSL check.
   *
   * <p>Returns:
   *
   * <ul>
   *   <li>{@code right(empty)} — skip-logic says allow; no CSL check needed (internal command,
   *       anonymous user, security disabled, or no-principal when authorizations are disabled).
   *   <li>{@code right(present)} — caller must run the CSL check with this {@link
   *       CamundaAuthentication}.
   *   <li>{@code left(rejection)} — no principal present and authorizations are enabled; the
   *       request must be rejected with the returned {@link Rejection}.
   * </ul>
   *
   * @param noPrincipalRejection the rejection to use when no principal claim is present and
   *     authorizations are enabled
   */
  public Either<Rejection, Optional<CamundaAuthentication>> resolveForCheck(
      final TypedRecord<?> command, final Rejection noPrincipalRejection) {
    if (command.isInternalCommand()) {
      LOG.trace("Skipping authorization check for internal command {}", command.getIntent());
      return Either.right(Optional.empty());
    }
    final var authorizations = command.getAuthorizations();
    if (Boolean.TRUE.equals(authorizations.get(Authorization.AUTHORIZED_ANONYMOUS_USER))) {
      LOG.trace(
          "Skipping authorization check for anonymous user on command {}", command.getIntent());
      return Either.right(Optional.empty());
    }
    if (!securityConfig.isAuthorizationsEnabled()
        && !securityConfig.isMultiTenancyChecksEnabled()) {
      LOG.trace(
          "Skipping authorization check for command {}: security disabled", command.getIntent());
      return Either.right(Optional.empty());
    }
    if (authorizations.get(Authorization.AUTHORIZED_USERNAME) == null
        && authorizations.get(Authorization.AUTHORIZED_CLIENT_ID) == null) {
      if (!securityConfig.isAuthorizationsEnabled()) {
        // Authorizations are disabled; the principal absence is irrelevant (multi-tenancy checks
        // do not require a claim-based identity for non-tenant-owned resources).
        return Either.right(Optional.empty());
      }
      LOG.debug(
          "Rejecting command {}: neither username nor clientId claim is present",
          command.getIntent());
      return Either.left(noPrincipalRejection);
    }
    return Either.right(Optional.of(claimsConverter.resolve(authorizations)));
  }

  /**
   * Resolves the {@link AuthorizedTenants} for a command from its authorization claims, reusing
   * this component's claims converter and security configuration. Centralizes the converter here so
   * command processors depend only on {@code CslAuthorizationCheck} instead of threading the
   * converter through their construction chains.
   */
  public AuthorizedTenants resolveAuthorizedTenants(final Map<String, Object> authorizations) {
    return AuthorizedTenantsResolver.resolve(authorizations, securityConfig, claimsConverter);
  }

  public boolean isMultiTenancyChecksEnabled() {
    return securityConfig.isMultiTenancyChecksEnabled();
  }

  /**
   * Tenant-assignment check for command sites that must verify tenant membership independently of a
   * resource {@link #check} — e.g. a command-level tenant gate, or a site whose RBAC check runs at
   * a different granularity (one tenant, many resource checks).
   *
   * <p>Encapsulates the skip-logic hand-rolled across engine processors: when multi-tenancy checks
   * are disabled the check is a no-op; when no username or clientId claim is present the check is
   * also a no-op (mirrors {@link #resolveForCheck} — a no-principal command is either an internal
   * command already exempted upstream, or authorizations are disabled and the primary permission
   * check already let it through; either way there is no principal to hold a tenant assignment
   * requirement against). Otherwise the authorized tenants are resolved from the command's claims
   * (anonymous access is authorized for every tenant) and {@code tenantId} must be among them.
   *
   * <p>Callers own the rejection semantics: {@code notAssignedRejection} carries the {@link
   * io.camunda.zeebe.protocol.record.RejectionType} — {@code FORBIDDEN} to signal "not assigned to
   * tenant", or {@code NOT_FOUND} to mask an existing resource — and the message.
   *
   * @param value the value to return on success (mirrors {@link #check}; enables {@code flatMap}
   *     composition with it)
   * @param notAssignedRejection the rejection to return when the principal is not assigned to
   *     {@code tenantId}
   */
  public <T> Either<Rejection, T> checkTenant(
      final TypedRecord<?> command,
      final String tenantId,
      final T value,
      final Rejection notAssignedRejection) {
    if (!securityConfig.isMultiTenancyChecksEnabled()) {
      return Either.right(value);
    }
    final var authorizations = command.getAuthorizations();
    if (authorizations.get(Authorization.AUTHORIZED_USERNAME) == null
        && authorizations.get(Authorization.AUTHORIZED_CLIENT_ID) == null) {
      return Either.right(value);
    }
    if (resolveAuthorizedTenants(authorizations).isAuthorizedForTenantId(tenantId)) {
      return Either.right(value);
    }
    return Either.left(notAssignedRejection);
  }

  /**
   * Like {@link #checkTenant} but for command sites that must verify membership across a list of
   * tenant IDs at once (e.g. a job-batch activation command targeting multiple explicit tenants),
   * using {@link AuthorizedTenants#isAuthorizedForTenantIds}. Shares the same skip-logic as {@link
   * #checkTenant}.
   *
   * @param notAssignedRejection the rejection to return when the principal is not assigned to all
   *     of {@code tenantIds}
   */
  public <T> Either<Rejection, T> checkTenants(
      final TypedRecord<?> command,
      final List<String> tenantIds,
      final T value,
      final Rejection notAssignedRejection) {
    if (!securityConfig.isMultiTenancyChecksEnabled()) {
      return Either.right(value);
    }
    final var authorizations = command.getAuthorizations();
    if (authorizations.get(Authorization.AUTHORIZED_USERNAME) == null
        && authorizations.get(Authorization.AUTHORIZED_CLIENT_ID) == null) {
      return Either.right(value);
    }
    if (resolveAuthorizedTenants(authorizations).isAuthorizedForTenantIds(tenantIds)) {
      return Either.right(value);
    }
    return Either.left(notAssignedRejection);
  }

  /**
   * Combines {@link #check} and {@link #checkTenant} into the single call most command sites need:
   * RBAC permission first, tenant membership second. Mirrors the priority {@code main}'s {@code
   * AuthorizationCheckBehavior} aggregation gives when both checks would fail on the same command
   * (permission rejection wins) — so a principal with no permission at all always sees {@code
   * FORBIDDEN}, never a tenant-shaped rejection that could hint at the resource's existence.
   *
   * <p>Only runs the tenant check once the permission check passes; a permission failure never
   * bothers a tenant lookup, and vice versa a resource-not-permitted-here principal never learns
   * whether {@code tenantId} would have mattered.
   *
   * @param tenantNotAssignedRejection the rejection to return if the principal has the permission
   *     but is not assigned to {@code tenantId} — callers choose {@code FORBIDDEN} for
   *     entity-creation commands or {@code NOT_FOUND} to mask cross-tenant existence of a
   *     looked-up-by-key resource
   */
  public <T> Either<Rejection, T> checkAuthorizationAndTenant(
      final TypedRecord<?> command,
      final RequiredAuthorization<?> required,
      final T value,
      final Rejection noPrincipalRejection,
      final String tenantId,
      final Rejection tenantNotAssignedRejection) {
    return check(command, required, value, noPrincipalRejection)
        .flatMap(v -> checkTenant(command, tenantId, v, tenantNotAssignedRejection));
  }

  /**
   * Like {@link #checkAuthorizationAndTenant} but with a caller-supplied {@code denialMapper} for
   * the permission check (see {@link #check(TypedRecord, RequiredAuthorization, Object, Rejection,
   * Function)}).
   */
  public <T> Either<Rejection, T> checkAuthorizationAndTenant(
      final TypedRecord<?> command,
      final RequiredAuthorization<?> required,
      final T value,
      final Rejection noPrincipalRejection,
      final Function<AuthorizationRejection, Rejection> denialMapper,
      final String tenantId,
      final Rejection tenantNotAssignedRejection) {
    return check(command, required, value, noPrincipalRejection, denialMapper)
        .flatMap(v -> checkTenant(command, tenantId, v, tenantNotAssignedRejection));
  }

  /**
   * Direct authentication-based check for multi-check callers that have already resolved the
   * principal via {@link #resolveForCheck}.
   *
   * <p>Delegates directly to the underlying {@link AuthorizationCheckPort} without any skip-logic.
   */
  public io.camunda.security.api.model.Either<AuthorizationRejection, Void> checkAuth(
      final CamundaAuthentication auth, final RequiredAuthorization<?> required) {
    return authzService.check(auth, required);
  }

  /**
   * Direct authentication-based check with a context value, for multi-check callers that have
   * already resolved the principal via {@link #resolveForCheck}.
   */
  public <T> io.camunda.security.api.model.Either<AuthorizationRejection, Void> checkAuth(
      final CamundaAuthentication auth, final RequiredAuthorization<T> required, final T ctx) {
    return authzService.check(auth, required, ctx);
  }

  /**
   * Like {@link #check} but skips the internal-command gate in {@link #resolveForCheck}. Use for
   * distributed commands: on target partitions they appear as internal (no request metadata) but
   * still carry the originating user's claims and must be subject to authorization checks.
   *
   * <p>All other skip conditions (anonymous user, security disabled, no principal) still apply.
   */
  public <T> Either<Rejection, T> checkForDistributedCommand(
      final TypedRecord<?> command,
      final RequiredAuthorization<?> required,
      final T value,
      final Rejection noPrincipalRejection) {
    return checkWithClaims(command.getAuthorizations(), required, value, noPrincipalRejection);
  }

  /**
   * Authorization check for contexts where no {@link TypedRecord} is available, only the raw claims
   * map (e.g. job-stream activation where claims come from {@link
   * io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties}). Applies the same
   * skip-logic as {@link #checkForDistributedCommand}: anonymous user, security disabled, no
   * principal.
   */
  public <T> Either<Rejection, T> checkWithClaims(
      final Map<String, Object> claims,
      final RequiredAuthorization<?> required,
      final T value,
      final Rejection noPrincipalRejection) {
    return checkWithClaims(
        claims, required, value, noPrincipalRejection, AuthorizationRejectionMapper::toRejection);
  }

  private <T> Either<Rejection, T> checkWithClaims(
      final Map<String, Object> claims,
      final RequiredAuthorization<?> required,
      final T value,
      final Rejection noPrincipalRejection,
      final Function<AuthorizationRejection, Rejection> denialMapper) {
    if (Boolean.TRUE.equals(claims.get(Authorization.AUTHORIZED_ANONYMOUS_USER))) {
      return Either.right(value);
    }
    if (!securityConfig.isAuthorizationsEnabled()
        && !securityConfig.isMultiTenancyChecksEnabled()) {
      return Either.right(value);
    }
    if (claims.get(Authorization.AUTHORIZED_USERNAME) == null
        && claims.get(Authorization.AUTHORIZED_CLIENT_ID) == null) {
      if (!securityConfig.isAuthorizationsEnabled()) {
        return Either.right(value);
      }
      return Either.left(noPrincipalRejection);
    }
    final var result = authzService.check(claims, required);
    if (result.isLeft()) {
      return Either.left(denialMapper.apply(result.leftValue()));
    }
    return Either.right(value);
  }

  /**
   * Full authorization check for single-check sites.
   *
   * <p>Applies the same skip-logic as {@link #checkWithClaims} using the command's claims,
   * delegating to {@link AuthorizationCheckPort#check(Map, RequiredAuthorization)}. Maps any CSL
   * rejection to a {@link Rejection} via {@link AuthorizationRejectionMapper#toRejection}.
   *
   * @param value the value to return on success
   * @param noPrincipalRejection the rejection to use when no principal claim is present and
   *     authorizations are enabled
   */
  public <T> Either<Rejection, T> check(
      final TypedRecord<?> command,
      final RequiredAuthorization<?> required,
      final T value,
      final Rejection noPrincipalRejection) {
    return check(
        command, required, value, noPrincipalRejection, AuthorizationRejectionMapper::toRejection);
  }

  /**
   * Full authorization check for single-check sites, with a caller-supplied mapping from a CSL
   * {@link AuthorizationRejection} to the engine {@link Rejection}.
   *
   * <p>Job command processors use the default {@link AuthorizationRejectionMapper#toRejection}
   * (which appends the {@code required resource identifiers} suffix, matching the pre-migration
   * engine message). The identity processors pass {@link
   * AuthorizationRejectionMapper#toBareRejection} to preserve their pre-migration bare message,
   * which never carried that suffix.
   *
   * @param denialMapper maps a CSL rejection (principal present but not authorized) to a {@link
   *     Rejection}
   */
  public <T> Either<Rejection, T> check(
      final TypedRecord<?> command,
      final RequiredAuthorization<?> required,
      final T value,
      final Rejection noPrincipalRejection,
      final Function<AuthorizationRejection, Rejection> denialMapper) {
    if (command.isInternalCommand()) {
      LOG.trace("Skipping authorization check for internal command {}", command.getIntent());
      return Either.right(value);
    }
    return checkWithClaims(
        command.getAuthorizations(),
        required,
        value,
        noPrincipalRejection,
        rejection -> {
          LOG.debug(
              "Authorization check rejected for command {}: {}", command.getIntent(), rejection);
          return denialMapper.apply(rejection);
        });
  }
}
