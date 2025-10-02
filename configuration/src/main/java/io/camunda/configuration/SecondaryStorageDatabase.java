/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public abstract class SecondaryStorageDatabase {

  /** Endpoint for the database configured as secondary storage. */
  private String url = "http://localhost:9200";

  /** Name of the cluster */
  private String clusterName = databaseName().toLowerCase();

  @NestedConfigurationProperty private Security security = new Security();

  @NestedConfigurationProperty private History history = new History(databaseName());

  /** Username for the database configured as secondary storage. */
  private String username = "";

  /** Password for the database configured as secondary storage. */
  private String password = "";

  /** Prefix to apply to the indexes. */
  private String indexPrefix = "";

  /** How many shards Elasticsearch uses for all Tasklist indices. */
  private int numberOfShards = 1;

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(final Security security) {
    this.security = security;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(final String clusterName) {
    this.clusterName = clusterName;
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public int getNumberOfShards() {
    return numberOfShards;
  }

  public void setNumberOfShards(final int numberOfShards) {
    this.numberOfShards = numberOfShards;
  }

  public History getHistory() {
    return history;
  }

  public void setHistory(final History history) {
    this.history = history;
  }
}
