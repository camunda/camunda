/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.net.URI;
import java.net.URISyntaxException;
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
 *   <li>{@code camunda.secrets.stores.env.<id>.prefix}
 *   <li>{@code camunda.secrets.stores.azure.<id>.vault-url}
 *   <li>{@code camunda.secrets.stores.azure.<id>.path-prefix}
 *   <li>{@code camunda.secrets.stores.azure.<id>.container-secret-id}
 *   <li>{@code camunda.secrets.stores.azure.<id>.managed-identity-client-id}
 *   <li>{@code camunda.secrets.stores.azure.<id>.authority-host}
 *   <li>{@code camunda.secrets.stores.azure.<id>.max-retries}
 *   <li>{@code camunda.secrets.stores.azure.<id>.call-timeout}
 *   <li>{@code camunda.secrets.stores.azure.<id>.attempt-timeout}
 * </ul>
 *
 * <p>Exactly one store per physical tenant is supported, and its {@code <id>} must be {@code
 * default} — that is the store id a {@code camunda.secrets.<name>} reference addresses. A store
 * under any other id is rejected at startup, since nothing could ever read it.
 *
 * <p>Secrets configuration is overridable per physical tenant via {@code
 * camunda.physical-tenants.<id>.secrets.*}. Every physical tenant inherits the stores configured
 * under {@code camunda.secrets.stores.*}, so a tenant that needs a different store type than the
 * root must not leave a root store configured: the inherited store would count towards its one
 * store.
 */
@NullMarked
public class Secrets {

  /**
   * Floor for {@link #DEFAULT_MAX_CONCURRENCY}, so a small container does not end up with fewer
   * permits than it has callers competing for them: below the number of concurrently resolving
   * callers, the shared semaphore is a throttle rather than a speed-up, and a two-core node would
   * otherwise resolve more slowly than it did before the semaphore existed.
   */
  private static final int MIN_DEFAULT_MAX_CONCURRENCY = 8;

  /**
   * Default upper bound on one call to a cloud secret store, retries included, and on a single
   * attempt within it. Shared by the AWS, GCP and Azure stores so all are bounded the same way;
   * mirrored by {@code AwsSecretsManagerStoreConfig#DEFAULT_CALL_TIMEOUT}, {@code
   * GcpSecretManagerStoreConfig#DEFAULT_CALL_TIMEOUT} and {@code
   * AzureKeyVaultStoreConfig#DEFAULT_CALL_TIMEOUT} in the store modules, which this module cannot
   * depend on. Keep them in sync.
   */
  private static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(5);

  private static final Duration DEFAULT_ATTEMPT_TIMEOUT = Duration.ofSeconds(2);

  /**
   * Twice the core count, not once: the permits bound blocking backend round trips rather than CPU
   * work, and staying clear of the virtual-thread carrier pool (sized to the core count) leaves
   * room for a store whose client pins its carrier. It also keeps the default above
   * partitions-per-node, which is what the callers competing for these permits scale with.
   */
  private static final int DEFAULT_MAX_CONCURRENCY_PER_CORE = 2;

  /**
   * Default for {@link #maxConcurrency}. Derived from the host rather than fixed, since the callers
   * sharing these permits (one per partition on this node, plus the REST resolution path) scale
   * with the node's size, and a fixed number too far below them turns the shared semaphore into a
   * throttle.
   *
   * <p>Computed here rather than read from {@code secret-store-api} for the same reason {@link
   * Cache}'s defaults are restated as literals: this module deliberately does not depend on that
   * module.
   */
  private static final int DEFAULT_MAX_CONCURRENCY =
      Math.max(
          MIN_DEFAULT_MAX_CONCURRENCY,
          Runtime.getRuntime().availableProcessors() * DEFAULT_MAX_CONCURRENCY_PER_CORE);

  @NestedConfigurationProperty private Stores stores = new Stores();
  @NestedConfigurationProperty private Cache cache = new Cache();

