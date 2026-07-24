/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.SECRET_READ_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.SECRET_REVEAL_AUTHORIZATION;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a deduplicated batch of {@code camunda.secrets.<name>} references, checking {@code
 * SECRET:REVEAL} per reference, and lists the references the caller is authorized to see, checking
 * {@code SECRET:READ}. Each resolved reference succeeds or fails independently; a denied or
 * unresolvable reference is reported in {@link SecretResolution#errors()} rather than failing the
 * batch.
 *
 * <p><b>Phase 1 scope (#56567, #56568):</b> the per-reference authorization, deduplication,
 * reference validation and per-reference outcome routing are real. The secret backend itself is
 * <b>mocked</b> ({@link #mockResolve}, {@link #mockListReferences}); neither endpoint yet uses the
 * {@code SecretStore} wiring. That wiring needs the physical-tenant {@code SecretStoreRegistry} to
 * be reachable from this module, but the registry currently lives in {@code dist}, which this
 * module cannot depend on without a cycle — resolving that dependency direction is tracked
 * alongside #56561 / #57199.
 */
@NullMarked
public class SecretServices extends PhysicalTenantScopedApiServices<SecretServices> {

  /**
   * Bounds the length of a single reference string. Each reference is used as an authorization
   * resource id and (once a real store is wired) a store lookup key, so an unbounded string must
   * not reach either. Enforced here (rather than only at the request-validation layer) so an
   * over-long reference is reported as a per-reference {@link SecretErrorCode#INVALID_REFERENCE}
   * like every other malformed reference, instead of failing the whole batch. Mirrored by the
   * {@code maxLength: 256} on {@code SecretResolveRequest.references} items in {@code
   * secrets.yaml}; kept in sync by {@code SecretRequestValidatorSpecSyncTest}.
   */
  public static final int MAX_REFERENCE_LENGTH = 256;

  private static final Logger LOG = LoggerFactory.getLogger(SecretServices.class);

  private static final String REFERENCE_PREFIX = "camunda.secrets.";

  // The <name> segment of a reference: a single, non-empty token of alphanumeric characters and
  // underscores. Deliberately narrow — the reference is used as an authorization resource id and
  // (once a real store is wired) a store lookup key, so pattern/glob characters ('*', '%'),
  // whitespace and dots must never reach either. Kept identical to the engine detector's
  // single-segment camunda.secrets.<name> pattern (see SecretReferenceLiteralValidator) so both
  // subsystems agree on what counts as a valid reference name for the same syntax.
  private static final Pattern REFERENCE_NAME_PATTERN = Pattern.compile("[\\p{Alnum}_]+");

  // Phase 1 only: the references the mocked backend pretends to know, shared by mockResolve and
  // mockListReferences so a caller who lists then resolves sees consistent references. Any other
  // authorized, valid reference resolves to NOT_FOUND and is absent from the listing. Removed once
  // a real SecretStore is wired (#56561/#57199).
  private static final Set<String> MOCK_RESOLVABLE_REFERENCES =
      Set.of("camunda.secrets.token", "camunda.secrets.a", "camunda.secrets.b");

  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;

  public SecretServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationChecker authorizationChecker,
      final AuthorizationsConfiguration authorizationsConfig,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.authorizationChecker = authorizationChecker;
    this.authorizationsConfig = authorizationsConfig;
  }

  /**
   * Resolves the given references. Duplicates are resolved once (server-side deduplication). The
   * returned {@link SecretResolution} always describes every distinct reference exactly once,
   * across {@code resolved} and {@code errors}.
   *
   * <p>Synchronous today (no broker round-trip while the backend is mocked), but returns a future
   * like every other action method on this base class so the signature does not have to break once
   * the real, likely I/O-bound {@code SecretStore} lookup replaces {@link #mockResolve}
   * (#56561/#57199).
   */
  public CompletableFuture<SecretResolution> resolve(
      final List<String> references, final CamundaAuthentication authentication) {
    try {
      final var distinct = new LinkedHashSet<>(references);
      final List<ResolvedSecret> resolved = new ArrayList<>();
      final List<SecretResolutionError> errors = new ArrayList<>();

      final List<String> validReferences = new ArrayList<>();
      for (final var reference : distinct) {
        if (isValidReference(reference)) {
          validReferences.add(reference);
        } else {
          errors.add(
              new SecretResolutionError(
                  reference,
                  SecretErrorCode.INVALID_REFERENCE,
                  "The secret reference is malformed."));
        }
      }
      if (validReferences.isEmpty()) {
        return CompletableFuture.completedFuture(new SecretResolution(resolved, errors));
      }

      // Authorize before any lookup so an unauthorized caller never receives a value or learns
      // whether the secret exists. A single query covers the whole batch instead of one
      // authorization round-trip per reference.
      final var authorizedScopes =
          retrieveAuthorizedScopes(authentication, SECRET_REVEAL_AUTHORIZATION);

      for (final var reference : validReferences) {
        if (!authorizedScopes.authorizes(reference)) {
          LOG.debug(
              "Denied secret reveal of '{}' for {}",
              reference,
              authentication.formattedPrincipal());
          errors.add(
              new SecretResolutionError(
                  reference,
                  SecretErrorCode.ACCESS_DENIED,
                  "The caller is not authorized to reveal the secret reference."));
          continue;
        }
        mockResolve(reference)
            .ifPresentOrElse(
                value -> {
                  LOG.debug(
                      "Revealed secret '{}' to {}", reference, authentication.formattedPrincipal());
                  resolved.add(new ResolvedSecret(reference, value));
                },
                () ->
                    errors.add(
                        new SecretResolutionError(
                            reference,
                            SecretErrorCode.NOT_FOUND,
                            "No secret was found for the reference.")));
      }
      return CompletableFuture.completedFuture(new SecretResolution(resolved, errors));
    } catch (final Exception ex) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(ex));
    }
  }

  /**
   * Lists the references the caller holds {@code SECRET:READ} on, filtering the backend's full
   * enumeration down to what the caller is authorized to see rather than accepting a
   * caller-supplied batch (contrast {@link #resolve}).
   *
   * <p>Authorizes before enumerating, mirroring {@link #resolve}: a caller with no wildcard and no
   * ID-scoped grant is denied everything, so the (mocked, but eventually store-backed) enumeration
   * is never invoked for them. Once a real {@code SecretStore} is wired, that enumeration is a
   * tenant-wide backend call with real cost (money, rate limits on AWS/GCP); skipping it for a
   * zero-grant caller avoids paying that cost for a result that is discarded anyway.
   *
   * <p>Synchronous today for the same reason {@link #resolve} is (see its Javadoc).
   */
  public CompletableFuture<List<String>> list(final CamundaAuthentication authentication) {
    try {
      final var authorizedScopes =
          retrieveAuthorizedScopes(authentication, SECRET_READ_AUTHORIZATION);
      if (!authorizedScopes.authorizesEverything() && authorizedScopes.isEmpty()) {
        return CompletableFuture.completedFuture(List.of());
      }
      final var references = mockListReferences();
      final var result = references.stream().filter(authorizedScopes::authorizes).toList();
      return CompletableFuture.completedFuture(result);
    } catch (final Exception ex) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(ex));
    }
  }

  /**
   * Retrieves the caller's authorization scopes for {@code requiredAuthorization} in a single
   * query, mirroring the bulk-fetch-then-locally-match pattern {@code
   * DefaultResourceAccessProvider} uses for search pre-filtering, rather than one query per
   * reference.
   */
  private AuthorizedScopes retrieveAuthorizedScopes(
      final CamundaAuthentication authentication,
      final RequiredAuthorization<?> requiredAuthorization) {
    // Matches DocumentServices#hasDocumentPermission: when authorization is disabled
    // cluster-wide, every reference is treated as authorized rather than denied. A deny-all here
    // would make the endpoint unusable in authorization-disabled setups (e.g. C8Run's default),
    // which the epic this endpoint serves explicitly targets.
    if (!authorizationsConfig.isEnabled()) {
      return AuthorizedScopes.everything();
    }
    final var authorizedScopes =
        authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            authentication, requiredAuthorization);
    if (authorizedScopes.contains(AuthorizationScope.WILDCARD)) {
      // A wildcard grant authorizes every reference.
      return AuthorizedScopes.everything();
    }
    final var authorizedResourceIds =
        authorizedScopes.stream()
            .filter(scope -> scope.getMatcher() == AuthorizationResourceMatcher.ID)
            .map(AuthorizationScope::getResourceId)
            .collect(Collectors.toSet());
    return AuthorizedScopes.only(authorizedResourceIds);
  }

  private static boolean isValidReference(final String reference) {
    if (reference == null
        || reference.length() > MAX_REFERENCE_LENGTH
        || !reference.startsWith(REFERENCE_PREFIX)) {
      return false;
    }
    final var name = reference.substring(REFERENCE_PREFIX.length());
    return REFERENCE_NAME_PATTERN.matcher(name).matches();
  }

  private static String bareName(final String reference) {
    return reference.substring(REFERENCE_PREFIX.length());
  }

  /**
   * Mocked secret lookup for Phase 1. Resolves only an explicit allow-list of references to a
   * deterministic placeholder value so dependent work (inbound connectors) can integrate against a
   * live endpoint; every other authorized, valid reference yields {@code NOT_FOUND}, exercising the
   * real not-found path rather than fabricating a value for an arbitrary reference. The value is
   * scoped by physical tenant so the mock cannot mask a cross-tenant leak once the real {@code
   * SecretStore} (which is itself registered per physical tenant) replaces it. TODO(#56561/#57199):
   * replace with {@code SecretStore.resolve(...)} once store is configured.
   */
  private Optional<String> mockResolve(final String reference) {
    if (!MOCK_RESOLVABLE_REFERENCES.contains(reference)) {
      return Optional.empty();
    }
    final var name = reference.substring(REFERENCE_PREFIX.length());
    return Optional.of("mock-value-for-" + name + "-in-tenant-" + getPhysicalTenantId());
  }

  /**
   * Mocked reference enumeration for Phase 1, sharing {@link #MOCK_RESOLVABLE_REFERENCES} with
   * {@link #mockResolve} so a caller who lists then resolves sees consistent references. Sorted for
   * a deterministic response, since the backing {@link Set} has no defined iteration order.
   * TODO(#56561/#57199): replace with {@code SecretStore.list(...)} once store wiring is reachable
   * from this module (see the class Javadoc for why it is not yet).
   */
  private List<String> mockListReferences() {
    return MOCK_RESOLVABLE_REFERENCES.stream().sorted().toList();
  }

  /**
   * The caller's authorization scopes for one {@code RequiredAuthorization}, either "authorizes
   * everything" (disabled cluster-wide authorization, or a wildcard grant) or a concrete set of
   * authorized bare secret names.
   *
   * <p>Grants are keyed by the bare {@code <name>} segment, not the full {@code
   * camunda.secrets.<name>} reference: the connector runtime resolves {@code {{secrets.X}}} by the
   * bare name {@code X} against the store, and users create secrets as {@code X} in Console, so the
   * authorization resource id must be the same bare name for the two to agree. Each reference this
   * class receives from {@link #resolve} / {@link #list} is reduced to its bare name before being
   * matched against the granted names.
   */
  private record AuthorizedScopes(boolean authorizesEverything, Set<String> authorizedNames) {
    static AuthorizedScopes everything() {
      return new AuthorizedScopes(true, Set.of());
    }

    static AuthorizedScopes only(final Set<String> authorizedNames) {
      return new AuthorizedScopes(false, authorizedNames);
    }

    boolean isEmpty() {
      return authorizedNames.isEmpty();
    }

    boolean authorizes(final String reference) {
      return authorizesEverything || authorizedNames.contains(bareName(reference));
    }
  }

  /** The per-reference outcome of a resolve request. */
  public record SecretResolution(
      List<ResolvedSecret> resolved, List<SecretResolutionError> errors) {}

  public record ResolvedSecret(String reference, String value) {}

  public record SecretResolutionError(String reference, SecretErrorCode code, String message) {}

  /**
   * The typed reason a reference could not be resolved. Mirrors the {@code secret-store} SPI's
   * {@code SecretErrorCode}; kept independent so the service module does not depend on the store
   * SPI while the backend is mocked.
   */
  public enum SecretErrorCode {
    NOT_FOUND,
    ACCESS_DENIED,
    INVALID_REFERENCE
  }
}
