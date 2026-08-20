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

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreException;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a deduplicated batch of {@code camunda.secrets.<name>} references, checking {@code
 * SECRET:REVEAL} per reference, and lists the references the caller is authorized to see, checking
 * {@code SECRET:READ}. Each resolved reference succeeds or fails independently; a denied or
 * unresolvable reference is reported in {@link SecretResolution#errors()} rather than failing the
 * batch.
 *
 * <p>Both read the secret stores of this service's physical tenant, taken from the {@link
 * SecretStoreRegistry} the service is constructed with. Each store caches what it resolves, so
 * resolving is cache-first without this service driving a cache itself: a reference whose value the
 * store already holds is served from there, and only the remaining references reach the backing
 * store. Listing always polls the stores, since what they hold is the values read so far rather
 * than the tenant's set of secrets.
 */
@NullMarked
public class SecretServices extends PhysicalTenantScopedApiServices<SecretServices> {

  /**
   * Bounds the length of a single reference string. Each reference is used as an authorization
   * resource id and a store lookup key, so an unbounded string must not reach either. Enforced here
   * (rather than only at the request-validation layer) so an over-long reference is reported as a
   * per-reference {@link SecretErrorCode#INVALID_REFERENCE} like every other malformed reference,
   * instead of failing the whole batch. Mirrored by the {@code maxLength: 256} on {@code
   * SecretResolveRequest.references} items in {@code secrets.yaml}; kept in sync by {@code
   * SecretRequestValidatorSpecSyncTest}.
   */
  public static final int MAX_REFERENCE_LENGTH = 256;

  /**
   * The {@code <name>} segment of a reference: a single, non-empty token of alphanumerics, {@code
   * _} and {@code -}. The charset is what keeps the FEEL expression an author writes, the
   * authorization resource id a permission is granted on, and the store lookup key one identifier —
   * the same string on all three surfaces, never one of them escaped or rewritten.
   *
   * <p>Writing that string in FEEL is a separate matter: a dash is in the charset, but FEEL reads a
   * bare dash as the minus operator, so a dashed name is only ever authored backtick-escaped
   * ({@code =camunda.secrets.`db-password`}). It is in regardless, because the backing stores
   * routinely hold dashed names and leaving it out made {@code db-password} storable and listable
   * but never resolvable. A dot is out because unquoted it separates path segments — {@code
   * camunda.secrets.tls.crt} is four FEEL segments rather than a reference — and the raw-text
   * scanners that share this charset cannot tell such a name from a trailing path access.
   *
   * <p>Not an injection boundary, and must not be narrowed as if it were: every authorization
   * lookup matches the resource id exactly (a terms query on ES/OS, a parameterized {@code IN} on
   * RDBMS, {@code Set.contains} here) rather than by pattern, and the charset has always admitted
   * {@code _}, which is a SQL {@code LIKE} single-character wildcard.
   *
   * <p>Kept identical to the name segment of {@code SecretReference.REFERENCE_PATTERN} in {@code
   * zeebe/engine}, which this module cannot depend on, so that both subsystems agree on what is a
   * valid reference name. Public for the same reason {@link #MAX_REFERENCE_LENGTH} is: {@code
   * SecretReferenceCharsetSyncTest}, in the one module with both artifacts on its classpath, is
   * what holds the two definitions together.
   */
  public static final Pattern REFERENCE_NAME_PATTERN = Pattern.compile("[\\p{Alnum}_-]+");

  private static final Logger LOG = LoggerFactory.getLogger(SecretServices.class);

  private static final String REFERENCE_PREFIX = "camunda.secrets.";

  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;
  private final SecretStoreRegistry secretStoreRegistry;