  /**
   * How many sequential backend calls a store whose cost scales with call count ({@code
   * namesPerCall()} less than the request size) may issue at once, bounded by a semaphore shared by
   * every such store the registry wraps. {@code 1} resolves exactly as before this setting existed:
   * one call at a time, on the calling thread. A store that already covers the whole request in one
   * call (a container-style store, or one backed by local disk) is unaffected either way; a batched
   * store (e.g. AWS's {@code BatchGetSecretValue} mode) is included once its request needs more
   * than one batch.
   *
   * <p>The permits are shared per node and physical tenant, so they bound how many backend calls
   * this node has in flight for such stores at once. A request small enough to resolve in a single
   * call (at most {@code namesPerCall()} names) takes no permit, so actual concurrency can exceed
   * this value by one such call per concurrent caller. The bound is on call concurrency, not
   * request rate, so it only approximates a provider quota measured in calls per second rather than
   * guaranteeing it sees at most this value times the node count.
   *
   * <p>Raising it past what one request can use changes nothing, since a request is split into
   * {@code ceil(names / namesPerCall())} chunks and cannot use more permits than it has chunks. The
   * resolve endpoint caps its request size at 20 names via the {@code maxItems} on its {@code
   * references} array; background resolution defaults to the same 20 via {@code
   * camunda.processing.engine.secrets.batch-resolution-limit}, but that limit is a configurable
   * default, not a hard cap. For a one-by-one store the endpoint's cap puts the chunk ceiling at
   * 20; for a batched store it is {@code ceil(20 / batchSize)}.
   *
   * <p>Defaults to twice the available processor count, and never below {@code 8}.
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
    private Map<String, EnvStore> env = new LinkedHashMap<>();

    /**
     * Azure Key Vault stores, keyed by store id; the only supported id is {@code default}. Sign-in
     * is meant to be identity-based (managed identity, workload identity, or a service principal
     * from the {@code AZURE_*} environment variables), but signing in and reading the vault are not
     * implemented yet: until then every reference resolves as not found. Empty by default, so no
     * Azure store is configured.
     */
    private Map<String, AzureKeyVaultStore> azure = new LinkedHashMap<>();

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

    public Map<String, EnvStore> getEnv() {
      return env;
    }

    public void setEnv(final Map<String, EnvStore> env) {
      this.env = env;
    }

    /** Not validated here, see {@link AzureKeyVaultStore#validate(String)}. */
    public Map<String, AzureKeyVaultStore> getAzure() {
      return azure;
    }

    public void setAzure(final Map<String, AzureKeyVaultStore> azure) {
      this.azure = azure;
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

    public String getPath() {
      return path;
    }

    public void setPath(final String path) {
      this.path = path;
    }
  }

  /**
   * Configuration for a store that reads secrets from the node's environment variables. The
   * environment is read once at startup, so changing a secret requires a restart.
   */
  public static class EnvStore {

    /**
     * Prefix every secret variable carries: secret {@code name} is read from the variable {@code
     * <prefix><name>}. Required, and must start with a letter, contain only letters, digits and
     * underscores, and end with an underscore, such as {@code APP_SECRET_}. Startup is refused when
     * it is missing or malformed, or when it starts, ignoring case, with a prefix reserved for the
     * platform's own settings and credentials, such as {@code CAMUNDA_}, {@code ZEEBE_}, {@code
     * SPRING_} or {@code AWS_}.
     */
    private String prefix = "";

    public String getPrefix() {
      return prefix;
    }

