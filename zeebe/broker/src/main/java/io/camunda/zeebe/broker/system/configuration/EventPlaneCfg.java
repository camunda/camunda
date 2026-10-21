/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;
import org.springframework.util.unit.DataSize;

/**
 * The Event Plane's publisher: on each partition's leader, it uploads the committed journal as
 * segments into an object store (zeebe/event-plane/docs/contracts.md). Off by default.
 */
public final class EventPlaneCfg {

  private boolean enabled = false;
  private String clusterId;
  private StoreCfg store = new StoreCfg();
  private Duration cutInterval = Duration.ofSeconds(5);
  private DataSize maxSegmentSize = DataSize.ofMegabytes(16);
  private Duration pollInterval = Duration.ofMillis(500);
  private String serviceUrl;
  private Duration snapshotInterval = Duration.ZERO;
  private String restoreHistory;
  private boolean restoreFromBucket = false;
  private Long restoreTo;
  private boolean backupLog = false;
  private Duration backupLogTimeout = Duration.ofMinutes(5);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  /** The cluster id in object names; by default, the cluster configuration's or cluster name. */
  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  public StoreCfg getStore() {
    return store;
  }

  public void setStore(final StoreCfg store) {
    this.store = store;
  }

  /** How long a segment collects entries before it is uploaded. */
  public Duration getCutInterval() {
    return cutInterval;
  }

  public void setCutInterval(final Duration cutInterval) {
    this.cutInterval = cutInterval;
  }

  /** The size at which a segment is uploaded early. */
  public DataSize getMaxSegmentSize() {
    return maxSegmentSize;
  }

  public void setMaxSegmentSize(final DataSize maxSegmentSize) {
    this.maxSegmentSize = maxSegmentSize;
  }

  /** How often the publisher looks for newly committed entries. */
  public Duration getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(final Duration pollInterval) {
    this.pollInterval = pollInterval;
  }

  /** The stateless service that publishers notify of uploads, or null to notify nobody. */
  public String getServiceUrl() {
    return serviceUrl;
  }

  public void setServiceUrl(final String serviceUrl) {
    this.serviceUrl = serviceUrl;
  }

  /** How often a leader uploads its newest snapshot; zero to upload none. */
  public Duration getSnapshotInterval() {
    return snapshotInterval;
  }

  public void setSnapshotInterval(final Duration snapshotInterval) {
    this.snapshotInterval = snapshotInterval;
  }

  /**
   * For a restore only: the id of the history the Event Plane goes on in afterwards. The operator
   * picks a new UUID and passes the same one to every broker's restore.
   */
  public String getRestoreHistory() {
    return restoreHistory;
  }

  public void setRestoreHistory(final String restoreHistory) {
    this.restoreHistory = restoreHistory;
  }

  /**
   * For a restore only: whether to restore the partitions from the store's snapshots and segments,
   * instead of from backups.
   */
  public boolean isRestoreFromBucket() {
    return restoreFromBucket;
  }

  public void setRestoreFromBucket(final boolean restoreFromBucket) {
    this.restoreFromBucket = restoreFromBucket;
  }

  /**
   * For a restore from the bucket only: the position to restore to, the last record's at most; null
   * to restore everything the store holds.
   */
  public Long getRestoreTo() {
    return restoreTo;
  }

  public void setRestoreTo(final Long restoreTo) {
    this.restoreTo = restoreTo;
  }

  /**
   * Backups keep their log in the Event Plane's bucket instead of copying the journal's segments.
   */
  public boolean isBackupLog() {
    return backupLog;
  }

  public void setBackupLog(final boolean backupLog) {
    this.backupLog = backupLog;
  }

  /** How long a backup waits for the bucket to hold its checkpoint. */
  public Duration getBackupLogTimeout() {
    return backupLogTimeout;
  }

  public void setBackupLogTimeout(final Duration backupLogTimeout) {
    this.backupLogTimeout = backupLogTimeout;
  }

  @Override
  public String toString() {
    return "EventPlaneCfg{enabled=%s, clusterId=%s, store=%s, cutInterval=%s, maxSegmentSize=%s, pollInterval=%s, serviceUrl=%s, snapshotInterval=%s, restoreHistory=%s, restoreFromBucket=%s, restoreTo=%s, backupLog=%s, backupLogTimeout=%s}"
        .formatted(
            enabled,
            clusterId,
            store,
            cutInterval,
            maxSegmentSize,
            pollInterval,
            serviceUrl,
            snapshotInterval,
            restoreHistory,
            restoreFromBucket,
            restoreTo,
            backupLog,
            backupLogTimeout);
  }

  /** Where segments go: a directory, or an S3 bucket. */
  public static final class StoreCfg {
    private String type = "filesystem";
    private String path;
    private String bucket;
    private String basePath;
    private String region;
    private String endpoint;
    private String accessKeyId;
    private String secretAccessKey;
    private boolean forcePathStyle = false;

    /** {@code filesystem} or {@code s3}. */
    public String getType() {
      return type;
    }

    public void setType(final String type) {
      this.type = type;
    }

    public String getPath() {
      return path;
    }

    public void setPath(final String path) {
      this.path = path;
    }

    public String getBucket() {
      return bucket;
    }

    public void setBucket(final String bucket) {
      this.bucket = bucket;
    }

    public String getBasePath() {
      return basePath;
    }

    public void setBasePath(final String basePath) {
      this.basePath = basePath;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(final String region) {
      this.region = region;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(final String endpoint) {
      this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
      return accessKeyId;
    }

    public void setAccessKeyId(final String accessKeyId) {
      this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
      return secretAccessKey;
    }

    public void setSecretAccessKey(final String secretAccessKey) {
      this.secretAccessKey = secretAccessKey;
    }

    public boolean isForcePathStyle() {
      return forcePathStyle;
    }

    public void setForcePathStyle(final boolean forcePathStyle) {
      this.forcePathStyle = forcePathStyle;
    }

    @Override
    public String toString() {
      return "StoreCfg{type=%s, path=%s, bucket=%s, basePath=%s, region=%s, endpoint=%s, forcePathStyle=%s}"
          .formatted(type, path, bucket, basePath, region, endpoint, forcePathStyle);
    }
  }
}
