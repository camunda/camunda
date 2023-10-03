/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.property;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/** This class contains all project configuration parameters. */
@Component
@Configuration
@ConfigurationProperties(TasklistProperties.PREFIX)
@PropertySource("classpath:version.properties")
public class TasklistProperties {

  public static final String PREFIX = "camunda.tasklist";
  public static final String ALPHA_RELEASES_SUFIX = "alpha";
  public static final long BATCH_OPERATION_MAX_SIZE_DEFAULT = 1_000_000L;
  public static final String ELASTIC_SEARCH = "elasticsearch";
  public static final String OPEN_SEARCH = "opensearch";
  public static String database = ELASTIC_SEARCH;
  private static final String UNKNOWN_VERSION = "unknown-version";

  private boolean importerEnabled = true;
  private boolean archiverEnabled = true;
  private boolean webappEnabled = true;

  private boolean persistentSessionsEnabled = false;
  /** Indicates, whether CSRF prevention is enabled. */
  @Deprecated private boolean csrfPreventionEnabled = true;

  private boolean fixUsernames = true;
  private String userId = "demo";
  private String displayName = "demo";
  private String password = "demo";
  private String operatorUserId = "act";
  private String operatorPassword = "act";
  private String operatorDisplayName = "act";
  private String readerUserId = "view";
  private String readerPassword = "view";
  private String readerDisplayName = "view";

  private List<String> roles = List.of("OWNER");

  /** Maximum size of batch operation. */
  private Long batchOperationMaxSize = BATCH_OPERATION_MAX_SIZE_DEFAULT;

  private boolean enterprise = false;

  @Value("${camunda.tasklist.internal.version.current}")
  private String version = UNKNOWN_VERSION;

  @NestedConfigurationProperty
  private SecurityProperties securityProperties = new SecurityProperties();

  @NestedConfigurationProperty
  private TasklistElasticsearchProperties elasticsearch = new TasklistElasticsearchProperties();

  @NestedConfigurationProperty
  private TasklistOpenSearchProperties openSearch = new TasklistOpenSearchProperties();

  @NestedConfigurationProperty
  private ZeebeElasticsearchProperties zeebeElasticsearch = new ZeebeElasticsearchProperties();

  @NestedConfigurationProperty
  private ZeebeOpenSearchProperties zeebeOpenSearch = new ZeebeOpenSearchProperties();

  @NestedConfigurationProperty private ZeebeProperties zeebe = new ZeebeProperties();

  @NestedConfigurationProperty private ImportProperties importer = new ImportProperties();

  @NestedConfigurationProperty private ArchiverProperties archiver = new ArchiverProperties();

  @NestedConfigurationProperty private ClientProperties client = new ClientProperties();

  @NestedConfigurationProperty private CloudProperties cloud = new CloudProperties();

  @NestedConfigurationProperty
  private FeatureFlagProperties featureFlag = new FeatureFlagProperties();

  @NestedConfigurationProperty
  private ClusterNodeProperties clusterNode = new ClusterNodeProperties();

  @NestedConfigurationProperty private Auth0Properties auth0 = new Auth0Properties();

  @NestedConfigurationProperty private IdentityProperties identity = new IdentityProperties();

  @NestedConfigurationProperty private BackupProperties backup = new BackupProperties();

  @NestedConfigurationProperty
  private MultiTenancyProperties multiTenancy = new MultiTenancyProperties();

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

  public boolean isAlphaVersion() {
    return getVersion().toLowerCase().contains(TasklistProperties.ALPHA_RELEASES_SUFIX);
  }

  public boolean isSelfManaged() {
    return getCloud().getClusterId() == null;
  }

  @Deprecated
  public boolean isCsrfPreventionEnabled() {
    return csrfPreventionEnabled;
  }

  @Deprecated
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

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
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

  public IdentityProperties getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityProperties identity) {
    this.identity = identity;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(final List<String> roles) {
    this.roles = roles;
  }

  public CloudProperties getCloud() {
    return cloud;
  }

  public void setCloud(final CloudProperties cloud) {
    this.cloud = cloud;
  }

  public BackupProperties getBackup() {
    return backup;
  }

  public TasklistProperties setBackup(BackupProperties backup) {
    this.backup = backup;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public TasklistProperties setVersion(String version) {
    this.version = version;
    return this;
  }

  public boolean isFixUsernames() {
    return fixUsernames;
  }

  public void setFixUsernames(boolean fixUsernames) {
    this.fixUsernames = fixUsernames;
  }

  public SecurityProperties getSecurityProperties() {
    return securityProperties;
  }

  public TasklistProperties setSecurityProperties(SecurityProperties securityProperties) {
    this.securityProperties = securityProperties;
    return this;
  }

  public FeatureFlagProperties getFeatureFlag() {
    return featureFlag;
  }

  public TasklistProperties setFeatureFlag(FeatureFlagProperties featureFlag) {
    this.featureFlag = featureFlag;
    return this;
  }

  public String getOperatorUserId() {
    return operatorUserId;
  }

  public TasklistProperties setOperatorUserId(String operatorUserId) {
    this.operatorUserId = operatorUserId;
    return this;
  }

  public String getOperatorPassword() {
    return operatorPassword;
  }

  public TasklistProperties setOperatorPassword(String operatorPassword) {
    this.operatorPassword = operatorPassword;
    return this;
  }

  public String getOperatorDisplayName() {
    return operatorDisplayName;
  }

  public TasklistProperties setOperatorDisplayName(String operatorDisplayName) {
    this.operatorDisplayName = operatorDisplayName;
    return this;
  }

  public String getReaderUserId() {
    return readerUserId;
  }

  public TasklistProperties setReaderUserId(String readerUserId) {
    this.readerUserId = readerUserId;
    return this;
  }

  public String getReaderPassword() {
    return readerPassword;
  }

  public TasklistProperties setReaderPassword(String readerPassword) {
    this.readerPassword = readerPassword;
    return this;
  }

  public String getReaderDisplayName() {
    return readerDisplayName;
  }

  public TasklistProperties setReaderDisplayName(String readerDisplayName) {
    this.readerDisplayName = readerDisplayName;
    return this;
  }

  public TasklistOpenSearchProperties getOpenSearch() {
    return openSearch;
  }

  public TasklistProperties setOpenSearch(TasklistOpenSearchProperties openSearch) {
    this.openSearch = openSearch;
    return this;
  }

  public static String getDatabase() {
    return database;
  }

  public TasklistProperties setDatabase(String database) {
    this.database = database;
    return this;
  }

  public ZeebeOpenSearchProperties getZeebeOpenSearch() {
    return zeebeOpenSearch;
  }

  public TasklistProperties setZeebeOpenSearch(ZeebeOpenSearchProperties zeebeOpenSearch) {
    this.zeebeOpenSearch = zeebeOpenSearch;
    return this;
  }

  public MultiTenancyProperties getMultiTenancy() {
    return multiTenancy;
  }

  public TasklistProperties setMultiTenancy(MultiTenancyProperties multiTenancy) {
    this.multiTenancy = multiTenancy;
    return this;
  }
}
