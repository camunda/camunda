/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;

public class OperateElasticsearchExporterConfiguration {

  private static final String DEFAULT_URL = "http://localhost:9200";

  /** Comma-separated Elasticsearch http urls */
  public String url = DEFAULT_URL;

  /** The request timeout for the elastic search client. The timeout unit is milliseconds. */
  public int requestTimeoutMs = 30_000;

  /**
   * When true the treePaths will be defined for two cases: * call activity hierarchy * hierarchy of
   * flow node instances within one process instance
   */
  public boolean calculateTreePaths = true;

  /**
   * Number of elements to store in cache when calculating tree paths. Cache may be bigger, this
   * size defines number of elements with strong references that cannot be garbage collected.
   */
  public int treePathCacheSize = 1_000;

  public int degreeOfConcurrency = 2;

  /**
   * Controls if besides the Operate entities the Zeebe records are also written to the Zeebe ES
   * indexes (the behavior of the default exporter).
   */
  public boolean writeToZeebeIndexes = false;

  private final IndexConfiguration zeebeIndex = new IndexConfiguration();

  private final AuthenticationConfiguration authentication = new AuthenticationConfiguration();
  private final BulkConfiguration bulk = new BulkConfiguration();

  public boolean hasAuthenticationPresent() {
    return getAuthentication().isPresent();
  }

  public AuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public IndexConfiguration getZeebeIndex() {
    return zeebeIndex;
  }

  public BulkConfiguration getBulk() {
    return bulk;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getRequestTimeoutMs() {
    return requestTimeoutMs;
  }

  public void setRequestTimeoutMs(int requestTimeoutMs) {
    this.requestTimeoutMs = requestTimeoutMs;
  }

  public boolean isCalculateTreePaths() {
    return calculateTreePaths;
  }

  public boolean isWriteToZeebeIndexes() {
    return writeToZeebeIndexes;
  }

  public void setWriteToZeebeIndexes(boolean writeToZeebeIndexes) {
    this.writeToZeebeIndexes = writeToZeebeIndexes;
  }

  public OperateElasticsearchExporterConfiguration setCalculateTreePaths(
      final boolean calculateTreePaths) {
    this.calculateTreePaths = calculateTreePaths;
    return this;
  }

  public int getTreePathCacheSize() {
    return treePathCacheSize;
  }

  public OperateElasticsearchExporterConfiguration setTreePathCacheSize(
      final int treePathCacheSize) {
    this.treePathCacheSize = treePathCacheSize;
    return this;
  }

  public int getDegreeOfConcurrency() {
    return degreeOfConcurrency;
  }

  public void setDegreeOfConcurrency(int degreeOfConcurrency) {
    this.degreeOfConcurrency = degreeOfConcurrency;
  }

  public static class AuthenticationConfiguration {
    private String username;
    private String password;

    public boolean isPresent() {
      return (username != null && !username.isEmpty()) && (password != null && !password.isEmpty());
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

    @Override
    public String toString() {
      // we don't want to expose this information
      return "AuthenticationConfiguration{Confidential information}";
    }
  }

  public static class BulkConfiguration {
    // delay before forced flush
    public int delay = 5;
    // bulk size before flush
    public int size = 1_000;
    // memory limit of the bulk in bytes before flush
    public int memoryLimit = 10 * 1024 * 1024;

    @Override
    public String toString() {
      return "BulkConfiguration{"
          + "delay="
          + delay
          + ", size="
          + size
          + ", memoryLimit="
          + memoryLimit
          + '}';
    }
  }
}
