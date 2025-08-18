/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

import java.util.Map;

public class OperateOpensearchProperties extends OpensearchProperties {

  public static final String DEFAULT_INDEX_PREFIX = "operate";
  private static final int DEFAULT_NUMBER_OF_SHARDS = 1;
  private static final int DEFAULT_NUMBER_OF_REPLICAS = 0;
  private static final String DEFAULT_REFRESH_INTERVAL = "1s";

  private String indexPrefix = DEFAULT_INDEX_PREFIX;
  private int numberOfShards = DEFAULT_NUMBER_OF_SHARDS;
  private Map<String, Integer> numberOfShardsForIndices = Map.of();
  private int numberOfReplicas = DEFAULT_NUMBER_OF_REPLICAS;
  private Map<String, Integer> numberOfReplicasForIndices = Map.of();
  private String refreshInterval = DEFAULT_REFRESH_INTERVAL;

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public void setDefaultIndexPrefix() {
    setIndexPrefix(DEFAULT_INDEX_PREFIX);
  }

  public int getNumberOfShards() {
    return numberOfShards;
  }

  public void setNumberOfShards(final int numberOfShards) {
    this.numberOfShards = numberOfShards;
  }

  public int getNumberOfReplicas() {
    return numberOfReplicas;
  }

  public void setNumberOfReplicas(final int numberOfReplicas) {
    this.numberOfReplicas = numberOfReplicas;
  }

  public String getRefreshInterval() {
    return refreshInterval;
  }

  public void setRefreshInterval(final String refreshInterval) {
    this.refreshInterval = refreshInterval;
  }

  public Map<String, Integer> getNumberOfShardsForIndices() {
    return numberOfShardsForIndices;
  }

  public void setNumberOfShardsForIndices(final Map<String, Integer> numberOfShardsForIndices) {
    this.numberOfShardsForIndices = numberOfShardsForIndices;
  }

  public Map<String, Integer> getNumberOfReplicasForIndices() {
    return numberOfReplicasForIndices;
  }

  public void setNumberOfReplicasForIndices(final Map<String, Integer> numberOfReplicasForIndices) {
    this.numberOfReplicasForIndices = numberOfReplicasForIndices;
  }

  public int getNumberOfReplicas(final String indexName) {
    return numberOfReplicasForIndices.getOrDefault(indexName, numberOfReplicas);
  }

  public int getNumberOfShards(final String indexName) {
    return numberOfShardsForIndices.getOrDefault(indexName, numberOfShards);
  }
}
