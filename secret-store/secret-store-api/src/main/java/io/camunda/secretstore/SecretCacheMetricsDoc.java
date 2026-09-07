/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import com.github.benmanes.caffeine.cache.RemovalCause;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.docs.MeterDocumentation;

/**
 * Meters for the {@link CaffeineSecretCache} each configured secret store resolves through.
 * Together with {@code camunda.secret.resolution.*} in the engine, they say whether a slow
 * resolution is a slow store or a cache that is not holding what callers ask for.
 *
 * <p>These meters exist only for a cache this module created. A {@link SecretStore} that implements
 * {@link LocallyCachedSecretStore} natively caches inside its own SDK and is never wrapped, so it
 * emits none of them — an absent series for such a store is not a broken cache. {@link
 * InMemorySecretCache} is likewise uninstrumented: it exists for callers and tests that want no
 * eviction at all, so there is nothing here to observe. A tenant left on the noop store — the
 * fallback when no store is configured at all — emits none of them either: that store answers every
 * name permanently missing, so its cache can never hold a value and a hit rate published for it
 * would read 0% forever against nothing an operator could tune.
 *
 * <p>No meter here is tagged by secret name or value. The cache is keyed by the bare secret name,
 * so such a tag would be both unbounded cardinality and customer data on the metrics endpoint. That
 * bounds what these meters disclose without making it nothing: the values still say how many
 * secrets a physical tenant holds and how often they stop resolving, so this endpoint stays as
 * operator-facing as the rest of it.
 *
 * <p>Implements Micrometer's own {@link MeterDocumentation} rather than the {@code
 * ExtendedMeterDocumentation} the rest of the repository uses: that interface lives in {@code
 * zeebe-util}, whose dependency set (guava, kryo, agrona, {@code zeebe-protocol}) is far larger
 * than this module and every secret store implementation built on it should carry. {@link
 * #getDescription()} is declared here instead, so a meter reads the same way it does elsewhere.
 */
@SuppressWarnings("NullableProblems")
public enum SecretCacheMetricsDoc implements MeterDocumentation {
  /** Secret cache lookups, split by whether the cache held the value */
  CACHE_RESULT {
    @Override
    public String getDescription() {
      return "Number of secret cache lookups, per store and per result, so the hit rate is "
          + "HIT / (HIT + MISS). Every lookup a caller makes is counted exactly once. Both reads "
          + "land here: the cache-first resolution path, and the job-activation lookup that only "
          + "ever reads the cache. The counter cannot tell those apart, so a falling hit rate says "
          + "the cache is not holding what callers ask for without saying which caller pays for it. "
          + "Nor does a MISS mean the value could have been held: a name the store answers "
          + "permanently — deleted, denied, or an invalid reference — is never cached, so every "
          + "lookup of it misses for as long as it is referenced, once per job activation attempt "
          + "on the activation path. Read a low hit rate against "
          + "camunda.secret.resolution.outcome first: misses concentrated on references that never "
          + "resolve are not a cache to tune. The cache is held in memory only, so every restart "
          + "starts the rate at zero and climbs as callers warm it: alert on a rate that stays low, "
          + "not on the value shortly after a deployment.";
    }

    @Override
    public String getName() {
      return "camunda.secret.cache.result";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {SecretCacheKeyNames.STORE, SecretCacheKeyNames.RESULT};
    }
  },

  /** Entries removed from a secret cache, split by what removed them */
  CACHE_EVICTIONS {
    @Override
    public String getDescription() {
      return "Number of entries removed from a secret cache, per store and per cause. SIZE and "
          + "EXPIRED are what say the bound no longer fits the workload: sustained SIZE evictions "
          + "mean the per-store maximum set by camunda.secrets.cache.max-size (default "
          + CaffeineSecretCache.DEFAULT_MAX_SIZE
          + " entries) is smaller than the working set, while a high EXPIRED rate together with a "
          + "low hit rate means the TTL set by camunda.secrets.cache.ttl (default "
          + CaffeineSecretCache.DEFAULT_TTL.toMinutes()
          + "m) is shorter than the interval callers resolve at. Both bounds are configurable and "
          + "overridable per physical tenant, so a rate that stays high on either is a bound to "
          + "raise rather than only a number to report. A forward jump of the "
          + "cluster clock is the one EXPIRED spike that means nothing: expiry follows that clock, "
          + "so time travel through /actuator/clock expires the whole cache at once. EXPLICIT "
          + "counts the invalidation the caching store performs when a store answers a secret "
          + "permanently — deleted, denied, or an invalid reference — so it tracks secrets "
          + "disappearing rather than the cache running out of room. Replacing the value of a name "
          + "already cached is not counted at all: the caching store writes over an existing name "
          + "on every re-resolve, so counting that would bury the causes that mean something.";
    }

    @Override
    public String getName() {
      return "camunda.secret.cache.evictions";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {SecretCacheKeyNames.STORE, SecretCacheKeyNames.CAUSE};
    }
  },

