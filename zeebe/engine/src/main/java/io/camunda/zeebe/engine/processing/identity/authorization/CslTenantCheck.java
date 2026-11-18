/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization;

import io.camunda.security.api.context.TokenClaimsAuthenticationResolver;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

/**
 * Encapsulates the tenant-membership checks used across engine command processors.
 *
 * <p>Most callers call {@link #checkTenant} directly, downstream of their own RBAC {@link
 * CslAuthorizationCheck#check} or {@link CslAuthorizationCheck#checkForDistributedCommand} call on
 * the same command; a few compose both via {@link
 * CslAuthorizationCheck#checkAuthorizationAndTenant}. Either way, whoever calls {@link
 * #checkTenant} is responsible for ensuring that RBAC check has already run and passed — do not
 * call it directly without one, and do not remove its no-principal skip without auditing every
 * caller. {@link #checkTenantsRequiringPrincipal} has a different contract — see its own javadoc.
 */
@NullMarked
public final class CslTenantCheck {

  private final TokenClaimsAuthenticationResolver claimsConverter;
  private final EngineSecurityConfig securityConfig;

  public CslTenantCheck(
      final TokenClaimsAuthenticationResolver claimsConverter,
      final EngineSecurityConfig securityConfig) {
    this.claimsConverter = claimsConverter;
    this.securityConfig = securityConfig;
  }

