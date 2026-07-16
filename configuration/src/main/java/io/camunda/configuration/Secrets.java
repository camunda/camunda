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
 *   <li>{@code camunda.secrets.stores.aws-secrets-manager.<id>.region}
 *   <li>{@code camunda.secrets.stores.aws-secrets-manager.<id>.path-prefix}
 *   <li>{@code camunda.secrets.stores.aws-secrets-manager.<id>.batch-enabled}
 *   <li>{@code camunda.secrets.stores.aws-secrets-manager.<id>.batch-size}
 *   <li>{@code camunda.secrets.stores.aws-secrets-manager.<id>.container-secret-id}
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
    private Map<String, AwsSecretsManagerStore> awsSecretsManager = new LinkedHashMap<>();

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
    public Map<String, AwsSecretsManagerStore> getAwsSecretsManager() {
      awsSecretsManager.forEach((id, store) -> store.validate(id));
      return awsSecretsManager;
    }

    public void setAwsSecretsManager(final Map<String, AwsSecretsManagerStore> awsSecretsManager) {
      this.awsSecretsManager = awsSecretsManager;
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
     * this one named secret (e.g. {@code app-config}, a JSON object of key-value pairs). Mutually
     * exclusive with {@link #batchEnabled}, since only this single secret is ever fetched.
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
     * @throws IllegalArgumentException if batch-size is outside the 1..20 range AWS accepts, or if
     *     both batch-enabled and container-secret-id are set (the two are contradictory: batching
     *     fetches multiple distinct secrets in one call, while a container secret id means only one
     *     secret is ever fetched)
     */
    void validate(final String storeId) {
      if (batchSize < 1 || batchSize > 20) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.aws-secrets-manager."
                + storeId
                + ".batch-size must be between 1 and 20, but was "
                + batchSize);
      }
      if (batchEnabled && containerSecretId != null) {
        throw new IllegalArgumentException(
            "camunda.secrets.stores.aws-secrets-manager."
                + storeId
                + ".batch-enabled and .container-secret-id are mutually exclusive, but both were "
                + "configured");
      }
    }
  }
}
