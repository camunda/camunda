/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.property;

import io.camunda.operate.conditions.DatabaseInfo;
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
@ConfigurationProperties(OperateProperties.PREFIX)
@PropertySource("classpath:operate-version.properties")
public class OperateProperties {

  public static final String PREFIX = "camunda.operate";

  public static final long BATCH_OPERATION_MAX_SIZE_DEFAULT = 1_000_000L;

  private static final String UNKNOWN_VERSION = "unknown-version";

  private boolean importerEnabled = true;
  private boolean archiverEnabled = true;
  private boolean webappEnabled = true;

  private boolean rfc3339ApiDateFormat = false;

  private boolean persistentSessionsEnabled = false;

  /** Indicates, whether CSRF prevention is enabled. */
  @Deprecated private boolean csrfPreventionEnabled = true;

  /** Standard user data */
  private String userId = "demo";

  private String displayName = "demo";

  private String password = "demo";

  private List<String> roles = List.of("OWNER");

  /** Maximum size of batch operation. */
  private Long batchOperationMaxSize = BATCH_OPERATION_MAX_SIZE_DEFAULT;

  private boolean enterprise = false;

  private String tasklistUrl = null;

  @Value("${camunda.operate.internal.version.current}")
  private String version = UNKNOWN_VERSION;

  @NestedConfigurationProperty
  private final OperateElasticsearchProperties elasticsearch = new OperateElasticsearchProperties();

  @NestedConfigurationProperty
  private OperateOpensearchProperties opensearch = new OperateOpensearchProperties();

  @NestedConfigurationProperty
  private ZeebeElasticsearchProperties zeebeElasticsearch = new ZeebeElasticsearchProperties();

  @NestedConfigurationProperty
  private ZeebeOpensearchProperties zeebeOpensearch = new ZeebeOpensearchProperties();

  @NestedConfigurationProperty private ZeebeProperties zeebe = new ZeebeProperties();

  @NestedConfigurationProperty
  private OperationExecutorProperties operationExecutor = new OperationExecutorProperties();

  @NestedConfigurationProperty private ImportProperties importer = new ImportProperties();

  @NestedConfigurationProperty private ArchiverProperties archiver = new ArchiverProperties();

  @NestedConfigurationProperty
  private ClusterNodeProperties clusterNode = new ClusterNodeProperties();

  @NestedConfigurationProperty private LdapProperties ldap = new LdapProperties();

  @NestedConfigurationProperty private Auth0Properties auth0 = new Auth0Properties();

  @NestedConfigurationProperty private IdentityProperties identity = new IdentityProperties();

  @NestedConfigurationProperty private AlertingProperties alert = new AlertingProperties();

  @NestedConfigurationProperty private CloudProperties cloud = new CloudProperties();

  @NestedConfigurationProperty private OAuthClientProperties client = new OAuthClientProperties();

  @NestedConfigurationProperty private BackupProperties backup = new BackupProperties();

  @NestedConfigurationProperty
  private WebSecurityProperties webSecurity = new WebSecurityProperties();

  @NestedConfigurationProperty
  private MultiTenancyProperties multiTenancy = new MultiTenancyProperties();

  public boolean isImporterEnabled() {
    return importerEnabled;
  }

  public void setImporterEnabled(final boolean importerEnabled) {
    this.importerEnabled = importerEnabled;
  }

  public boolean isArchiverEnabled() {
    return archiverEnabled;
  }

  public void setArchiverEnabled(final boolean archiverEnabled) {
    this.archiverEnabled = archiverEnabled;
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

  @Deprecated
  public boolean isCsrfPreventionEnabled() {
    return csrfPreventionEnabled;
  }

  @Deprecated
  public void setCsrfPreventionEnabled(final boolean csrfPreventionEnabled) {
    this.csrfPreventionEnabled = csrfPreventionEnabled;
  }

  public OperateElasticsearchProperties getElasticsearch() {
    return elasticsearch;
  }

  public OperateOpensearchProperties getOpensearch() {
    return opensearch;
  }

  public void setOpensearch(final OperateOpensearchProperties opensearch) {
    this.opensearch = opensearch;
  }

  public ZeebeElasticsearchProperties getZeebeElasticsearch() {
    return zeebeElasticsearch;
  }

  public void setZeebeElasticsearch(final ZeebeElasticsearchProperties zeebeElasticsearch) {
    this.zeebeElasticsearch = zeebeElasticsearch;
  }

  public ZeebeOpensearchProperties getZeebeOpensearch() {
    return zeebeOpensearch;
  }

  public void setZeebeOpensearch(final ZeebeOpensearchProperties zeebeOpensearch) {
    this.zeebeOpensearch = zeebeOpensearch;
  }

  public ZeebeProperties getZeebe() {
    return zeebe;
  }

  public void setZeebe(final ZeebeProperties zeebe) {
    this.zeebe = zeebe;
  }

  public LdapProperties getLdap() {
    return ldap;
  }

  public void setLdap(final LdapProperties ldap) {
    this.ldap = ldap;
  }

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

  public ArchiverProperties getArchiver() {
    return archiver;
  }

  public void setArchiver(final ArchiverProperties archiver) {
    this.archiver = archiver;
  }

  public ClusterNodeProperties getClusterNode() {
    return clusterNode;
  }

  public void setClusterNode(final ClusterNodeProperties clusterNode) {
    this.clusterNode = clusterNode;
  }

  public boolean isEnterprise() {
    return enterprise;
  }

  public void setEnterprise(final boolean enterprise) {
    this.enterprise = enterprise;
  }

  public Auth0Properties getAuth0() {
    return auth0;
  }

  public OperateProperties setAuth0(final Auth0Properties auth0) {
    this.auth0 = auth0;
    return this;
  }

  public WebSecurityProperties getWebSecurity() {
    return webSecurity;
  }

  public OperateProperties setWebSecurity(final WebSecurityProperties webSecurity) {
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

  public OperateProperties setPersistentSessionsEnabled(final boolean persistentSessionsEnabled) {
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

  public MultiTenancyProperties getMultiTenancy() {
    return multiTenancy;
  }

  public OperateProperties setMultiTenancy(final MultiTenancyProperties multiTenancy) {
    this.multiTenancy = multiTenancy;
    return this;
  }

  public boolean isRfc3339ApiDateFormat() {
    return rfc3339ApiDateFormat;
  }

  public void setRfc3339ApiDateFormat(final boolean rfc3339ApiDateFormat) {
    this.rfc3339ApiDateFormat = rfc3339ApiDateFormat;
  }

  public String getIndexPrefix() {
    if (DatabaseInfo.isElasticsearch()) {
      if (getElasticsearch() != null) {
        return getElasticsearch().getIndexPrefix();
      } else {
        return null;
      }
    } else {
      if (getOpensearch() != null) {
        return getOpensearch().getIndexPrefix();
      } else {
        return null;
      }
    }
  }
}
