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
}