    /**
     * Coerces {@code null} to an empty prefix, so {@link #getPrefix()} keeps its {@code NullMarked}
     * contract whatever a caller passes.
     */
    public void setPrefix(final @Nullable String prefix) {
      this.prefix = prefix == null ? "" : prefix;
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

    /**
     * Upper bound on one call to AWS Secrets Manager, retries included. The AWS SDK sets no such
     * bound by default, which lets an unresponsive endpoint hold the caller for minutes. The
     * background secret resolution calls the store from an IO-bound actor thread shared with every
     * partition's exporter, so an unbounded call there stalls exporting broker-wide
     * (camunda/camunda#62869). Must be positive.
     */
    private Duration callTimeout = DEFAULT_CALL_TIMEOUT;

    /**
     * Upper bound on a single HTTP attempt within a call, so one stalled socket does not consume
     * the whole {@link #callTimeout} budget. Must be positive and not longer than {@link
     * #callTimeout}, which would make it unreachable.
     */
    private Duration attemptTimeout = DEFAULT_ATTEMPT_TIMEOUT;

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

    public Duration getCallTimeout() {
      return callTimeout;
    }

    /**
     * @param callTimeout the configured value, or {@code null}, bound to the default the same way
     *     an unset property is, so an explicitly empty value does not leave the store unbounded
     */
    public void setCallTimeout(final @Nullable Duration callTimeout) {
      this.callTimeout = callTimeout == null ? DEFAULT_CALL_TIMEOUT : callTimeout;
    }

    public Duration getAttemptTimeout() {
      return attemptTimeout;
    }

    /**
     * @param attemptTimeout the configured value, or {@code null}, bound to the same outcome {@code
     *     callTimeout} already has for an empty value
     */
    public void setAttemptTimeout(final @Nullable Duration attemptTimeout) {
      this.attemptTimeout = attemptTimeout == null ? DEFAULT_ATTEMPT_TIMEOUT : attemptTimeout;
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
      if (!callTimeout.isPositive()) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.aws."
                + storeId
                + ".call-timeout must be positive, but was "
                + callTimeout);
      }
      if (!attemptTimeout.isPositive()) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.aws."
                + storeId
                + ".attempt-timeout must be positive, but was "
                + attemptTimeout);
      }
      if (attemptTimeout.compareTo(callTimeout) > 0) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.aws."
                + storeId
                + ".attempt-timeout must not be longer than .call-timeout ("
                + callTimeout
                + "), but was "
                + attemptTimeout);
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

    /**
     * Upper bound on one call to GCP Secret Manager, retries included. gax's defaults let an
     * unresponsive endpoint hold the caller far longer than that. The background secret resolution
     * calls the store from an IO-bound actor thread shared with every partition's exporter, so an
     * unbounded call there stalls exporting broker-wide (camunda/camunda#62869). Must be positive.
     */
    private Duration callTimeout = DEFAULT_CALL_TIMEOUT;

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

    public Duration getCallTimeout() {
      return callTimeout;
    }

    /**
     * @param callTimeout the configured value, or {@code null}, bound to the default the same way
     *     an unset property is, so an explicitly empty value does not leave the store unbounded
     */
    public void setCallTimeout(final @Nullable Duration callTimeout) {
      this.callTimeout = callTimeout == null ? DEFAULT_CALL_TIMEOUT : callTimeout;
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
      if (!callTimeout.isPositive()) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.gcp."
                + storeId
                + ".call-timeout must be positive, but was "
                + callTimeout);
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

  /**
   * Configuration for an Azure Key Vault store. Sign-in is meant to be identity-based only (managed
   * identity, workload identity, or a service principal from the standard {@code AZURE_*}
   * environment variables), so no static credentials are accepted here by design.
   *
   * <p>Signing in and reading the vault are not implemented yet (camunda/camunda#67225,
   * camunda/camunda#67226): until then the store resolves every reference as not found.
   */
  public static class AzureKeyVaultStore {

    /**
     * Default number of retries for a throttled or transient request; mirrored by {@code
     * AzureKeyVaultStoreConfig#DEFAULT_MAX_RETRIES} in the store module, which this module cannot
     * depend on. Keep them in sync.
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /** Characters Key Vault accepts in a secret name. */
    private static final Pattern SECRET_NAME_PATTERN = Pattern.compile("[0-9A-Za-z-]*");

    /** Longest secret name Key Vault accepts. */
    private static final int MAX_SECRET_NAME_LENGTH = 127;

    /**
     * URL of the vault holding the secrets, such as {@code https://my-vault.vault.azure.net}.
     * Required, with no default. Must be an absolute {@code https://} URL with only a host and
     * optional port, as Key Vault requires HTTPS; any host name is accepted, so private endpoints
     * and sovereign clouds work.
     */
    private @Nullable String vaultUrl;

    /**
     * Optional prefix prepended, with no separator inserted, to every reference name to form the
     * Key Vault secret name (e.g. {@code camunda-} plus reference {@code db-password} reads secret
     * {@code camunda-db-password}). When omitted, references map to bare secret names. Applies to
     * {@link #containerSecretId} as well. Key Vault secret names allow only {@code [0-9A-Za-z-]},
     * so use e.g. {@code camunda-} rather than {@code camunda/}; any other character is rejected at
     * startup. At most 126 characters, leaving room for at least one character of the name. No
     * prefix by default.
     */
    private @Nullable String pathPrefix;

    /**
     * Opt-in: instead of one Key Vault secret per reference, treat every reference as a JSON key
     * inside this one named secret (e.g. {@code app-config}). The secret's value must be a flat
     * JSON object mapping each reference name to a string value (e.g. {@code {"db-password":
     * "s3cr3t"}}). Not set by default. With {@link #pathPrefix} prepended, it must be a Key Vault
     * secret name: 1 to 127 characters of {@code [0-9A-Za-z-]}.
     */
    private @Nullable String containerSecretId;

    /**
     * Client id of the user-assigned managed identity to sign in with, so each physical tenant can
     * use its own identity. Must not be blank when set. Not set by default, so the system-assigned
     * identity or whichever identity the environment provides is used.
     */
    private @Nullable String managedIdentityClientId;

    /**
     * Microsoft Entra authority host to sign in against, for sovereign clouds (e.g. {@code
     * https://login.microsoftonline.us}). Must be an absolute {@code https://} URL with only a host
     * and optional port when set. Not set by default, so the public cloud is used.
     */
    private @Nullable String authorityHost;

    /**
     * How many times a throttled or transient request to the vault is retried. Must not be
     * negative. Defaults to {@code 3}.
     */
    private int maxRetries = DEFAULT_MAX_RETRIES;

    /**
     * Upper bound on one call to Key Vault, retries included. The background secret resolution
     * calls the store from an IO-bound actor thread shared with every partition's exporter, so an
     * unbounded call there stalls exporting broker-wide (camunda/camunda#62869). Must be positive.
     * Defaults to 5 seconds.
     */
    private Duration callTimeout = DEFAULT_CALL_TIMEOUT;

    /**
     * Upper bound on a single HTTP attempt within a call, so one stalled socket does not consume
     * the whole {@link #callTimeout} budget. Must be positive and not longer than {@link
     * #callTimeout}. Defaults to 2 seconds, or to {@link #callTimeout} when that is shorter. {@code
     * null} while unset, so the default can follow {@link #callTimeout}.
     */
    private @Nullable Duration attemptTimeout;

    public @Nullable String getVaultUrl() {
      return vaultUrl;
    }

    /** Surrounding whitespace, as quoting in YAML or an env var can leave, is stripped. */
    public void setVaultUrl(final @Nullable String vaultUrl) {
      this.vaultUrl = strip(vaultUrl);
    }

    public @Nullable String getPathPrefix() {
      return pathPrefix;
    }

    /** Surrounding whitespace is stripped, as for {@link #setVaultUrl}. */
    public void setPathPrefix(final @Nullable String pathPrefix) {
      this.pathPrefix = strip(pathPrefix);
    }

    public @Nullable String getContainerSecretId() {
      return containerSecretId;
    }

    /** Surrounding whitespace is stripped, as for {@link #setVaultUrl}. */
    public void setContainerSecretId(final @Nullable String containerSecretId) {
      this.containerSecretId = strip(containerSecretId);
    }

    public @Nullable String getManagedIdentityClientId() {
      return managedIdentityClientId;
    }

    /** Surrounding whitespace is stripped, as for {@link #setVaultUrl}. */
    public void setManagedIdentityClientId(final @Nullable String managedIdentityClientId) {
      this.managedIdentityClientId = strip(managedIdentityClientId);
    }

    public @Nullable String getAuthorityHost() {
      return authorityHost;
    }

    /** Surrounding whitespace is stripped, as for {@link #setVaultUrl}. */
    public void setAuthorityHost(final @Nullable String authorityHost) {
      this.authorityHost = strip(authorityHost);
    }

    public int getMaxRetries() {
      return maxRetries;
    }

    /**
     * @param maxRetries the configured value, or {@code null}, bound to the default the same way an
     *     unset property is, so an explicitly empty value does not fail binding
     */
    public void setMaxRetries(final @Nullable Integer maxRetries) {
      this.maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
    }

    public Duration getCallTimeout() {
      return callTimeout;
    }

    /**
     * @param callTimeout the configured value, or {@code null}, bound to the default the same way
     *     an unset property is, so an explicitly empty value does not leave the store unbounded
     */
    public void setCallTimeout(final @Nullable Duration callTimeout) {
      this.callTimeout = callTimeout == null ? DEFAULT_CALL_TIMEOUT : callTimeout;
    }

    /**
     * @return the configured value or, when unset, 2 seconds capped at {@link #getCallTimeout()},
     *     so lowering only the call timeout does not leave the default attempt timeout above it
     */
    public Duration getAttemptTimeout() {
      if (attemptTimeout != null) {
        return attemptTimeout;
      }
      return DEFAULT_ATTEMPT_TIMEOUT.compareTo(callTimeout) <= 0
          ? DEFAULT_ATTEMPT_TIMEOUT
          : callTimeout;
    }

    /**
     * @param attemptTimeout the configured value, or {@code null}, bound to the default the same
     *     way an unset property is
     */
    public void setAttemptTimeout(final @Nullable Duration attemptTimeout) {
      this.attemptTimeout = attemptTimeout;
    }

    /**
     * Rejects settings that are set but can never work. Called where the store is built rather than
     * from {@link Stores#getAzure()}, unlike the AWS and GCP stores: the physical-tenant overlay
     * reads that map while a tenant's overrides sit on a fresh instance whose other fields still
     * hold their defaults, so a cross-field check there would compare against values nobody set.
     * Required-field presence ({@code vault-url}) is checked by the caller.
     *
     * @param property the property prefix of this store as the operator configured it, e.g. {@code
     *     camunda.physical-tenants.pt1.secrets.stores.azure.default}, so an error names the
     *     property to fix
     * @throws IllegalArgumentException if the store sets a blank container-secret-id,
     *     managed-identity-client-id or authority-host, a vault-url or authority-host that is not
     *     an absolute https URL with only a host and optional port, a path-prefix or
     *     container-secret-id with characters outside {@code [0-9A-Za-z-]}, a path-prefix that
     *     leaves no room for a secret name, an effective container secret name longer than 127
     *     characters (Key Vault secret-name rules), a negative max-retries, or timeouts that are
     *     not positive or whose attempt bound exceeds the call bound
     */
    public void validate(final String property) {
      requireNotBlank(property, "container-secret-id", containerSecretId);
      requireNotBlank(property, "managed-identity-client-id", managedIdentityClientId);
      requireNotBlank(property, "authority-host", authorityHost);
      requireHttpsUrl(property, "vault-url", vaultUrl);
      requireHttpsUrl(property, "authority-host", authorityHost);
      requireSecretNameChars(property, "path-prefix", pathPrefix);
      requireSecretNameChars(property, "container-secret-id", containerSecretId);
      if (pathPrefix != null && pathPrefix.length() >= MAX_SECRET_NAME_LENGTH) {
        throw new IllegalArgumentException(
            property
                + ".path-prefix must be at most "
                + (MAX_SECRET_NAME_LENGTH - 1)
                + " characters so a secret name fits after it, but was "
                + pathPrefix.length());
      }
      if (containerSecretId != null) {
        final int fullLength =
            (pathPrefix == null ? 0 : pathPrefix.length()) + containerSecretId.length();
        if (fullLength > MAX_SECRET_NAME_LENGTH) {
          throw new IllegalArgumentException(
              property
                  + " effective container secret name (path-prefix + container-secret-id) must be"
                  + " at most "
                  + MAX_SECRET_NAME_LENGTH
                  + " characters, but was "
                  + fullLength);
        }
      }
      if (maxRetries < 0) {
        throw new IllegalArgumentException(
            property + ".max-retries must not be negative, but was " + maxRetries);
      }
      if (!callTimeout.isPositive()) {
        throw new IllegalArgumentException(
            property + ".call-timeout must be positive, but was " + callTimeout);
      }
      final var effectiveAttemptTimeout = getAttemptTimeout();
      if (!effectiveAttemptTimeout.isPositive()) {
        throw new IllegalArgumentException(
            property + ".attempt-timeout must be positive, but was " + effectiveAttemptTimeout);
      }
      if (effectiveAttemptTimeout.compareTo(callTimeout) > 0) {
        throw new IllegalArgumentException(
            property
                + ".attempt-timeout must not be longer than .call-timeout ("
                + callTimeout
                + "), but was "
                + effectiveAttemptTimeout);
      }
    }

    private static @Nullable String strip(final @Nullable String value) {
      return value == null ? null : value.strip();
    }

    private static void requireNotBlank(
        final String property, final String name, final @Nullable String value) {
      if (value != null && value.isBlank()) {
        throw new IllegalArgumentException(property + "." + name + " must not be blank");
      }
    }

    /** Blank values are left to the presence checks, so only a set value is checked here. */
    private static void requireHttpsUrl(
        final String property, final String name, final @Nullable String value) {
      if (value == null || value.isBlank()) {
        return;
      }
      final var message =
          property
              + "."
              + name
              + " must be an absolute https URL with only a host and optional port, as Azure Key"
              + " Vault requires HTTPS";
      final URI uri;
      try {
        uri = new URI(value);
      } catch (final URISyntaxException e) {
        // the cause is dropped: its message repeats the value, which may hold credentials
        throw new IllegalArgumentException(message);
      }
      if (uri.getHost() == null && uri.getRawAuthority() != null) {
        // java.net.URI leaves the host null for names it cannot parse, e.g. with an underscore
        throw new IllegalArgumentException(
            property
                + "."
                + name
                + " must have a host name of only letters, digits, hyphens and dots");
      }
      if (!isHttpsHostUrl(uri)) {
        throw new IllegalArgumentException(message);
      }
    }

    /**
     * Whether the URI is {@code https://host[:port][/]}: credentials, a path, a query or a fragment
     * never form a Key Vault or Entra ID endpoint. Mirrored by {@code AzureKeyVaultStoreConfig} in
     * the store module.
     */
    private static boolean isHttpsHostUrl(final URI uri) {
      final var path = uri.getRawPath();
      return "https".equalsIgnoreCase(uri.getScheme())
          && uri.getHost() != null
          && uri.getRawUserInfo() == null
          && (path == null || path.isEmpty() || "/".equals(path))
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null;
    }

    private static void requireSecretNameChars(
        final String property, final String name, final @Nullable String value) {
      if (value != null && !SECRET_NAME_PATTERN.matcher(value).matches()) {
        throw new IllegalArgumentException(
            property
                + "."
                + name
                + " must contain only [0-9A-Za-z-] to form valid Key Vault secret names, but has"
                + " an invalid character at index "
                + firstInvalidCharIndex(value));
      }
    }

    private static int firstInvalidCharIndex(final String value) {
      for (int i = 0; i < value.length(); i++) {
        final char c = value.charAt(i);
        if (!(c == '-' || c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) {
          return i;
        }
      }
      return -1;
    }
  }
}