  /**
   * Resolves the {@link TenantAccess} for the given authorization claims, reusing this component's
   * claims converter and security configuration. Centralizes the converter here so command
   * processors depend only on {@code CslTenantCheck} instead of threading the converter through
   * their construction chains. For a command, use {@link #resolveAuthorizedTenants(TypedRecord)};
   * this variant is for claims without a command, e.g. from a job stream.
   *
   * <p>Anonymous access is a wildcard grant; a multi-tenancy-disabled command resolves to the
   * default tenant; a claims-free command resolves to an empty allowed grant (authorized for no
   * tenant); otherwise the authorized tenant IDs come from the resolved {@link
   * io.camunda.security.api.model.CamundaAuthentication}.
   */
  public TenantAccess resolveAuthorizedTenantsWithClaims(final Map<String, Object> authorizations) {
    if (isAnonymousCommand(authorizations)) {
      return TenantAccess.wildcard(List.of());
    }
    if (!securityConfig.isMultiTenancyChecksEnabled()) {
      return TenantAccess.allowed(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    }
    if (authorizations.get(Authorization.AUTHORIZED_USERNAME) == null
        && authorizations.get(Authorization.AUTHORIZED_CLIENT_ID) == null) {
      return TenantAccess.allowed(List.of());
    }
    final var authentication = claimsConverter.resolve(authorizations);
    final var tenantIds =
        Objects.requireNonNullElse(authentication.authenticatedTenantIds(), List.<String>of());
    return TenantAccess.allowed(tenantIds);
  }

  /**
   * Resolves the {@link TenantAccess} for a command. Use this variant for every command. When
   * multi-tenancy checks are enabled, an internal command that is not distributed is authorized for
   * every tenant. Otherwise the result is that of {@link #resolveAuthorizedTenantsWithClaims} for
   * the claims of the command.
   */
  public TenantAccess resolveAuthorizedTenants(final TypedRecord<?> command) {
    if (securityConfig.isMultiTenancyChecksEnabled() && isInternalNotDistributed(command)) {
      return TenantAccess.wildcard(List.of());
    }
    return resolveAuthorizedTenantsWithClaims(command.getAuthorizations());
  }

  /**
   * The engine writes internal commands, so it does not check their tenants. It copies the claims
   * of a client command to its follow-up commands only to name the actor. A distributed command
   * also counts as internal on the target partition, but its claims are those of the client
   * command, and some processors check them.
   */
  private static boolean isInternalNotDistributed(final TypedRecord<?> command) {
    return command.isInternalCommand() && !command.isCommandDistributed();
  }

  /**
   * True when the raw claims/authorizations map marks the caller as an anonymous user. Checked on
   * the raw map because some write-path call sites run before (or without) building a {@link
   * io.camunda.security.api.model.CamundaAuthentication}.
   */
  static boolean isAnonymousCommand(final Map<String, Object> authorizations) {
    return Boolean.TRUE.equals(authorizations.get(Authorization.AUTHORIZED_ANONYMOUS_USER));
  }

  public boolean isMultiTenancyChecksEnabled() {
    return securityConfig.isMultiTenancyChecksEnabled();
  }

  /**
   * Tenant-assignment check for command sites that must verify tenant membership independently of a
   * resource {@link CslAuthorizationCheck#check} — e.g. a command-level tenant gate, or a site
   * whose RBAC check runs at a different granularity (one tenant, many resource checks).
   *
   * <p>Encapsulates the skip-logic hand-rolled across engine processors: when multi-tenancy checks
   * are disabled the check is a no-op; for an internal command that is not distributed the check is
   * a no-op; when no username or clientId claim is present the check is also a no-op (mirrors
   * {@link CslAuthorizationCheck#resolveForCheck} — a no-principal command is either a distributed
   * command without claims, or authorizations are disabled and the primary permission check already
   * let it through; either way there is no principal to hold a tenant assignment requirement
   * against). Otherwise the authorized tenants are resolved from the command's claims (anonymous
   * access is authorized for every tenant) and {@code tenantId} must be among them.
   *
   * <p>This no-principal skip is load-bearing: every caller must ensure a RBAC {@link
   * CslAuthorizationCheck#check} or {@link CslAuthorizationCheck#checkForDistributedCommand} call
   * for the same command has already run and passed before reaching this method — either directly,
   * or composed via {@link CslAuthorizationCheck#checkAuthorizationAndTenant}. When authorizations
   * are enabled, that RBAC check itself rejects a no-principal command before this method runs;
   * when authorizations are disabled, letting a claims-free command through here (e.g. deployment/
   * process lifecycle commands issued without an explicit user, common in tests and tooling) is the
   * established, relied-upon behavior. Do not call this method directly without a preceding RBAC
   * check, and do not remove this skip without auditing every caller.
   *
   * <p>Callers own the rejection semantics: {@code notAssignedRejection} carries the {@link
   * io.camunda.zeebe.protocol.record.RejectionType} — {@code FORBIDDEN} to signal "not assigned to
   * tenant", or {@code NOT_FOUND} to mask an existing resource — and the message.
   *
   * @param value the value to return on success (mirrors {@link CslAuthorizationCheck#check};
   *     enables {@code flatMap} composition with it)
   */
  public <T> Either<Rejection, T> checkTenant(
      final TypedRecord<?> command,
      final String tenantId,
      final T value,
      final Rejection notAssignedRejection) {
    if (!securityConfig.isMultiTenancyChecksEnabled() || isInternalNotDistributed(command)) {
      return Either.right(value);
    }
    final var authorizations = command.getAuthorizations();
    if (authorizations.get(Authorization.AUTHORIZED_USERNAME) == null
        && authorizations.get(Authorization.AUTHORIZED_CLIENT_ID) == null) {
      return Either.right(value);
    }
    if (resolveAuthorizedTenantsWithClaims(authorizations).isAuthorizedForTenantId(tenantId)) {
      return Either.right(value);
    }
    return Either.left(notAssignedRejection);
  }

  /**
   * Unlike {@link #checkTenant}, this method has no no-principal skip: a claims-free caller is
   * rejected for every tenant it names, rather than vacuously authorized. (The
   * multi-tenancy-disabled and anonymous-caller skips still apply, same as {@link #checkTenant}. An
   * internal command that is not distributed also passes, if its {@code authorizedTenants} come
   * from {@link #resolveAuthorizedTenants(TypedRecord)}.) Use it only for command sites that call
   * the tenant check directly, with no preceding {@link CslAuthorizationCheck#check}, and that
   * verify membership across a list of tenant IDs at once, using {@link
   * TenantAccess#isAuthorizedForTenantIds}.
   *
   * <p>Without a preceding {@link CslAuthorizationCheck#check} to reject a claims-free command
   * first, sharing {@link #checkTenant}'s no-principal skip here would silently authorize a
   * claims-free caller for every tenant it names instead of rejecting it — hence the guarantee is
   * deliberate, not an oversight.
   *
   * <p>Also unlike {@link #checkTenant}, takes an already-resolved {@link TenantAccess} and a lazy
   * {@code Supplier<Rejection>} rather than resolving internally and building the rejection eagerly
   * — callers that already resolved tenants for the same command don't pay to resolve twice, and
   * the rejection message is only built on the rejected path. The supplier is only invoked on
   * rejection, which never happens for an anonymous principal, so it's always safe to call {@code
   * authorizedTenants.tenantIds()} inside it.
   *
   * @param authorizedTenants the tenants the command's principal is authorized for, as resolved by
   *     {@link #resolveAuthorizedTenants} from the same command's claims
   */
  public <T> Either<Rejection, T> checkTenantsRequiringPrincipal(
      final List<String> tenantIds,
      final TenantAccess authorizedTenants,
      final T value,
      final Supplier<Rejection> notAssignedRejection) {
    if (!securityConfig.isMultiTenancyChecksEnabled()) {
      return Either.right(value);
    }
    if (authorizedTenants.isAuthorizedForTenantIds(tenantIds)) {
      return Either.right(value);
    }
    return Either.left(notAssignedRejection.get());
  }
}
