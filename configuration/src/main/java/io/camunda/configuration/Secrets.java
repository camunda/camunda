/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * This section allows configuring named secret stores.
 *
 * <p>Canonical unified configuration properties are under {@code camunda.secrets.*}, including:
 *
 * <ul>
 *   <li>{@code camunda.secrets.cache.ttl}
 *   <li>{@code camunda.secrets.cache.max-size}
 *   <li>{@code camunda.secrets.max-concurrency}
 *   <li>{@code camunda.secrets.stores.file.<id>.path}
 *   <li>{@code camunda.secrets.stores.aws.<id>.region}
 *   <li>{@code camunda.secrets.stores.aws.<id>.path-prefix}
 *   <li>{@code camunda.secrets.stores.aws.<id>.batch-enabled}
 *   <li>{@code camunda.secrets.stores.aws.<id>.batch-size}
 *   <li>{@code camunda.secrets.stores.aws.<id>.container-secret-id}
 *   <li>{@code camunda.secrets.stores.gcp.<id>.project-id}
 *   <li>{@code camunda.secrets.stores.gcp.<id>.path-prefix}
 *   <li>{@code camunda.secrets.stores.gcp.<id>.endpoint}
 *   <li>{@code camunda.secrets.stores.gcp.<id>.container-secret-id}
 * </ul>
 *
 * <p>Exactly one store per physical tenant is supported, and its {@code <id>} must be {@code
 * default} — that is the store id a {@code camunda.secrets.<name>} reference addresses. A store
 * under any other id is rejected at startup, since nothing could ever read it.
 *
 * <p>Secrets configuration is overridable per physical tenant via {@code
 * camunda.physical-tenants.<id>.secrets.*}.
 */
@NullMarked
public class Secrets {

  /**
   * Default for {@link #maxConcurrency}, restated here as a literal for the same reason {@link
   * Cache}'s defaults are: this module deliberately does not depend on {@code secret-store-api}.
   */
  private static final int DEFAULT_MAX_CONCURRENCY = 8;

  @NestedConfigurationProperty private Stores stores = new Stores();
  @NestedConfigurationProperty private Cache cache = new Cache();

  /**
   * How many names a store that pays one backend call per name ({@code resolvesOneByOne()}) may
   * resolve concurrently, on a thread pool shared by every store the registry wraps. {@code 1}
   * resolves exactly as before this setting existed: one call at a time, on the calling thread. A
   * store that already covers several names per call (a batched or container-style store, or one
   * backed by local disk) is unaffected either way.
   *
   * <p>Defaults to {@code 8}.
   */
  private Integer maxConcurrency = DEFAULT_MAX_CONCURRENCY;

  public Stores getStores() {
    return stores;
  }

  public void setStores(final Stores stores) {
    this.stores = stores;
  }

  /**
   * @throws IllegalArgumentException if the cache ttl is below one minute or is not a whole number
   *     of minutes, or if the cache max-size is below 1
   */
  public Cache getCache() {
    cache.validate();
    return cache;
  }

  public void setCache(final Cache cache) {
    this.cache = cache;
  }

  /**
   * @throws IllegalArgumentException if max-concurrency is below 1
   */
  public int getMaxConcurrency() {
    if (maxConcurrency < 1) {
      throw new IllegalArgumentException(
          "camunda.secrets.max-concurrency must be at least 1, but was " + maxConcurrency);
    }
    return maxConcurrency;
  }

  /**
   * @param maxConcurrency the configured value, or {@code null}, bound to the same outcome {@code
   *     ttl}/{@code max-size} already have, rather than the two disagreeing on what an empty value
   *     means (routine for an env-var-driven deployment)
   */
  public void setMaxConcurrency(final @Nullable Integer maxConcurrency) {
    this.maxConcurrency = maxConcurrency == null ? DEFAULT_MAX_CONCURRENCY : maxConcurrency;
  }

  public static class Stores {

    private Map<String, FileStore> file = new LinkedHashMap<>();
    private Map<String, AwsSecretsManagerStore> aws = new LinkedHashMap<>();
    private Map<String, GcpSecretManagerStore> gcp = new LinkedHashMap<>();

    public Map<String, FileStore> getFile() {
      return file;
    }

    public void setFile(final Map<String, FileStore> file) {
      this.file = file;
    }

