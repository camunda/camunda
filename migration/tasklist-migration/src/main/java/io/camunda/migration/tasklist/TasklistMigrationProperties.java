/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camunda.migration.tasklist")
public class TasklistMigrationProperties {

  private int batchSize = 20;
  private ConnectConfiguration connect = new ConnectConfiguration();
  private IndexConfig index = new IndexConfig();

  public ConnectConfiguration getConnect() {
    return connect;
  }

  public void setConnect(final ConnectConfiguration connect) {
    this.connect = connect;
  }

  public IndexConfig getIndex() {
    return index;
  }

  public void setIndex(final IndexConfig index) {
    this.index = index;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public static class IndexConfig {
    private String sourcePrefix;
    private String targetPrefix;

    public String getSourcePrefix() {
      return sourcePrefix;
    }

    public void setSourcePrefix(final String sourcePrefix) {
      this.sourcePrefix = sourcePrefix;
    }

    public String getTargetPrefix() {
      return targetPrefix;
    }

    public void setTargetPrefix(final String targetPrefix) {
      this.targetPrefix = targetPrefix;
    }
  }
}
