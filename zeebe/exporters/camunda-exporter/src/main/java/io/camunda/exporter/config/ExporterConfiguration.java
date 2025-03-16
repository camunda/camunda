/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.config;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import java.util.HashMap;
import java.util.Map;

public class ExporterConfiguration {

  private ConnectConfiguration connect = new ConnectConfiguration();
  private IndexSettings index = new IndexSettings();
  private BulkConfiguration bulk = new BulkConfiguration();
  private HistoryConfiguration history = new HistoryConfiguration();
  private CacheConfiguration processCache = new CacheConfiguration();
  private CacheConfiguration formCache = new CacheConfiguration();
  private PostExportConfiguration postExport = new PostExportConfiguration();
  private IncidentNotifierConfiguration notifier = new IncidentNotifierConfiguration();
  private boolean createSchema = true;

  public ConnectConfiguration getConnect() {
    return connect;
  }

  public void setConnect(final ConnectConfiguration connect) {
    this.connect = connect;
  }

  public IndexSettings getIndex() {
    return index;
  }

  public void setIndex(final IndexSettings index) {
    this.index = index;
  }

  public BulkConfiguration getBulk() {
    return bulk;
  }

  public void setBulk(final BulkConfiguration bulk) {
    this.bulk = bulk;
  }

  public CacheConfiguration getProcessCache() {
    return processCache;
  }

  public void setProcessCache(final CacheConfiguration processCache) {
    this.processCache = processCache;
  }

  public CacheConfiguration getFormCache() {
    return formCache;
  }

  public void setFormCache(final CacheConfiguration formCache) {
    this.formCache = formCache;
  }

  public boolean isCreateSchema() {
    return createSchema;
  }

  public void setCreateSchema(final boolean createSchema) {
    this.createSchema = createSchema;
  }

  public PostExportConfiguration getPostExport() {
    return postExport;
  }

  public void setPostExport(final PostExportConfiguration postExport) {
    this.postExport = postExport;
  }

  public IncidentNotifierConfiguration getNotifier() {
    return notifier;
  }

  public void setNotifier(final IncidentNotifierConfiguration notifier) {
    this.notifier = notifier;
  }

  public HistoryConfiguration getHistory() {
    return history;
  }

  public void setHistory(final HistoryConfiguration history) {
    this.history = history;
  }

  @Override
  public String toString() {
    return "ExporterConfiguration{"
        + "connect="
        + connect
        + ", index="
        + index
        + ", bulk="
        + bulk
        + ", history="
        + history
        + ", createSchema="
        + createSchema
        + ", processCache="
        + processCache
        + ", formCache="
        + formCache
        + ", postExport="
        + postExport
        + '}';
  }

  public static class IndexSettings {
    public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;
    private String prefix = "";
    private String zeebeIndexPrefix = "zeebe-record";

    private Integer numberOfShards = 1;
    private Integer numberOfReplicas = 0;

    private Map<String, Integer> replicasByIndexName = new HashMap<>();
    private Map<String, Integer> shardsByIndexName = new HashMap<>();

    private Integer variableSizeThreshold = DEFAULT_VARIABLE_SIZE_THRESHOLD;

    private boolean shouldWaitForImporters = true;

    public String getPrefix() {
      return prefix;
    }

    public void setPrefix(final String prefix) {
      this.prefix = prefix;
    }

    public Integer getNumberOfShards() {
      return numberOfShards;
    }

    public void setNumberOfShards(final Integer numberOfShards) {
      this.numberOfShards = numberOfShards;
    }

    public Integer getNumberOfReplicas() {
      return numberOfReplicas;
    }

    public void setNumberOfReplicas(final Integer numberOfReplicas) {
      this.numberOfReplicas = numberOfReplicas;
    }

    public Integer getVariableSizeThreshold() {
      return variableSizeThreshold;
    }

    public void setVariableSizeThreshold(final Integer variableSizeThreshold) {
      this.variableSizeThreshold = variableSizeThreshold;
    }

    public boolean shouldWaitForImporters() {
      return shouldWaitForImporters;
    }