    /**
     * @throws IllegalArgumentException if any store's batch-size is outside the 1..20 range AWS
     *     accepts, or if a store sets both batch-enabled and container-secret-id (the two are
     *     contradictory: batching fetches multiple distinct secrets in one call, while a container
     *     secret id means only one secret is ever fetched)
     */
    public Map<String, AwsSecretsManagerStore> getAws() {
      aws.forEach((id, store) -> store.validate(id));
      return aws;
    }

    public void setAws(final Map<String, AwsSecretsManagerStore> aws) {
      this.aws = aws;
    }

    /**
     * @throws IllegalArgumentException if any store sets a blank project-id/container-secret-id, a
     *     path-prefix or container-secret-id with characters outside {@code [a-zA-Z0-9_-]}, or an
     *     effective container secret id longer than 255 characters (GCP secret-id rules)
     */
    public Map<String, GcpSecretManagerStore> getGcp() {
      gcp.forEach((id, store) -> store.validate(id));
      return gcp;
    }

    public void setGcp(final Map<String, GcpSecretManagerStore> gcp) {
      this.gcp = gcp;
    }
  }

  /**
   * Cache settings applied to every configured store's cache. One cache is created per store, so
   * {@link #maxSize} bounds each store's cache on its own rather than being a budget shared across
   * stores.
   *
   * <p>The defaults are restated here as literals rather than read from the cache implementation,
   * because this module deliberately does not depend on the {@code secret-store-api} module — the
   * same reason {@code EngineSecrets} and {@code ProcessCache} restate theirs. {@code
   * SecretStoreConfigurationTest} pins the two sets of defaults against each other, since {@code
   * dist} is the only module that sees both.
   */
  public static class Cache {

    /** Smallest ttl accepted, and the granularity every ttl must be a whole multiple of. */
    private static final Duration MIN_TTL = Duration.ofMinutes(1);

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(20);

    private static final int DEFAULT_MAX_SIZE = 1000;

    /**
     * How long a resolved secret is served from a store's cache before it is fetched from the store
     * again, so a secret rotated in the store is picked up without a restart. Minute granularity
     * only: a value below {@code 1m}, or one that is not a whole number of minutes, is rejected at
     * startup — the value is a staleness budget measured in minutes, and expressing it in seconds
     * invites a cache so short that it turns cache-only lookups into store round trips.
     *
     * <p>Defaults to {@code 20m}.
     */
    private Duration ttl = DEFAULT_TTL;

    /**
     * Maximum number of secrets each store's cache holds; once it is reached, caching another
     * secret evicts one already held. Which one is the cache implementation's choice, so no secret
     * is guaranteed to stay resident — an evicted secret is re-read from the store on its next
     * resolution. Must be at least 1.
     *
     * <p>This is a per-cache limit, not a budget shared across stores, so the worst-case memory
     * footprint is the number of configured stores times this value — each cached entry holding one
     * secret's name and its value.
     *
     * <p>Defaults to {@code 1000}.
     */
    // Boxed rather than an int so an explicitly empty value survives binding (see setMaxSize), and
    // never null once bound, which is what lets getMaxSize() hand an int to the cache. Kept out of
    // the javadoc above because that text is harvested into the operator-facing configuration
    // reference, where the field's Java type is noise.
    private Integer maxSize = DEFAULT_MAX_SIZE;

    public Duration getTtl() {
      return ttl;
    }

    /**
     * Coerces {@code null} back to the default, because {@link #getTtl()} is handed straight to the
     * cache, which cannot take one. An explicitly empty property value ({@code
     * camunda.secrets.cache.ttl=}) — routine when the value comes from an unset environment
     * variable — converts to {@code null}, which Spring's binder reads as "not bound", so in that
     * path the field default simply stands and this setter is never called. The coercion covers a
     * caller that sets the field directly, keeping the {@code NullMarked} contract of {@link
     * #getTtl()} true whatever is passed.
     */
    public void setTtl(final @Nullable Duration ttl) {
      this.ttl = ttl == null ? DEFAULT_TTL : ttl;
    }

    public int getMaxSize() {
      return maxSize;
    }

    /**
     * Takes a boxed {@link Integer} for the sake of the empty-value path described on {@link
     * #setTtl}: an empty {@code camunda.secrets.cache.max-size=} converts to {@code null}, and
     * assigning that to an {@code int} parameter fails conversion outright ("A null value cannot be
     * assigned to a primitive type"), turning an unset environment variable into a startup crash
     * naming no remedy. Boxed, the binder treats it as "not bound" and the default stands — the
     * same outcome {@code ttl} already has, rather than the two disagreeing on what an empty value
     * means.
     */
    public void setMaxSize(final @Nullable Integer maxSize) {
      this.maxSize = maxSize == null ? DEFAULT_MAX_SIZE : maxSize;
    }

