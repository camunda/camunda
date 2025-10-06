/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

import io.camunda.operate.conditions.DatabaseType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * This class contains all project configuration parameters.
 *
 * <p>NOTE: Some of the fields of this object are overridden with values coming from the Unified
 * Configuration system, from the object
 * io.camunda.configuration.beanoverrides.OperatePropertiesOverride
 */
public class OperateProperties {

  public static final String PREFIX = "camunda.operate";

  public static final long BATCH_OPERATION_MAX_SIZE_DEFAULT = 1_000_000L;

  private static final String UNKNOWN_VERSION = "unknown-version";

  private boolean webappEnabled = true;

  private boolean rfc3339ApiDateFormat = false;

  private String password = "demo";

  private List<String> roles = List.of("OWNER");

  /** Maximum size of batch operation. */
  private Long batchOperationMaxSize = BATCH_OPERATION_MAX_SIZE_DEFAULT;

  private boolean enterprise = false;

  private String tasklistUrl = null;

  @Value("${camunda.operate.internal.version.current}")
  private String version = UNKNOWN_VERSION;

  @NestedConfigurationProperty
  private OperateElasticsearchProperties elasticsearch = new OperateElasticsearchProperties();

  @NestedConfigurationProperty
  private OperateOpensearchProperties opensearch = new OperateOpensearchProperties();

  @NestedConfigurationProperty private ZeebeProperties zeebe = new ZeebeProperties();

  @NestedConfigurationProperty
  private OperationExecutorProperties operationExecutor = new OperationExecutorProperties();

  @NestedConfigurationProperty private ImportProperties importer = new ImportProperties();

  @NestedConfigurationProperty private IdentityProperties identity = new IdentityProperties();

  @NestedConfigurationProperty private CloudProperties cloud = new CloudProperties();

  @NestedConfigurationProperty private BackupProperties backup = new BackupProperties();

  private DatabaseType database = DatabaseType.Elasticsearch;

  public DatabaseType getDatabase() {
    return database;
  }

  public void setDatabase(final DatabaseType database) {
    this.database = database;
  }

  public boolean isElasticsearchDB() {
    return DatabaseType.Elasticsearch.equals(database);
  }

  public boolean isWebappEnabled() {
    return webappEnabled;
  }

  public void setWebappEnabled(final boolean webappEnabled) {
    this.webappEnabled = webappEnabled;
  }

  public Long getBatchOperationMaxSize() {
    return batchOperationMaxSize;
  }

  public void setBatchOperationMaxSize(final Long batchOperationMaxSize) {
    this.batchOperationMaxSize = batchOperationMaxSize;
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

  public ZeebeProperties getZeebe() {
    return zeebe;
  }

  public void setZeebe(final ZeebeProperties zeebe) {
    this.zeebe = zeebe;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public OperationExecutorProperties getOperationExecutor() {
    return operationExecutor;
  }

  public void setOperationExecutor(final OperationExecutorProperties operationExecutor) {
    this.operationExecutor = operationExecutor;
  }

  public ImportProperties getImporter() {
    return importer;
  }

  public void setImporter(final ImportProperties importer) {
    this.importer = importer;
  }

  public boolean isEnterprise() {
    return enterprise;
  }

  public void setEnterprise(final boolean enterprise) {
    this.enterprise = enterprise;
  }

  public IdentityProperties getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityProperties identity) {
    this.identity = identity;
  }

  public CloudProperties getCloud() {
    return cloud;
  }

  public OperateProperties setCloud(final CloudProperties cloud) {
    this.cloud = cloud;
    return this;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(final List<String> roles) {
    this.roles = roles;
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

  public String getVersion() {
    return version;
  }

  public OperateProperties setVersion(final String version) {
    this.version = version;
    return this;
  }

  public boolean isRfc3339ApiDateFormat() {
    return rfc3339ApiDateFormat;
  }

  public void setRfc3339ApiDateFormat(final boolean rfc3339ApiDateFormat) {
    this.rfc3339ApiDateFormat = rfc3339ApiDateFormat;
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
