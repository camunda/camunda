/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * This section allows configuring document stores.
 *
 * <p>Canonical unified configuration properties are under {@code camunda.document.*}, including:
 *
 * <ul>
 *   <li>{@code camunda.document.default-store-id}
 *   <li>{@code camunda.document.thread-pool-size}
 *   <li>{@code camunda.document.aws.<storeId>.*}
 *   <li>{@code camunda.document.gcp.<storeId>.*}
 *   <li>{@code camunda.document.azure.<storeId>.*}
 *   <li>{@code camunda.document.local.<storeId>.*}
 *   <li>{@code camunda.document.in-memory.<storeId>.*}
 * </ul>
 *
 * <p>The legacy {@code DOCUMENT_*} properties are deprecated and supported through a compatibility
 * bridge.
 */
public class Document {

  /** Default document store id to use when a request does not explicitly choose a store. */
  private String defaultStoreId;

  /** Thread pool size used for document store operations. */
  private Integer threadPoolSize;

  /** AWS document stores keyed by store id. */
  @NestedConfigurationProperty private Map<String, AwsStore> aws = new LinkedHashMap<>();

  /** GCP document stores keyed by store id. */
  @NestedConfigurationProperty private Map<String, GcpStore> gcp = new LinkedHashMap<>();

  /** Azure document stores keyed by store id. */
  @NestedConfigurationProperty private Map<String, AzureStore> azure = new LinkedHashMap<>();

  /** Local filesystem document stores keyed by store id. */
  @NestedConfigurationProperty private Map<String, LocalStore> local = new LinkedHashMap<>();

  /** In-memory document stores keyed by store id. */
  @NestedConfigurationProperty private Map<String, InMemoryStore> inMemory = new LinkedHashMap<>();

  /**
   * Optional per-tenant restriction: a flat list of store ids that narrows the inherited catalog to
   * a subset for this physical tenant (least privilege). Meaningful only on a tenant overlay
   * ({@code camunda.physical-tenants.<id>.document.assigned}); ignored at the root. An empty list
   * means "no restriction" — the tenant keeps the full union of inherited and private stores.
   */
  private List<String> assigned = new ArrayList<>();

  public String getDefaultStoreId() {
    final String value =
        UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
            "camunda.document.default-store-id",
            defaultStoreId,
            String.class,
            BackwardsCompatibilityMode.SUPPORTED,
            Set.of("DOCUMENT_DEFAULT_STORE_ID"));
    return value != null ? value.toLowerCase() : null;
  }

  public void setDefaultStoreId(final String defaultStoreId) {
    this.defaultStoreId = defaultStoreId;
  }

  public Integer getThreadPoolSize() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        "camunda.document.thread-pool-size",
        threadPoolSize,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of("DOCUMENT_THREAD_POOL_SIZE"));
  }

  public void setThreadPoolSize(final Integer threadPoolSize) {
    this.threadPoolSize = threadPoolSize;
  }

  public Map<String, AwsStore> getAws() {
    return aws;
  }

  public void setAws(final Map<String, AwsStore> aws) {
    this.aws = aws;
  }

  public Map<String, GcpStore> getGcp() {
    return gcp;
  }

  public void setGcp(final Map<String, GcpStore> gcp) {
    this.gcp = gcp;
  }

  public Map<String, AzureStore> getAzure() {
    return azure;
  }

  public void setAzure(final Map<String, AzureStore> azure) {
    this.azure = azure;
  }

  public Map<String, LocalStore> getLocal() {
    return local;
  }

  public void setLocal(final Map<String, LocalStore> local) {
    this.local = local;
  }

  public Map<String, InMemoryStore> getInMemory() {
    return inMemory;
  }

  public void setInMemory(final Map<String, InMemoryStore> inMemory) {
    this.inMemory = inMemory;
  }

  public List<String> getAssigned() {
    return assigned;
  }

  public void setAssigned(final List<String> assigned) {
    this.assigned = assigned;
  }

  public static class AwsStore {
    private String bucketName;
    private String bucketPath;
    private String region;
    private Long bucketTtl;

    public String getBucketName() {
      return bucketName;
    }

    public void setBucketName(final String bucketName) {
      this.bucketName = bucketName;
    }

    public String getBucketPath() {
      return bucketPath;
    }

    public void setBucketPath(final String bucketPath) {
      this.bucketPath = bucketPath;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(final String region) {
      this.region = region;
    }

    public Long getBucketTtl() {
      return bucketTtl;
    }

    public void setBucketTtl(final Long bucketTtl) {
      this.bucketTtl = bucketTtl;
    }
  }

  public static class GcpStore {
    private String bucketName;
    private String prefix;

    public String getBucketName() {
      return bucketName;
    }

    public void setBucketName(final String bucketName) {
      this.bucketName = bucketName;
    }

    public String getPrefix() {
      return prefix;
    }

    public void setPrefix(final String prefix) {
      this.prefix = prefix;
    }
  }

  public static class AzureStore {
    private String containerName;
    private String containerPath;
    private String endpoint;
    private String connectionString;

    public String getContainerName() {
      return containerName;
    }

    public void setContainerName(final String containerName) {
      this.containerName = containerName;
    }

    public String getContainerPath() {
      return containerPath;
    }

    public void setContainerPath(final String containerPath) {
      this.containerPath = containerPath;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(final String endpoint) {
      this.endpoint = endpoint;
    }

    public String getConnectionString() {
      return connectionString;
    }

    public void setConnectionString(final String connectionString) {
      this.connectionString = connectionString;
    }
  }

  public static class LocalStore {
    private String path;

    public String getPath() {
      return path;
    }

    public void setPath(final String path) {
      this.path = path;
    }
  }

  public static class InMemoryStore {}
}