    /**
     * Enforces the bounds the cache is built with, with property-path-aware messages in the style
     * of the store validators above. Reported against the canonical {@code camunda.secrets.cache.*}
     * path even when the value came from a {@code camunda.physical-tenants.<id>.secrets.cache.*}
     * override — this config object carries no tenant identity, exactly as the store validators do
     * not. {@code SecretStoreConfiguration} names the tenant when it rethrows, since it is the
     * caller that knows which one it is reading for.
     *
     * <p>Runs from the validating {@link Secrets#getCache()}, which {@code
     * SecretStoreConfiguration} reads once per physical tenant before building any store — that
     * unconditional read is what makes this a startup check rather than a first-use one.
     *
     * @throws IllegalArgumentException if ttl is below one minute or is not a whole number of
     *     minutes, or if max-size is below 1
     */
    void validate() {
      if (ttl.compareTo(MIN_TTL) < 0) {
        throw new IllegalArgumentException(
            "camunda.secrets.cache.ttl must be at least 1 minute, but was " + ttl);
      }
      if (!ttl.equals(Duration.ofMinutes(ttl.toMinutes()))) {
        throw new IllegalArgumentException(
            "camunda.secrets.cache.ttl must be a whole number of minutes, but was " + ttl);
      }
      if (maxSize < 1) {
        throw new IllegalArgumentException(
            "camunda.secrets.cache.max-size must be at least 1, but was " + maxSize);
      }
    }
  }

  public static class FileStore {

    /**
     * Path to the directory backing this file-based secret store. Defaults to {@code
     * /etc/camunda/secrets}.
     */
    private String path = "/etc/camunda/secrets";

    /**
     * BENCHMARK ONLY, never set in production. When set, every {@code resolve} call sleeps this
     * many milliseconds per requested name before reading the file, simulating a real cloud secret
     * manager's round trip on top of local disk. Absent (the default), resolution is unaffected.
     */
    private @Nullable Long benchmarkSimulatedLatencyMs;

    public String getPath() {
      return path;
    }

    public void setPath(final String path) {
      this.path = path;
    }

    public @Nullable Long getBenchmarkSimulatedLatencyMs() {
      return benchmarkSimulatedLatencyMs;
    }

    public void setBenchmarkSimulatedLatencyMs(final @Nullable Long benchmarkSimulatedLatencyMs) {
      this.benchmarkSimulatedLatencyMs = benchmarkSimulatedLatencyMs;
    }
  }

  /**
   * Configuration for an AWS Secrets Manager store. Authentication is always identity-based (AWS
   * SDK default credentials provider chain): no static credentials are accepted here by design.
   */
  public static class AwsSecretsManagerStore {

    /**
     * AWS region for this store. Optional: when omitted the SDK resolves it from the environment
     * ({@code AWS_REGION}) or instance metadata.
     */
    private @Nullable String region;

    /**
     * Optional prefix prepended, with no separator inserted, to every reference name to form the
     * AWS secret id (e.g. {@code camunda/} plus reference {@code db-password} resolves to secret id
     * {@code camunda/db-password}; include the trailing separator here if one is wanted). When
     * omitted, references map to bare secret names. Applies to {@link #containerSecretId} as well,
     * since that is itself resolved as an AWS secret id.
     */
    private @Nullable String pathPrefix;

    /**
     * Opt-in: resolve secrets via AWS's {@code BatchGetSecretValue} (fewer round-trips) instead of
     * one {@code GetSecretValue} call per reference. Off by default because it requires the {@code
     * secretsmanager:BatchGetSecretValue} IAM action in addition to {@code GetSecretValue}, which
     * not every deployment's IAM policy grants. Mutually exclusive with {@link #containerSecretId}.
     */
    private boolean batchEnabled = false;

    /**
     * Maximum number of secret ids per {@code BatchGetSecretValue} call when {@link #batchEnabled}
     * is set. Only meaningful when batching is enabled. Must be between 1 and 20 (AWS's cap).
     */
    private int batchSize = 20;

