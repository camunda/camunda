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
  private RetentionConfiguration retention = new RetentionConfiguration();
  private ArchiverConfiguration archiver = new ArchiverConfiguration();
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

  public RetentionConfiguration getRetention() {
    return retention;
  }

  public void setRetention(final RetentionConfiguration retention) {
    this.retention = retention;
  }

  public ArchiverConfiguration getArchiver() {
    return archiver;
  }

  public void setArchiver(final ArchiverConfiguration archiver) {
    this.archiver = archiver;
  }

  public boolean isCreateSchema() {
    return createSchema;
  }

  public void setCreateSchema(final boolean createSchema) {
    this.createSchema = createSchema;
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
        + ", retention="
        + retention
        + ", createSchema="
        + createSchema
        + ", archiver="
        + archiver
        + '}';
  }

  public static final class IndexSettings {
    public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;
    private String prefix = "";

    private Integer numberOfShards = 1;
    private Integer numberOfReplicas = 0;

    private Map<String, Integer> replicasByIndexName = new HashMap<>();
    private Map<String, Integer> shardsByIndexName = new HashMap<>();

    private Integer variableSizeThreshold = DEFAULT_VARIABLE_SIZE_THRESHOLD;

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

  public static final class RetentionConfiguration {
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

  public static class ArchiverConfiguration {
    private boolean rolloverEnabled = true;
    private String elsRolloverDateFormat = "date";
    private String rolloverInterval = "1d";
    private int rolloverBatchSize = 100;
    private String waitPeriodBeforeArchiving = "1h";
    private int delayBetweenRuns = 2000;

    public boolean isRolloverEnabled() {
      return rolloverEnabled;
    }

    public void setRolloverEnabled(final boolean rolloverEnabled) {
      this.rolloverEnabled = rolloverEnabled;
    }

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

    @Override
    public String toString() {
      return "RetentionConfiguration{"
          + "rolloverEnabled="
          + rolloverEnabled
          + '\''
          + ", elsRolloverDateFormat='"
          + elsRolloverDateFormat
          + '\''
          + ", rolloverInterval='"
          + rolloverInterval
          + '\''
          + ", rolloverBatchSize='"
          + rolloverBatchSize
          + '\''
          + ", waitPeriodBeforeArchiving='"
          + waitPeriodBeforeArchiving
          + '\''
          + ", delayBetweenRuns='"
          + delayBetweenRuns
          + '\''
          + '}';
    }
  }
}
