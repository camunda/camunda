/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.OidcUserInfoAugmentationConfiguration;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caching {@link OidcClaimsProvider} that, when UserInfo augmentation is enabled, calls the OIDC
 * UserInfo endpoint on cache miss and <em>additively</em> merges claims onto the JWT: JWT wins on
 * every conflict, UserInfo only contributes claims absent from the JWT. This preserves the
 * cryptographic guarantee of the signed token — UserInfo cannot override {@code sub}, {@code iss},
 * {@code aud}, {@code exp}, or any other JWT-supplied claim.
 *
 * <p>Runtime fail-open on IdP errors: a {@code /userinfo} fetch failure (network, non-2xx, parse
 * failure, or UserInfo {@code sub} mismatch per OIDC Core §5.3.2) is logged at ERROR and the
 * provider returns the JWT claims unchanged. Degraded entries are negatively cached for a short TTL
 * ({@link OidcUserInfoAugmentationConfiguration#NEGATIVE_CACHE_TTL}) to avoid hammering a down IdP.
 */
public class CachingOidcClaimsProvider implements OidcClaimsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(CachingOidcClaimsProvider.class);
  private static final String CACHE_METRIC = "camunda.oidc.userinfo.cache";
  private static final String FETCH_METRIC = "camunda.oidc.userinfo.fetch";

  private final OidcAuthenticationConfiguration oidcConfig;
  private final Map<String, URI> userInfoUriByIssuer;
  private final OidcUserInfoClient userInfoClient;
  private final Cache<String, CacheEntry> cache;
  private final Counter hitCounter;
  private final Counter missCounter;
  private final Timer fetchTimer;
  private final Counter fetchFailureCounter;
  private final Duration clockSkew;

  public CachingOidcClaimsProvider(
      final OidcAuthenticationConfiguration oidcConfig,
      final Map<String, URI> userInfoUriByIssuer,
      final OidcUserInfoClient userInfoClient,
      final MeterRegistry meterRegistry) {
    this.oidcConfig = Objects.requireNonNull(oidcConfig);
    this.userInfoUriByIssuer =
        userInfoUriByIssuer == null ? Map.of() : Map.copyOf(userInfoUriByIssuer);
    this.userInfoClient = Objects.requireNonNull(userInfoClient);
    clockSkew = oidcConfig.getClockSkew();
    final OidcUserInfoAugmentationConfiguration aug = oidcConfig.getUserInfoAugmentation();
    cache =
        Caffeine.newBuilder()
            .maximumSize(aug.getCacheMaxSize())
            .expireAfter(new EntryExpiry(aug.getCacheTtl()))
            .build();
    hitCounter = Counter.builder(CACHE_METRIC).tag("result", "hit").register(meterRegistry);
    missCounter = Counter.builder(CACHE_METRIC).tag("result", "miss").register(meterRegistry);
    fetchTimer = Timer.builder(FETCH_METRIC).register(meterRegistry);
    fetchFailureCounter =
        Counter.builder(FETCH_METRIC).tag("outcome", "failure").register(meterRegistry);
  }

  @Override
  public Map<String, Object> claimsFor(
      final Map<String, Object> jwtClaims, final String tokenValue) {
    if (!oidcConfig.getUserInfoAugmentation().isEnabled()) {
      return jwtClaims;
    }

    // Resolve the userinfo URI per-token by the JWT's 'iss' claim. This guarantees we always
    // hand the token to the same IdP that signed it — critical in multi-provider setups where
    // tokens from provider A must not be sent to provider B's /userinfo endpoint.
    final Object issClaim = jwtClaims.get("iss");
    final URI userInfoUri = issClaim instanceof final String s ? userInfoUriByIssuer.get(s) : null;
    if (userInfoUri == null) {
      // Either the JWT has no 'iss', or no registration matches it. Degrade rather than 500.
      LOG.error(
          "UserInfo augmentation enabled but no userinfo URI is registered for issuer '{}'; "
              + "returning JWT-only claims",
          issClaim);
      return jwtClaims;
    }

    final String key = cacheKey(jwtClaims);
    if (key == null) {
      // Neither 'jti' nor (sub+iat+exp) are usable as a cache key for this token.
      // Bypass the cache entirely — fetch + merge once, don't store. Rare in practice
      // (every mainstream IdP emits at least sub+iat+exp on access tokens) but keeps
      // the feature correct for malformed JWTs.
      return loadEntry(jwtClaims, tokenValue, userInfoUri).claims();
    }
    // Fast-path: was it already cached? If so, record the hit and return.
    // Narrow race: a concurrent thread may populate between this check and the load below,
    // in which case our load() sees the populated entry and returns without running the
    // loader. Counter bookkeeping may skew by one under pure load but functional behaviour
    // (single fetch per key) is preserved by Caffeine's atomic loading contract.
    final CacheEntry cached = cache.getIfPresent(key);
    if (cached != null) {
      hitCounter.increment();
      return cached.claims();
    }
    // Miss — use Caffeine's atomic cache.get(key, loader) so concurrent misses on the same
    // key coalesce: exactly one thread runs the loader; the others wait for the result.
    final CacheEntry entry =
        cache.get(
            key,
            k -> {
              missCounter.increment();
              return loadEntry(jwtClaims, tokenValue, userInfoUri);
            });
    return entry.claims();
  }

  private CacheEntry loadEntry(
      final Map<String, Object> jwtClaims, final String tokenValue, final URI userInfoUri) {
    try {
      final Map<String, Object> userInfoClaims =
          fetchTimer.recordCallable(() -> userInfoClient.fetch(userInfoUri, tokenValue));
      return mergeEntry(jwtClaims, userInfoClaims);
    } catch (final InterruptedException e) {
      // Restore interrupt flag so the calling thread can terminate cleanly.
      Thread.currentThread().interrupt();
      fetchFailureCounter.increment();
      LOG.error("UserInfo fetch interrupted; returning JWT-only claims", e);
      return CacheEntry.degraded(jwtClaims);
    } catch (final Exception e) {
      fetchFailureCounter.increment();
      LOG.error(
          "UserInfo augmentation failed for bearer request; continuing with JWT-only claims. "
              + "Authorization may be incomplete if required claims are only available via "
              + "/userinfo. Ensure IdP availability is monitored.",
          e);
      return CacheEntry.degraded(jwtClaims);
    }
  }

  /**
   * Drop every cached entry. Used by integration tests to isolate state between test methods when
   * the provider is a Spring-managed singleton.
   */
  @VisibleForTesting
  public void invalidateCache() {
    cache.invalidateAll();
  }

  /**
   * Apply additive merge: JWT wins on every conflict, UserInfo contributes only claims absent from
   * the JWT. Before merging, enforce OIDC Core §5.3.2 — UserInfo {@code sub} MUST equal JWT {@code
   * sub}. A mismatch is treated as a degradation event (fail-open, JWT-only claims).
   */
  private CacheEntry mergeEntry(
      final Map<String, Object> jwtClaims, final Map<String, Object> userInfoClaims) {
    final Object userInfoSub = userInfoClaims.get("sub");
    if (userInfoSub != null && !userInfoSub.equals(jwtClaims.get("sub"))) {
      fetchFailureCounter.increment();
      LOG.error(
          "UserInfo 'sub' did not match JWT 'sub' (OIDC Core §5.3.2 violation); "
              + "returning JWT-only claims. Possible IdP misconfiguration.");
      return CacheEntry.degraded(jwtClaims);
    }
    // Start with UserInfo, let JWT overwrite — JWT wins on every key they share.
    final Map<String, Object> merged = new HashMap<>(userInfoClaims);
    merged.putAll(jwtClaims);
    return CacheEntry.augmented(Map.copyOf(merged), tokenExpiry(jwtClaims));
  }

  /**
   * Build a cache key that is unique per (issuer, token) pair but stores no bearer-token material.
   * Returns null when neither {@code jti} nor (sub + iat + exp) are usable — callers should bypass
   * the cache for that request.
   *
   * <p>The {@code iss} prefix is required because {@code jti} is only required to be unique per
   * issuer (RFC 7519); two providers can legitimately issue tokens with identical {@code jti}
   * values, which would otherwise collide and leak claims across issuers.
   *
   * <p>When {@code jti} is absent the fallback uses {@code sub + iat + exp} — identifiers already
   * present on every standard access token. This is a far better cache-key choice than {@code
   * SHA-256(token)} because a heap dump or metrics leak of the key itself reveals only auth
   * metadata ("alice had a token valid for this window"), not a token-correlatable fingerprint.
   */
  private static String cacheKey(final Map<String, Object> jwtClaims) {
    final Object iss = jwtClaims.get("iss");
    if (!(iss instanceof final String issuer) || issuer.isBlank()) {
      return null;
    }
    final Object jti = jwtClaims.get("jti");
    if (jti instanceof final String s && !s.isBlank()) {
      return "jti:" + issuer + ":" + s;
    }
    final Object sub = jwtClaims.get("sub");
    final Long iat = epochSecond(jwtClaims.get("iat"));
    final Long exp = epochSecond(jwtClaims.get("exp"));
    if (sub instanceof String && iat != null && exp != null) {
      return "sie:" + issuer + ":" + sub + ":" + iat + ":" + exp;
    }
    return null;
  }

  private static Long epochSecond(final Object value) {
    if (value instanceof final Instant i) {
      return i.getEpochSecond();
    }
    if (value instanceof final Number n) {
      return n.longValue();
    }
    return null;
  }

  private static Instant tokenExpiry(final Map<String, Object> jwtClaims) {
    // Spring's NimbusJwtDecoder + MappedJwtClaimSetConverter converts 'exp' to java.time.Instant,
    // so that branch must come first. The Number branch covers test fixtures and decoders that
    // surface the raw epoch-seconds value.
    final Object exp = jwtClaims.get("exp");
    if (exp instanceof final Instant i) {
      return i;
    }
    if (exp instanceof final Number n) {
      return Instant.ofEpochSecond(n.longValue());
    }
    LOG.warn(
        "JWT 'exp' claim is neither Instant nor Number (was {}); "
            + "falling back to 5-minute cache TTL for this token",
        exp == null ? "null" : exp.getClass().getSimpleName());
    return Instant.now().plus(Duration.ofMinutes(5));
  }

  /**
   * Cache entry. A {@code degraded} entry represents a fail-open outcome: we couldn't fetch (or
   * validate) UserInfo, so we returned JWT claims unchanged and cached that decision for the
   * negative-cache TTL to prevent hammering the IdP.
   */
  private record CacheEntry(Map<String, Object> claims, Instant tokenExp, boolean degraded) {

    static CacheEntry augmented(final Map<String, Object> claims, final Instant tokenExp) {
      return new CacheEntry(claims, tokenExp, false);
    }

    static CacheEntry degraded(final Map<String, Object> jwtClaims) {
      return new CacheEntry(jwtClaims, null, true);
    }
  }

  /**
   * Caffeine {@link Expiry} that expires augmented entries at min(configured TTL, token exp − skew
   * − now) and degraded entries at a short fixed negative-cache TTL.
   */
  private final class EntryExpiry implements Expiry<String, CacheEntry> {
    private final Duration configuredTtl;

    EntryExpiry(final Duration configuredTtl) {
      this.configuredTtl = configuredTtl;
    }

    @Override
    public long expireAfterCreate(
        final String key, final CacheEntry value, final long currentTime) {
      return ttlNanos(value);
    }

    @Override
    public long expireAfterUpdate(
        final String key,
        final CacheEntry value,
        final long currentTime,
        final long currentDuration) {
      return ttlNanos(value);
    }

    @Override
    public long expireAfterRead(
        final String key,
        final CacheEntry value,
        final long currentTime,
        final long currentDuration) {
      return currentDuration;
    }

    private long ttlNanos(final CacheEntry value) {
      if (value.degraded()) {
        return OidcUserInfoAugmentationConfiguration.NEGATIVE_CACHE_TTL.toNanos();
      }
      final Duration untilExp = Duration.between(Instant.now(), value.tokenExp()).minus(clockSkew);
      final Duration effective =
          untilExp.isNegative() || untilExp.isZero() || untilExp.compareTo(configuredTtl) < 0
              ? untilExp
              : configuredTtl;
      if (effective.isNegative() || effective.isZero()) {
        return 0L;
      }
      return effective.toNanos();
    }
  }
}