  /** Entries a secret cache currently holds */
  CACHE_SIZE {
    @Override
    public String getDescription() {
      return "Estimated number of entries a secret cache currently holds, per store, out of the "
          + "per-store maximum set by camunda.secrets.cache.max-size (default "
          + CaffeineSecretCache.DEFAULT_MAX_SIZE
          + " entries, overridable per physical tenant). Estimated because eviction is "
          + "asynchronous, so the value can briefly sit above that maximum and can lag a removal: "
          + "read it as a level to compare against it, not as an exact count. The default is "
          + "stated here because the maximum is published on no meter of its own. Sitting at the "
          + "maximum with a healthy hit rate is fine; sitting there while "
          + "camunda.secret.cache.evictions with cause=SIZE keeps rising is the signal that the "
          + "cache is too small.";
    }

    @Override
    public String getName() {
      return "camunda.secret.cache.size";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    // deliberately no base unit: Micrometer appends it to the meter name, so declaring `entries`
    // would publish camunda_secret_cache_size_entries and break every dashboard built on the name

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {SecretCacheKeyNames.STORE};
    }
  };

  /** Returns the description (also known as {@code help} in some systems) for the given meter. */
  public abstract String getDescription();

  @SuppressWarnings("NullableProblems")
  public enum SecretCacheKeyNames implements KeyName {
    /**
     * The ID of the secret store whose cache this is, as the operator configured it under {@code
     * camunda.secrets.stores.<type>.<id>}.
     *
     * <p>Bounded by configuration, unlike the identically named tag on {@code
     * camunda.secret.resolution.*}, which carries whatever store ID a secret reference names and so
     * needs a bound of its own. The two use the same key so a dashboard can join on it.
     *
     * <p>A store ID is only unique within one physical tenant. Nothing in this module knows about
     * tenants, so whoever registers these meters for more than one tenant has to keep them apart —
     * the Spring wiring does it by giving each tenant a registry tagged with its physical tenant
     * ID.
     */
    STORE("store"),
    /**
     * Whether a lookup found the value in the cache. A {@link SecretCacheResult}.
     *
     * <p>Keyed {@code result} rather than the {@code type} that {@code CaffeineCacheStatsCounter}
     * in {@code zeebe-util} uses for the same hit/miss split on the exporter and process caches.
     * This is not an oversight to correct: a dashboard for this feature joins these meters with
     * {@code camunda.secret.resolution.*}, whose result tag is also {@code result}, and renaming
     * this to match the generic caches would break the join that is actually used to read it.
     */
    RESULT("result"),
    /** What removed an entry from the cache. A {@link SecretCacheEvictionCause}. */
    CAUSE("cause");

    private final String key;

    SecretCacheKeyNames(final String key) {
      this.key = key;
    }

    @Override
    public String asString() {
      return key;
    }
  }

  /**
   * The value domain of the {@link SecretCacheKeyNames#RESULT} tag on {@link #CACHE_RESULT}.
   *
   * <p>Every value counts one lookup, so the two can be summed and divided by one another — which
   * is the whole point, since the hit rate is derived rather than published.
   */
  public enum SecretCacheResult {
    /** The cache held a value for the name */
    HIT,
    /** The cache held no value for the name, so the caller had to reach the store or give up */
    MISS
  }

  /**
   * The value domain of the {@link SecretCacheKeyNames#CAUSE} tag on {@link #CACHE_EVICTIONS}.
   *
   * <p>Mirrors the subset of Caffeine's {@link RemovalCause} that removes an entry, rather than
   * exposing that type as a tag value: a library constant renamed upstream would otherwise silently
   * rename a metric tag value. {@link RemovalCause#REPLACED} has no counterpart here on purpose —
   * see {@link #from(RemovalCause)}.
   *
   * <p>Every value counts one entry leaving one cache, so all four share a unit and can be summed
   * across the tag.
   */
  public enum SecretCacheEvictionCause {
    /** The cache was full, so it dropped an entry to make room for another */
    SIZE,
    /** The entry's TTL elapsed */
    EXPIRED,
    /**
     * Something removed the entry by name. In practice this is {@link CachingSecretStore}
     * invalidating a name its store answered permanently — deleted, access denied, or not a valid
     * reference for that store.
     */
    EXPLICIT,
    /**
     * The entry's key or value was garbage collected. Unreachable as this cache is built today (it
     * uses neither weak keys nor soft values), and carried only so that enabling either later
     * cannot make evictions vanish from the counter without anyone noticing.
     */
    COLLECTED;

    /**
     * Maps Caffeine's removal cause onto the tag value.
     *
     * <p>Exhaustive and without a {@code default} branch on purpose, so a constant added to {@link
     * RemovalCause} upstream is a compile error here rather than an eviction silently bucketed
     * under an existing tag value.
     *
     * @throws IllegalArgumentException for {@link RemovalCause#REPLACED}, which is not a removal at
     *     all — the entry stays and only its value changes, which {@link CachingSecretStore} does
     *     on every re-resolve. Caffeine never reports it as an eviction, so reaching this is a bug
     *     rather than a state to fold into some other cause.
     */
    public static SecretCacheEvictionCause from(final RemovalCause cause) {
      return switch (cause) {
        case SIZE -> SIZE;
        case EXPIRED -> EXPIRED;
        case EXPLICIT -> EXPLICIT;
        case COLLECTED -> COLLECTED;
        case REPLACED ->
            throw new IllegalArgumentException(
                "Expected a cause that removes a cache entry, but got REPLACED, which only "
                    + "overwrites the value of a name that stays cached");
      };
    }
  }
}
