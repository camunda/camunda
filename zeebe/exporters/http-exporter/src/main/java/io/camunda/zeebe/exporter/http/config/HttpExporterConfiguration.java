/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http.config;

public class HttpExporterConfiguration {

  // Path to the configuration json file for export subscriptions
  private String configPath;

  // These properties will override the default values in the configuration file.
  private String url;
  private Integer batchSize;
  private Integer batchInterval;
  private String jsonFilter;

  public String getConfigPath() {
    return configPath;
  }

  public void setConfigPath(final String configPath) {
    this.configPath = configPath;
  }

  public Integer getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public void setBatchSize(final Integer batchSize) {
    this.batchSize = batchSize;
  }

  public Integer getBatchInterval() {
    return batchInterval;
  }

  public void setBatchInterval(final int batchInterval) {
    this.batchInterval = batchInterval;
  }

  public void setBatchInterval(final Integer batchInterval) {
    this.batchInterval = batchInterval;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getJsonFilter() {
    return jsonFilter;
  }

  public void setJsonFilter(final String jsonFilter) {
    this.jsonFilter = jsonFilter;
  }
}
