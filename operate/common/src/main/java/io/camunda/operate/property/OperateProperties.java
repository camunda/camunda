/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

import io.camunda.operate.conditions.DatabaseType;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * This class contains all project configuration parameters.
 *
 * <p>NOTE: Some of the fields of this object are overridden with values coming from the Unified
 * Configuration system, from the object io.camunda.operate.OperatePropertiesOverride
 */
public class OperateProperties {

  public static final String PREFIX = "camunda.operate";

  private boolean enterprise = false;

  private String tasklistUrl = null;

  @NestedConfigurationProperty
  private OperateElasticsearchProperties elasticsearch = new OperateElasticsearchProperties();

  @NestedConfigurationProperty
  private OperateOpensearchProperties opensearch = new OperateOpensearchProperties();

  @NestedConfigurationProperty private CloudProperties cloud = new CloudProperties();

  @NestedConfigurationProperty private BackupProperties backup = new BackupProperties();

  private DatabaseType database = DatabaseType.Elasticsearch;

  public DatabaseType getDatabase() {
    return database;
  }

  public void setDatabase(final DatabaseType database) {
    this.database = database;
  }

  public OperateElasticsearchProperties getElasticsearch() {
    return elasticsearch;
  }

  public void setElasticsearch(final OperateElasticsearchProperties elasticsearch) {
    this.elasticsearch = elasticsearch;
  }

  public OperateOpensearchProperties getOpensearch() {
    return opensearch;
  }

  public void setOpensearch(final OperateOpensearchProperties opensearch) {
    this.opensearch = opensearch;
  }

  public boolean isEnterprise() {
    return enterprise;
  }

  public void setEnterprise(final boolean enterprise) {
    this.enterprise = enterprise;
  }

  public CloudProperties getCloud() {
    return cloud;
  }

  public OperateProperties setCloud(final CloudProperties cloud) {
    this.cloud = cloud;
    return this;
  }

  public BackupProperties getBackup() {
    return backup;
  }

  public OperateProperties setBackup(final BackupProperties backup) {
    this.backup = backup;
    return this;
  }

  public String getTasklistUrl() {
    return tasklistUrl;
  }

  public void setTasklistUrl(final String tasklistUrl) {
    this.tasklistUrl = tasklistUrl;
  }

  public String getIndexPrefix(final DatabaseType databaseType) {
    return switch (databaseType) {
      case Elasticsearch -> getElasticsearch() == null ? null : getElasticsearch().getIndexPrefix();
      case Opensearch -> getOpensearch() == null ? null : getOpensearch().getIndexPrefix();
      default -> null;
    };
  }

  public String getIndexPrefix() {
    return getIndexPrefix(database);
  }
}