    public String getZeebeIndexPrefix() {
      return zeebeIndexPrefix;
    }

    public void setZeebeIndexPrefix(final String zeebeIndexPrefix) {
      this.zeebeIndexPrefix = zeebeIndexPrefix;
    }

    public void setShouldWaitForImporters(final boolean shouldWaitForImporters) {
      this.shouldWaitForImporters = shouldWaitForImporters;
    }

    @Override
    public String toString() {
      return "IndexSettings{"
          + "prefix='"
          + prefix
          + '\''
          + ", numberOfShards="
          + numberOfShards
          + ", numberOfReplicas="
          + numberOfReplicas
          + ", replicasByIndexName="
          + replicasByIndexName
          + ", shardsByIndexName="
          + shardsByIndexName
          + ", variableSizeThreshold="
          + variableSizeThreshold
          + ", zeebeIndexPrefix='"
          + zeebeIndexPrefix
          + '}';
    }

    public Map<String, Integer> getReplicasByIndexName() {
      return replicasByIndexName;
    }

    public void setReplicasByIndexName(final Map<String, Integer> replicasByIndexName) {
      this.replicasByIndexName = replicasByIndexName;
    }

    public Map<String, Integer> getShardsByIndexName() {
      return shardsByIndexName;
    }

    public void setShardsByIndexName(final Map<String, Integer> shardsByIndexName) {
      this.shardsByIndexName = shardsByIndexName;
    }
  }

  public static class RetentionConfiguration {
    private boolean enabled = false;
    private String minimumAge = "30d";
    private String policyName;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public String getMinimumAge() {
      return minimumAge;
    }

    public void setMinimumAge(final String minimumAge) {
      this.minimumAge = minimumAge;
    }

    public String getPolicyName() {
      return policyName;
    }

    public void setPolicyName(final String policyName) {
      this.policyName = policyName;
    }

    @Override
    public String toString() {
      return "RetentionConfiguration{"
          + "enabled="
          + enabled
          + ", minimumAge='"
          + minimumAge
          + '\''
          + ", policyName='"
          + policyName
          + '\''
          + '}';
    }
  }

  public static class BulkConfiguration {
    // delay before forced flush
    private int delay = 5;
    // bulk size before flush
    private int size = 1_000;

    public int getDelay() {
      return delay;
    }

    public void setDelay(final int delay) {
      this.delay = delay;
    }

    public int getSize() {
      return size;
    }

    public void setSize(final int size) {
      this.size = size;
    }

    @Override
    public String toString() {
      return "BulkConfiguration{" + "delay=" + delay + ", size=" + size + '}';
    }
  }

  public static class HistoryConfiguration {
    private String elsRolloverDateFormat = "date";
    private String rolloverInterval = "1d";
    private int rolloverBatchSize = 100;
    private String waitPeriodBeforeArchiving = "1h";
    private int delayBetweenRuns = 2000;
    private int maxDelayBetweenRuns = 60000;
    private RetentionConfiguration retention = new RetentionConfiguration();

    public String getElsRolloverDateFormat() {
      return elsRolloverDateFormat;
    }

    public void setElsRolloverDateFormat(final String elsRolloverDateFormat) {
      this.elsRolloverDateFormat = elsRolloverDateFormat;
    }

    public String getRolloverInterval() {
      return rolloverInterval;
    }

    public void setRolloverInterval(final String rolloverInterval) {
      this.rolloverInterval = rolloverInterval;
    }

    public String getArchivingTimePoint() {
      return "now-" + waitPeriodBeforeArchiving;
    }

    public int getRolloverBatchSize() {
      return rolloverBatchSize;
    }

    public void setRolloverBatchSize(final int rolloverBatchSize) {
      this.rolloverBatchSize = rolloverBatchSize;
    }

    public RetentionConfiguration getRetention() {
      return retention;
    }

    public void setRetention(final RetentionConfiguration retention) {
      this.retention = retention;
    }

    public String getWaitPeriodBeforeArchiving() {
      return waitPeriodBeforeArchiving;
    }

