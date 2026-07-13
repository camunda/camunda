/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

public class OperateElasticsearchProperties {

  public static final String DEFAULT_INDEX_PREFIX = "";

  private String indexPrefix = DEFAULT_INDEX_PREFIX;

  /** Indicates whether operate does a proper health check for ES clusters. */
  private boolean healthCheckEnabled = true;

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public boolean isHealthCheckEnabled() {
    return healthCheckEnabled;
  }

  public void setHealthCheckEnabled(final boolean healthCheckEnabled) {
    this.healthCheckEnabled = healthCheckEnabled;
  }
}
