/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

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
 * <p>Secrets configuration is overridable per physical tenant via {@code
 * camunda.physical-tenants.<id>.secrets.*}.
 */
@NullMarked
public class Secrets {

  @NestedConfigurationProperty private Stores stores = new Stores();

  public Stores getStores() {
    return stores;
  }

  public void setStores(final Stores stores) {
    this.stores = stores;
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
   * Configuration for a GCP Secret Manager store. Authentication is always identity-based (GCP
   * Application Default Credentials chain): no static credentials are accepted here by design.
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
     * @throws IllegalArgumentException if project-id or container-secret-id is set but blank, if
     *     path-prefix or container-secret-id contains characters outside {@code [a-zA-Z0-9_-]}, or
     *     if the effective container secret id (path-prefix + container-secret-id) exceeds 255
     *     characters
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