    public void setWaitPeriodBeforeArchiving(final String waitPeriodBeforeArchiving) {
      this.waitPeriodBeforeArchiving = waitPeriodBeforeArchiving;
    }

    public int getDelayBetweenRuns() {
      return delayBetweenRuns;
    }

    public void setDelayBetweenRuns(final int delayBetweenRuns) {
      this.delayBetweenRuns = delayBetweenRuns;
    }

    public int getMaxDelayBetweenRuns() {
      return maxDelayBetweenRuns;
    }

    public void setMaxDelayBetweenRuns(final int maxDelayBetweenRuns) {
      this.maxDelayBetweenRuns = maxDelayBetweenRuns;
    }

    @Override
    public String toString() {
      return "ArchiverConfiguration{"
          + ", elsRolloverDateFormat='"
          + elsRolloverDateFormat
          + '\''
          + ", rolloverInterval='"
          + rolloverInterval
          + '\''
          + ", rolloverBatchSize="
          + rolloverBatchSize
          + ", waitPeriodBeforeArchiving='"
          + waitPeriodBeforeArchiving
          + '\''
          + ", delayBetweenRuns="
          + delayBetweenRuns
          + ", maxDelayBetweenRuns="
          + maxDelayBetweenRuns
          + ", retention="
          + retention
          + '}';
    }
  }

  public static class CacheConfiguration {
    private int maxCacheSize = 10000;

    public int getMaxCacheSize() {
      return maxCacheSize;
    }

    public void setMaxCacheSize(final int maxCacheSize) {
      this.maxCacheSize = maxCacheSize;
    }

    @Override
    public String toString() {
      return "CacheConfiguration{" + "cacheSize=" + maxCacheSize + '}';
    }
  }

  public static final class PostExportConfiguration {
    private int batchSize = 100;
    private int delayBetweenRuns = 2000;
    private int maxDelayBetweenRuns = 60000;
    private boolean ignoreMissingData = false;

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(final int batchSize) {
      this.batchSize = batchSize;
    }

    public int getDelayBetweenRuns() {
      return delayBetweenRuns;
    }

    public void setDelayBetweenRuns(final int delayBetweenRuns) {
      this.delayBetweenRuns = delayBetweenRuns;
    }

    public int getMaxDelayBetweenRuns() {
      return maxDelayBetweenRuns;
    }

    public void setMaxDelayBetweenRuns(final int maxDelayBetweenRuns) {
      this.maxDelayBetweenRuns = maxDelayBetweenRuns;
    }

    public boolean isIgnoreMissingData() {
      return ignoreMissingData;
    }

    public void setIgnoreMissingData(final boolean ignoreMissingData) {
      this.ignoreMissingData = ignoreMissingData;
    }

    @Override
    public String toString() {
      return "PostExportConfiguration{"
          + "batchSize="
          + batchSize
          + ", delayBetweenRuns="
          + delayBetweenRuns
          + ", maxDelayBetweenRuns="
          + maxDelayBetweenRuns
          + ", ignoreMissingData="
          + ignoreMissingData
          + '}';
    }
  }

  public static final class IncidentNotifierConfiguration {

    private String webhook;

    /** Defines the domain which the user always sees */
    private String auth0Domain;

    private String m2mClientId;

    private String m2mClientSecret;

    private String m2mAudience;

    public String getWebhook() {
      return webhook;
    }

    public void setWebhook(final String webhook) {
      this.webhook = webhook;
    }

    public String getAuth0Domain() {
      return auth0Domain;
    }

    public void setAuth0Domain(final String auth0Domain) {
      this.auth0Domain = auth0Domain;
    }

    public String getM2mClientId() {
      return m2mClientId;
    }

    public void setM2mClientId(final String m2mClientId) {
      this.m2mClientId = m2mClientId;
    }

    public String getM2mClientSecret() {
      return m2mClientSecret;
    }

    public void setM2mClientSecret(final String m2mClientSecret) {
      this.m2mClientSecret = m2mClientSecret;
    }

    public String getM2mAudience() {
      return m2mAudience;
    }

    public void setM2mAudience(final String m2mAudience) {
      this.m2mAudience = m2mAudience;
    }
  }
}