    /**
     * Opt-in: instead of one AWS secret per reference, treat every reference as a JSON key inside
     * this one named secret (e.g. {@code app-config}). Mutually exclusive with {@link
     * #batchEnabled}, since only this single secret is ever fetched.
     *
     * <p>The named secret's string value must be a flat JSON object mapping each reference name to
     * a JSON string value (e.g. {@code {"db-password": "s3cr3t"}}); nested objects/arrays are not
     * supported, and a non-object value makes the store unusable.
     */
    private @Nullable String containerSecretId;

    public @Nullable String getRegion() {
      return region;
    }

    public void setRegion(final @Nullable String region) {
      this.region = region;
    }

    public @Nullable String getPathPrefix() {
      return pathPrefix;
    }

    public void setPathPrefix(final @Nullable String pathPrefix) {
      this.pathPrefix = pathPrefix;
    }

    public boolean isBatchEnabled() {
      return batchEnabled;
    }

    public void setBatchEnabled(final boolean batchEnabled) {
      this.batchEnabled = batchEnabled;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(final int batchSize) {
      this.batchSize = batchSize;
    }

    public @Nullable String getContainerSecretId() {
      return containerSecretId;
    }

    public void setContainerSecretId(final @Nullable String containerSecretId) {
      this.containerSecretId = containerSecretId;
    }

    /**
     * Mirrors the invariants of {@code io.camunda.secretstore.aws.AwsSecretsManagerStoreConfig}'s
     * canonical constructor in the {@code secret-store-aws} module (not depended on from here, to
     * keep this module free of the AWS SDK) — with property-path-aware messages instead of that
     * record's generic ones. Keep both in sync: a new rule added to one but not the other leaves a
     * gap for whichever path skips this one (Spring config binding here vs. any other caller of
     * that record's constructor directly).
     *
     * @throws IllegalArgumentException if batch-size is outside the 1..20 range AWS accepts, if
     *     container-secret-id is blank, or if both batch-enabled and container-secret-id are set
     *     (the two are contradictory: batching fetches multiple distinct secrets in one call, while
     *     a container secret id means only one secret is ever fetched)
     */
    void validate(final String storeId) {
      if (batchSize < 1 || batchSize > 20) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.aws."
                + storeId
                + ".batch-size must be between 1 and 20, but was "
                + batchSize);
      }
      if (containerSecretId != null && containerSecretId.isBlank()) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.aws." + storeId + ".container-secret-id must not be blank");
      }
      if (batchEnabled && containerSecretId != null) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.aws."
                + storeId
                + ".batch-enabled and .container-secret-id are mutually exclusive, but both were "
                + "configured");
      }
    }
  }

  /**
   * Configuration for a GCP Secret Manager store. Authentication uses the GCP Application Default
   * Credentials (ADC) chain (e.g. a service-account key file referenced by {@code
   * GOOGLE_APPLICATION_CREDENTIALS}, gcloud user credentials, or the attached service account via
   * the compute metadata server). This config does not accept explicit credentials fields.
   */
  public static class GcpSecretManagerStore {

    /**
     * GCP secret ids allow only these characters, and are capped at 255 characters. Both {@link
     * #pathPrefix} (which is prepended to form ids) and {@link #containerSecretId} (which is itself
     * an id) must respect this, otherwise every lookup would target an id GCP rejects.
     */
    private static final Pattern SECRET_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]*");

    private static final int MAX_SECRET_ID_LENGTH = 255;

    /**
     * GCP project id owning the secrets. Optional: when omitted the client resolves it from the
     * environment ({@code GOOGLE_CLOUD_PROJECT}) or the compute metadata server, via the
     * Application Default Credentials chain — mirroring how {@link AwsSecretsManagerStore#region}
     * is resolved for AWS. If set it must not be blank.
     */
    private @Nullable String projectId;

    /**
     * Optional prefix prepended, with no separator inserted, to every reference name to form the
     * GCP secret id (e.g. {@code camunda-} plus reference {@code db-password} resolves to secret id
     * {@code camunda-db-password}; include the trailing separator here if one is wanted). When
     * omitted, references map to bare secret ids. Applies to {@link #containerSecretId} as well,
     * since that is itself resolved as a GCP secret id.
     *
     * <p>Note GCP secret ids only allow {@code [a-zA-Z0-9_-]} and are capped at 255 characters, so
     * an AWS-style {@code camunda/} prefix (with a slash) would produce invalid ids; use e.g.
     * {@code camunda-} instead.
     */
    private @Nullable String pathPrefix;

    /**
     * Optional Secret Manager endpoint override (e.g. a regional endpoint such as {@code
     * secretmanager.europe-west1.rep.googleapis.com:443}, or a Private Service Connect endpoint).
     * Unlike the AWS SDK, the GCP client libraries do not honour an endpoint override via
     * environment variable, so it is exposed here as first-class config; it also doubles as the
     * hook for pointing at an emulator in tests. When omitted, the default global endpoint is used.
     */
    private @Nullable String endpoint;

    /**
     * Opt-in: instead of one GCP secret per reference, treat every reference as a JSON key inside
     * this one named secret (e.g. {@code app-config}). If set it must not be blank.
     *
     * <p>The named secret's value must be a flat JSON object mapping each reference name to a JSON
     * string value (e.g. {@code {"db-password": "s3cr3t"}}); nested objects/arrays are not
     * supported, and a non-object value makes the store unusable.
     */
    private @Nullable String containerSecretId;

    public @Nullable String getProjectId() {
      return projectId;
    }

    public void setProjectId(final @Nullable String projectId) {
      this.projectId = projectId;
    }

    public @Nullable String getPathPrefix() {
      return pathPrefix;
    }

    public void setPathPrefix(final @Nullable String pathPrefix) {
      this.pathPrefix = pathPrefix;
    }

    public @Nullable String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(final @Nullable String endpoint) {
      this.endpoint = endpoint;
    }

    public @Nullable String getContainerSecretId() {
      return containerSecretId;
    }

    public void setContainerSecretId(final @Nullable String containerSecretId) {
      this.containerSecretId = containerSecretId;
    }

    /**
     * Only present-value checks live here (blank, GCP-secret-id charset, and length), so the
     * validating {@link Stores#getGcp()} stays safe to call from the physical-tenant overlay: a
     * transiently-inherited-then-missing field is {@code null}, which is skipped. Required-field
     * presence and connectivity are enforced by the GCP store implementation instead (mirroring how
     * AWS defers those to {@code AwsSecretsManagerStoreConfig} plus its startup connectivity
     * probe), not by this config layer.
     *
     * <p>{@code path-prefix} and {@code container-secret-id} are validated against the GCP
     * secret-id rules ({@code [a-zA-Z0-9_-]}, max 255 chars) because both feed into secret ids: a
     * bad prefix would corrupt every id, and the container-secret-id is itself an id. The prefix's
     * length is checked together with the container id (the only id fully known at config time);
     * per-reference ids in flat mode are formed at runtime and validated there.
     *
     * @throws IllegalArgumentException if project-id, container-secret-id, or endpoint is set but
     *     blank, if path-prefix or container-secret-id contains characters outside {@code
     *     [a-zA-Z0-9_-]}, or if the effective container secret id (path-prefix +
     *     container-secret-id) exceeds 255 characters
     */
    void validate(final String storeId) {
      if (projectId != null && projectId.isBlank()) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.gcp." + storeId + ".project-id must not be blank");
      }
      if (containerSecretId != null && containerSecretId.isBlank()) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.gcp." + storeId + ".container-secret-id must not be blank");
      }
      if (endpoint != null && endpoint.isBlank()) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.gcp." + storeId + ".endpoint must not be blank");
      }
      if (pathPrefix != null && !SECRET_ID_PATTERN.matcher(pathPrefix).matches()) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.gcp."
                + storeId
                + ".path-prefix must contain only [a-zA-Z0-9_-] to form valid GCP secret ids, but "
                + "was '"
                + pathPrefix
                + "'");
      }
      if (containerSecretId != null && !SECRET_ID_PATTERN.matcher(containerSecretId).matches()) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.gcp."
                + storeId
                + ".container-secret-id must contain only [a-zA-Z0-9_-] to be a valid GCP secret "
                + "id, but was '"
                + containerSecretId
                + "'");
      }
      if (containerSecretId != null) {
        final int fullLength =
            (pathPrefix == null ? 0 : pathPrefix.length()) + containerSecretId.length();
        if (fullLength > MAX_SECRET_ID_LENGTH) {
          throw new IllegalArgumentException(
              "camunda.secrets.stores.gcp."
                  + storeId
                  + " effective container secret id (path-prefix + container-secret-id) must be at "
                  + "most "
                  + MAX_SECRET_ID_LENGTH
                  + " characters, but was "
                  + fullLength);
        }
      }
    }
  }
}