  public SecretServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationChecker authorizationChecker,
      final AuthorizationsConfiguration authorizationsConfig,
      final SecretStoreRegistry secretStoreRegistry,
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
    this.secretStoreRegistry = secretStoreRegistry;
  }

  /**
   * Returns the secret stores of this service's physical tenant. {@link #resolve} and {@link #list}
   * read the field directly, so this exists only so the wiring can be asserted, deliberately not a
   * production read path: a caller holding the registry could read a value without the
   * per-reference {@code SECRET:REVEAL} check {@link #resolve} applies.
   */
  @VisibleForTesting
  public SecretStoreRegistry getSecretStoreRegistry() {
    return secretStoreRegistry;
  }

  /**
   * Resolves the given references. Duplicates are resolved once (server-side deduplication). The
   * returned {@link SecretResolution} always describes every distinct reference exactly once,
   * across {@code resolved} and {@code errors}.
   *
   * <p>Reference validation and authorization run on the calling thread, as they did before a store
   * was wired. The store lookup is handed to the API executor: reading an external store (a file,
   * an AWS Secrets Manager call) is blocking I/O that must not occupy the request thread.
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

      final List<String> authorizedReferences = new ArrayList<>();
      for (final var reference : validReferences) {
        if (authorizedScopes.authorizes(reference)) {
          authorizedReferences.add(reference);
        } else {
          LOG.debug(
              "Denied secret reveal of '{}' for {}",
              reference,
              authentication.formattedPrincipal());
          errors.add(
              new SecretResolutionError(
                  reference,
                  SecretErrorCode.ACCESS_DENIED,
                  "The caller is not authorized to reveal the secret reference."));
        }
      }
      if (authorizedReferences.isEmpty()) {
        return CompletableFuture.completedFuture(new SecretResolution(resolved, errors));
      }

      return CompletableFuture.supplyAsync(
          () -> readFromStores(authorizedReferences, resolved, errors, authentication),
          executorProvider.getExecutor());
    } catch (final Exception ex) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(ex));
    }
  }

  /**
   * Asks each configured store for the authorized references, adding every outcome to {@code
   * resolved} or {@code errors}. A store caches what it resolves, so a reference it already holds
   * never reaches its backing store and every value it answers with is cached; this method only
   * decides what each outcome means to the API. A reference nothing answered for is reported as
   * {@code NOT_FOUND}.
   *
   * <p>The first store to answer for a reference decides it, a failure included. A physical tenant
   * is capped at one configured store in this release (enforced at startup by {@code
   * SecretStoreConfiguration}), so the loop below sees a single store and there is no second store
   * to fall back to. Lifting that cap has to revisit this, since a store's {@code NOT_FOUND} counts
   * as an answer here and would have to become "ask the next store" instead.
   *
   * <p>It also has to revisit the sequential loop itself: every store read is blocking I/O (a file
   * read, an AWS Secrets Manager call), so several stores have to be read concurrently rather than
   * one after another, which costs the sum of their latencies. Sequential is only free while a
   * single store is all there is.
   */
  private SecretResolution readFromStores(
      final List<String> references,
      final List<ResolvedSecret> resolved,
      final List<SecretResolutionError> errors,
      final CamundaAuthentication authentication) {
    // the references still to be resolved, in a stable order so the outcomes do not come out in
    // whatever order a hash map happens to iterate in
    final Set<String> pending = new LinkedHashSet<>(references);

    try {
      for (final var store : secretStoreRegistry.getStores().entrySet()) {
        if (pending.isEmpty()) {
          break;
        }
        readFromStore(store.getKey(), store.getValue(), pending, resolved, errors, authentication);
      }
    } catch (final SecretStoreException e) {
      // a store that cannot be read at all fails the whole request rather than reporting every
      // reference as missing, which a caller could not tell apart from a genuinely empty store
      LOG.warn("Failed to read a configured secret store while resolving secret references", e);
      throw new ServiceException(
          "Failed to read the configured secret store.", ServiceException.Status.UNAVAILABLE);
    }

    // nothing answered for these: either no store is configured, or a store left the name out of
    // the results it answered with
    pending.forEach(reference -> errors.add(errorFor(reference, SecretErrorCode.NOT_FOUND)));
    return new SecretResolution(resolved, errors);
  }

  /**
   * Asks one store for every reference still {@code pending}, revealing the ones it resolves and
   * reporting the ones it fails on, taking both out of {@code pending}. A reference the store
   * answers without a result for stays pending, since an absent entry is not an answer.
   */
  private void readFromStore(
      final String storeId,
      final SecretStore store,
      final Set<String> pending,
      final List<ResolvedSecret> resolved,
      final List<SecretResolutionError> errors,
      final CamundaAuthentication authentication) {
    // the references to ask this store for, keyed by the bare name the stores are keyed by. A
    // separate map, since resolving a reference takes it out of the pending ones.
    final Map<String, String> requested = new LinkedHashMap<>();
    pending.forEach(reference -> requested.put(bareNameOf(reference), reference));

    final var results = store.resolve(requested.keySet());
    requested.forEach(
        (name, reference) -> {
          switch (results.get(name)) {
            case final SecretResolutionResult.Resolved value -> {
              reveal(reference, value.value(), resolved, authentication);
              pending.remove(reference);
            }
            case final SecretResolutionResult.Failed failure -> {
              LOG.debug(
                  "Store '{}' could not resolve secret '{}': {} ({})",
                  storeId,
                  reference,
                  failure.code(),
                  failure.message());
              errors.add(errorFor(reference, toApiErrorCode(failure.code())));
              pending.remove(reference);
            }
            // a store that answers without an entry for a requested name is treated as not knowing
            // it, rather than dropping the reference from the response entirely
            case null -> {}
          }
        });
  }

  private void reveal(
      final String reference,
      final String value,
      final List<ResolvedSecret> resolved,
      final CamundaAuthentication authentication) {
    LOG.debug("Revealed secret '{}' to {}", reference, authentication.formattedPrincipal());
    resolved.add(new ResolvedSecret(reference, value));
  }

  /**
   * Maps a store's failure to the API-facing error code. A store's {@code ACCESS_DENIED} is the
   * cluster's own store credentials being rejected, not the caller lacking {@code SECRET:REVEAL},
   * so it must not surface as this API's {@code ACCESS_DENIED}, which always refers to the caller's
   * permission.
   *
   * <p>Deliberately without a {@code default} branch: the SPI and this gateway ship from the same
   * repository in the same release, so a code added to the SPI has to be mapped here in that same
   * change, and an unhandled one is a compile error rather than a silent {@code UNREADABLE}.
   */
  private static SecretErrorCode toApiErrorCode(final io.camunda.secretstore.SecretErrorCode code) {
    return switch (code) {
      case NOT_FOUND -> SecretErrorCode.NOT_FOUND;
      case INVALID_REF -> SecretErrorCode.INVALID_REFERENCE;
      case ACCESS_DENIED, UNREADABLE -> SecretErrorCode.UNREADABLE;
    };
  }

  private static SecretResolutionError errorFor(
      final String reference, final SecretErrorCode code) {
    return new SecretResolutionError(reference, code, messageFor(code));
  }

  /**
   * The message reported for a failed reference. Deliberately fixed per code: a store's own message
   * carries its internals (secret ids, file paths, status codes) and is only logged.
   */
  private static String messageFor(final SecretErrorCode code) {
    return switch (code) {
      case NOT_FOUND -> "No secret was found for the reference.";
      case ACCESS_DENIED -> "The caller is not authorized to reveal the secret reference.";
      case INVALID_REFERENCE -> "The configured secret store rejected the reference.";
      case UNREADABLE -> "The configured secret store could not read a value for the reference.";
    };
  }

  /**
   * Lists the references the caller holds {@code SECRET:READ} on, filtering the backend's full
   * enumeration down to what the caller is authorized to see rather than accepting a
   * caller-supplied batch (contrast {@link #resolve}).
   *
   * <p>Authorizes before enumerating, mirroring {@link #resolve}: a caller with no wildcard and no
   * ID-scoped grant is denied everything, so the enumeration is never invoked for them. That
   * enumeration is a tenant-wide store call with real cost (money, rate limits on AWS/GCP);
   * skipping it for a zero-grant caller avoids paying that cost for a result that is discarded
   * anyway.
   *
   * <p>Runs the enumeration on the API executor, for the same reason {@link #resolve} does.
   */
  public CompletableFuture<List<String>> list(final CamundaAuthentication authentication) {
    try {
      final var authorizedScopes =
          retrieveAuthorizedScopes(authentication, SECRET_READ_AUTHORIZATION);
      if (!authorizedScopes.authorizesEverything() && authorizedScopes.isEmpty()) {
        return CompletableFuture.completedFuture(List.of());
      }
      return CompletableFuture.supplyAsync(
          () -> listFromStores().stream().filter(authorizedScopes::authorizes).toList(),
          executorProvider.getExecutor());
    } catch (final Exception ex) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(ex));
    }
  }

  /**
   * Enumerates the references of every configured store, sorted and without duplicates. A store's
   * {@code list()} deliberately bypasses what it has cached: that is the values read so far, which
   * is not the tenant's set of secrets.
   *
   * <p>A listed reference is usable verbatim with {@link #resolve}, but not always in a FEEL
   * expression: a name FEEL does not accept as a bare identifier — a dashed one above all — has to
   * be backtick-escaped there, {@code =camunda.secrets.`db-password`}. The endpoint's description
   * in {@code secrets.yaml} says so, since the caller cannot see this method.
   */
  private List<String> listFromStores() {
    final var references = new TreeSet<String>();
    try {
      for (final var store : secretStoreRegistry.getStores().entrySet()) {
        for (final var name : store.getValue().list()) {
          if (isResolvableName(name)) {
            references.add(REFERENCE_PREFIX + name);
          } else {
            // A store may allow names this API cannot: a name carrying a dot is outside the
            // reference charset resolve() accepts and cannot be granted a permission on either,
            // so listing it would only offer the caller a reference every other surface here
            // rejects. See REFERENCE_NAME_PATTERN for why the charset stops where it does.
            LOG.debug(
                "Omitting secret '{}' of store '{}' from the listing: its name cannot form a valid"
                    + " secret reference",
                name,
                store.getKey());
          }
        }
      }
    } catch (final SecretStoreException e) {
      LOG.warn("Failed to read a configured secret store while listing secret references", e);
      throw new ServiceException(
          "Failed to read the configured secret store.", ServiceException.Status.UNAVAILABLE);
    }
    return List.copyOf(references);
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

  /**
   * Whether the caller's reference is one this service accepts.
   *
   * <p>The length is bounded before {@link #bareNameOf} takes the name out, so an over-long
   * reference is rejected without copying it. Bounding the whole reference is the same test {@link
   * #isResolvableName} applies to the name, since a reference that reached it carries the prefix.
   */
  private static boolean isValidReference(final String reference) {
    return reference != null
        && reference.length() <= MAX_REFERENCE_LENGTH
        && reference.startsWith(REFERENCE_PREFIX)
        && isResolvableName(bareNameOf(reference));
  }

  /**
   * Whether a store's secret name can form a reference this service accepts, so that {@link #list}
   * only ever offers references {@link #resolve} takes.
   *
   * <p>A {@code null} name is one of those it cannot. The store SPI is {@code @NullMarked} and so
   * promises not to list one, but a store is third-party code: a broken one should cost the caller
   * that name, not the whole listing with an internal error.
   */
  private static boolean isResolvableName(final @Nullable String name) {
    return name != null
        && REFERENCE_PREFIX.length() + name.length() <= MAX_REFERENCE_LENGTH
        && REFERENCE_NAME_PATTERN.matcher(name).matches();
  }

  /** The bare secret name the stores are keyed by, i.e. the reference without its prefix. */
  private static String bareNameOf(final String reference) {
    return reference.substring(REFERENCE_PREFIX.length());
  }

  /**
   * The caller's authorization scopes for one {@code RequiredAuthorization}, either "authorizes
   * everything" (disabled cluster-wide authorization, or a wildcard grant) or a concrete set of
   * authorized secret references.
   *
   * <p>Grants are keyed by the full {@code camunda.secrets.<name>} reference — the canonical FEEL
   * reference that appears in the BPMN/FEEL expression — not the bare {@code <name>} segment. A
   * permission grant on that string maps 1:1 to what a process author actually writes, and the same
   * reference is the API-facing identifier in {@link #resolve} / {@link #list}, so the
   * authorization resource id, the API name and the FEEL expression all agree on one identifier.
   */
  private record AuthorizedScopes(boolean authorizesEverything, Set<String> authorizedReferences) {
    static AuthorizedScopes everything() {
      return new AuthorizedScopes(true, Set.of());
    }

    static AuthorizedScopes only(final Set<String> authorizedReferences) {
      return new AuthorizedScopes(false, authorizedReferences);
    }

    boolean isEmpty() {
      return authorizedReferences.isEmpty();
    }

    boolean authorizes(final String reference) {
      return authorizesEverything || authorizedReferences.contains(reference);
    }
  }

  /**
   * The per-reference outcome of a resolve request. Copies both lists, since {@link
   * #readFromStores} builds them by mutation and a caller must not be handed a handle on that.
   */
  public record SecretResolution(
      List<ResolvedSecret> resolved, List<SecretResolutionError> errors) {

    public SecretResolution {
      resolved = List.copyOf(resolved);
      errors = List.copyOf(errors);
    }
  }

  /**
   * A revealed secret. {@code toString} masks the value, mirroring the {@code secret-store} SPI's
   * {@code SecretResolutionResult.Resolved}: a resolved value must never reach a log line, and the
   * record's generated {@code toString} would print it in any log statement, exception message or
   * test failure dump this ever lands in.
   */
  public record ResolvedSecret(String reference, String value) {

    @Override
    public String toString() {
      return "ResolvedSecret[reference=" + reference + ", value=***]";
    }
  }

  public record SecretResolutionError(String reference, SecretErrorCode code, String message) {}

  /**
   * The typed reason a reference could not be resolved. Mirrors the {@code secret-store} SPI's
   * {@code SecretErrorCode}; kept independent so the API-facing error contract does not couple to
   * the SPI's enum and can evolve with the endpoint instead.
   */
  public enum SecretErrorCode {
    NOT_FOUND,
    ACCESS_DENIED,
    INVALID_REFERENCE,
    UNREADABLE
  }
}
