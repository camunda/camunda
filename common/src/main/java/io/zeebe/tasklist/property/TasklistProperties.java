/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/** This class contains all project configuration parameters. */
@Component
@Configuration
@ConfigurationProperties(TasklistProperties.PREFIX)
public class TasklistProperties {

  public static final String PREFIX = "zeebe.tasklist";
  public static final long BATCH_OPERATION_MAX_SIZE_DEFAULT = 1_000_000L;

  private boolean importerEnabled = true;
  private boolean archiverEnabled = true;
  private boolean webappEnabled = true;

  private boolean persistentSessionsEnabled = false;
  /** Indicates, whether CSRF prevention is enabled. */
  private boolean csrfPreventionEnabled = true;

  /** Standard user data */
  private String username = "demo";

  private String password = "demo";

  /** Maximum size of batch operation. */
  private Long batchOperationMaxSize = BATCH_OPERATION_MAX_SIZE_DEFAULT;

  private boolean enterprise = false;

  @NestedConfigurationProperty
  private TasklistElasticsearchProperties elasticsearch = new TasklistElasticsearchProperties();

  @NestedConfigurationProperty
  private ZeebeElasticsearchProperties zeebeElasticsearch = new ZeebeElasticsearchProperties();

  @NestedConfigurationProperty private ZeebeProperties zeebe = new ZeebeProperties();

  @NestedConfigurationProperty private ImportProperties importer = new ImportProperties();

  @NestedConfigurationProperty private ArchiverProperties archiver = new ArchiverProperties();

  @NestedConfigurationProperty private ClientProperties client = new ClientProperties();

  @NestedConfigurationProperty
  private ClusterNodeProperties clusterNode = new ClusterNodeProperties();

  @NestedConfigurationProperty private Auth0Properties auth0 = new Auth0Properties();

  public boolean isImporterEnabled() {
    return importerEnabled;
  }

  public void setImporterEnabled(boolean importerEnabled) {
    this.importerEnabled = importerEnabled;
  }

  public boolean isArchiverEnabled() {
    return archiverEnabled;
  }

  public void setArchiverEnabled(boolean archiverEnabled) {
    this.archiverEnabled = archiverEnabled;
  }

  public boolean isWebappEnabled() {
    return webappEnabled;
  }

  public void setWebappEnabled(boolean webappEnabled) {
    this.webappEnabled = webappEnabled;
  }

  public Long getBatchOperationMaxSize() {
    return batchOperationMaxSize;
  }

  public void setBatchOperationMaxSize(Long batchOperationMaxSize) {
    this.batchOperationMaxSize = batchOperationMaxSize;
  }

  public boolean isCsrfPreventionEnabled() {
    return csrfPreventionEnabled;
  }

  public void setCsrfPreventionEnabled(boolean csrfPreventionEnabled) {
    this.csrfPreventionEnabled = csrfPreventionEnabled;
  }

  public TasklistElasticsearchProperties getElasticsearch() {
    return elasticsearch;
  }

  public void setElasticsearch(TasklistElasticsearchProperties elasticsearch) {
    this.elasticsearch = elasticsearch;
  }

  public ZeebeElasticsearchProperties getZeebeElasticsearch() {
    return zeebeElasticsearch;
  }

  public void setZeebeElasticsearch(ZeebeElasticsearchProperties zeebeElasticsearch) {
    this.zeebeElasticsearch = zeebeElasticsearch;
  }

  public ZeebeProperties getZeebe() {
    return zeebe;
  }

  public void setZeebe(ZeebeProperties zeebe) {
    this.zeebe = zeebe;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public ImportProperties getImporter() {
    return importer;
  }

  public void setImporter(ImportProperties importer) {
    this.importer = importer;
  }

  public ArchiverProperties getArchiver() {
    return archiver;
  }

  public void setArchiver(ArchiverProperties archiver) {
    this.archiver = archiver;
  }

  public ClusterNodeProperties getClusterNode() {
    return clusterNode;
  }

  public void setClusterNode(ClusterNodeProperties clusterNode) {
    this.clusterNode = clusterNode;
  }

  public boolean isEnterprise() {
    return enterprise;
  }

  public void setEnterprise(boolean enterprise) {
    this.enterprise = enterprise;
  }

  public ClientProperties getClient() {
    return client;
  }

  public void setClient(final ClientProperties client) {
    this.client = client;
  }

  public boolean isPersistentSessionsEnabled() {
    return persistentSessionsEnabled;
  }

  public TasklistProperties setPersistentSessionsEnabled(boolean persistentSessionsEnabled) {
    this.persistentSessionsEnabled = persistentSessionsEnabled;
    return this;
  }

  public Auth0Properties getAuth0() {
    return auth0;
  }

  public TasklistProperties setAuth0(final Auth0Properties auth0) {
    this.auth0 = auth0;
    return this;
  }
}
