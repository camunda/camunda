/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.config;

import io.camunda.exporter.http.matcher.Filter;
import java.util.List;

public class HttpExporterConfiguration {

  // Path to the configuration json file for export subscriptions
  private String configPath;

  // These properties will override the default values in the configuration file.
  private String url;
  private Integer batchSize = 100; // Default batch size
  private Integer batchInterval = 10000; // Default batch interval in milliseconds
  private String jsonFilter;
  private Integer maxRetries = 3; // Default maximum number of retries for failed requests
  private Long retryDelay = 1000L; // Default retry delay in milliseconds
  private Long timeout = 5000L; // Default timeout in milliseconds
  private Boolean continueOnError = false; // Default behavior for error handling
  private List<Filter> filters;

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

  public Integer getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(final Integer maxRetries) {
    this.maxRetries = maxRetries;
  }

  public Long getRetryDelay() {
    return retryDelay;
  }

  public void setRetryDelay(final Long retryDelay) {
    this.retryDelay = retryDelay;
  }

  public Long getTimeout() {
    return timeout;
  }

  public void setTimeout(final Long timeout) {
    this.timeout = timeout;
  }

  public Boolean getContinueOnError() {
    return continueOnError;
  }

  public void setContinueOnError(final Boolean continueOnError) {
    this.continueOnError = continueOnError;
  }

  public List<Filter> getFilters() {
    return filters;
  }

  public void setFilters(final List<Filter> filters) {
    this.filters = filters;
  }
}
