/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

import java.util.List;
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
@PropertySource("classpath:version.properties")
public class OperateProperties {

  public static final String PREFIX = "camunda.operate";
  public static final long BATCH_OPERATION_MAX_SIZE_DEFAULT = 1_000_000L;

  private static final String UNKNOWN_VERSION = "unknown-version";

  private boolean importerEnabled = true;
  private boolean archiverEnabled = true;
  private boolean webappEnabled = true;

  private boolean persistentSessionsEnabled = false;
  /**
   * Indicates, whether CSRF prevention is enabled.
   */
  @Deprecated
  private boolean csrfPreventionEnabled = true;

  /**
   * Standard user data
   */
  private String userId = "demo";
  private String displayName = "demo";

  private String password = "demo";

  private List<String> roles = List.of("OWNER");

  /**
   * Maximum size of batch operation.
   */
  private Long batchOperationMaxSize = BATCH_OPERATION_MAX_SIZE_DEFAULT;

  private boolean enterprise = false;

  private String tasklistUrl = null;

  @Value("${camunda.operate.internal.version.current}")
  private String version = UNKNOWN_VERSION;

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
  private IdentityProperties identity = new IdentityProperties();

  @NestedConfigurationProperty
  private AlertingProperties alert = new AlertingProperties();

  @NestedConfigurationProperty
  private CloudProperties cloud = new CloudProperties();

  @NestedConfigurationProperty
  private OAuthClientProperties client = new OAuthClientProperties();

  @NestedConfigurationProperty
  private BackupProperties backup = new BackupProperties();

  @NestedConfigurationProperty
  private WebSecurityProperties webSecurity = new WebSecurityProperties();

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

  @Deprecated
  public boolean isCsrfPreventionEnabled() {
    return csrfPreventionEnabled;
  }

  @Deprecated
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

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(final String displayName) {
    this.displayName = displayName;
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

  public WebSecurityProperties getWebSecurity(){
    return webSecurity;
  }

  public OperateProperties setWebSecurity(final WebSecurityProperties webSecurity){
    this.webSecurity = webSecurity;
    return this;
  }

  public IdentityProperties getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityProperties identity) {
    this.identity = identity;
  }

  public AlertingProperties getAlert() {
    return alert;
  }

  public OperateProperties setAlert(final AlertingProperties alert) {
    this.alert = alert;
    return this;
  }

  public CloudProperties getCloud() {
    return cloud;
  }

  public OperateProperties setCloud(final CloudProperties cloud) {
    this.cloud = cloud;
    return this;
  }

  public boolean isPersistentSessionsEnabled() {
    return persistentSessionsEnabled;
  }

  public OperateProperties setPersistentSessionsEnabled(boolean persistentSessionsEnabled) {
    this.persistentSessionsEnabled = persistentSessionsEnabled;
    return this;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(final List<String> roles) {
    this.roles = roles;
  }

  @Deprecated(forRemoval = true)
  public OAuthClientProperties getClient() {
    return client;
  }

  @Deprecated(forRemoval = true)
  public void setClient(final OAuthClientProperties client) {
    this.client = client;
  }

  public BackupProperties getBackup() {
    return backup;
  }

  public OperateProperties setBackup(BackupProperties backup) {
    this.backup = backup;
    return this;
  }

  public String getTasklistUrl() {
    return tasklistUrl;
  }

  public void setTasklistUrl(String tasklistUrl) {
    this.tasklistUrl = tasklistUrl;
  }

  public String getVersion() {
    return version;
  }

  public OperateProperties setVersion(String version) {
    this.version = version;
    return this;
  }
}
