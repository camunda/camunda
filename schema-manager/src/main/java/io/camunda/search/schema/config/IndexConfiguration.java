/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.config;

import java.util.HashMap;
import java.util.Map;

public class IndexConfiguration {
  public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;
  private String zeebeIndexPrefix = "zeebe-record";

  private Integer numberOfShards = 1;
  private Integer numberOfReplicas = 0;
  private Integer templatePriority;

  private Map<String, Integer> replicasByIndexName = new HashMap<>();
  private Map<String, Integer> shardsByIndexName = new HashMap<>();

  private Integer variableSizeThreshold = DEFAULT_VARIABLE_SIZE_THRESHOLD;

  private boolean shouldWaitForImporters = true;

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

  public Integer getTemplatePriority() {
    return templatePriority;
  }

  public void setTemplatePriority(final Integer templatePriority) {
    this.templatePriority = templatePriority;
  }

  @Override
  public String toString() {
    return "IndexConfiguration{"
        + "numberOfShards="
        + numberOfShards
        + ", numberOfReplicas="
        + numberOfReplicas
        + ", templatePriority="
        + templatePriority
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
