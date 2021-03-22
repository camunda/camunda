/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.property;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * This class contains all project configuration parameters.
 */
@Component
@Configuration
@ConfigurationProperties(OperateProperties.PREFIX)
public class OperateProperties {

  public static final String PREFIX = "camunda.operate";
  public static final long BATCH_OPERATION_MAX_SIZE_DEFAULT = 1_000_000L;

  private boolean importerEnabled = true;
  private boolean archiverEnabled = true;
  private boolean webappEnabled = true;

  private boolean persistentSessionsEnabled = false;
  /**
   * Indicates, whether CSRF prevention is enabled.
   */
  private boolean csrfPreventionEnabled = true;

  /**
   * Standard user data
   */
  private String username = "demo";
  private String password = "demo";

  /**
   * Maximum size of batch operation.
   */
  private Long batchOperationMaxSize = BATCH_OPERATION_MAX_SIZE_DEFAULT;

  private boolean enterprise = false;

  @NestedConfigurationProperty
  private OperateElasticsearchProperties elasticsearch = new OperateElasticsearchProperties();

  @NestedConfigurationProperty
  private ZeebeElasticsearchProperties zeebeElasticsearch = new ZeebeElasticsearchProperties();

  @NestedConfigurationProperty
  private ZeebeProperties zeebe = new ZeebeProperties();

  @NestedConfigurationProperty
  private OperationExecutorProperties operationExecutor = new OperationExecutorProperties();

  @NestedConfigurationProperty
  private ImportProperties importer = new ImportProperties();

  @NestedConfigurationProperty
  private ArchiverProperties archiver = new ArchiverProperties();

  @NestedConfigurationProperty
  private ClusterNodeProperties clusterNode = new ClusterNodeProperties();

  @NestedConfigurationProperty
  private LdapProperties ldap = new LdapProperties();

  @NestedConfigurationProperty
  private Auth0Properties auth0 = new Auth0Properties();

  @NestedConfigurationProperty
  private AlertingProperties alert = new AlertingProperties();

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

  public OperateElasticsearchProperties getElasticsearch() {
    return elasticsearch;
  }

  public void setElasticsearch(OperateElasticsearchProperties elasticsearch) {
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

  public LdapProperties getLdap(){ return ldap; }

  public void setLdap(LdapProperties ldap) {this.ldap = ldap; }

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
  public OperationExecutorProperties getOperationExecutor() {
    return operationExecutor;
  }

  public void setOperationExecutor(OperationExecutorProperties operationExecutor) {
    this.operationExecutor = operationExecutor;
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

  public Auth0Properties getAuth0() {
    return auth0;
  }

  public OperateProperties setAuth0(final Auth0Properties auth0) {
    this.auth0 = auth0;
    return this;
  }

  public AlertingProperties getAlert() {
    return alert;
  }

  public OperateProperties setAlert(final AlertingProperties alert) {
    this.alert = alert;
    return this;
  }

  public boolean isPersistentSessionsEnabled() {
    return persistentSessionsEnabled;
  }

  public OperateProperties setPersistentSessionsEnabled(boolean persistentSessionsEnabled) {
    this.persistentSessionsEnabled = persistentSessionsEnabled;
    return this;
  }
}
