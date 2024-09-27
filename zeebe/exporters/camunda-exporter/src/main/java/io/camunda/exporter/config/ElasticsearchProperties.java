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

public class ElasticsearchProperties {
  private boolean createSchema;
  private ConnectConfiguration connect = new ConnectConfiguration();
  private IndexSettings defaultSettings = new IndexSettings();
  private Map<String, Integer> replicasByIndexName = new HashMap<>();
  private Map<String, Integer> shardsByIndexName = new HashMap<>();

  public boolean isCreateSchema() {
    return createSchema;
  }

  public void setCreateSchema(final boolean createSchema) {
    this.createSchema = createSchema;
  }

  public ConnectConfiguration getConnect() {
    return connect;
  }

  public void setConnect(final ConnectConfiguration connect) {
    this.connect = connect;
  }

  public IndexSettings getDefaultSettings() {
    return defaultSettings;
  }

  public void setDefaultSettings(final IndexSettings defaultSettings) {
    this.defaultSettings = defaultSettings;
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

  public String getIndexPrefix() {
    return connect.getIndexPrefix();
  }

  public void setIndexPrefix(final String indexPrefix) {
    connect.setIndexPrefix(indexPrefix);
  }

  public static final class IndexSettings {
    private Integer numberOfShards = 1;
    private Integer numberOfReplicas = 0;

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
  }
}
