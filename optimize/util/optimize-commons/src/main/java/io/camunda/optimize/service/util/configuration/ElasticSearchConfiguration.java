/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationUtil.ensureGreaterThanZero;
import static io.camunda.optimize.service.util.configuration.ConfigurationUtil.resolvePathAsAbsoluteUrl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.service.util.configuration.db.DatabaseBackup;
import io.camunda.optimize.service.util.configuration.db.DatabaseConnection;
import io.camunda.optimize.service.util.configuration.db.DatabaseSecurity;
import io.camunda.optimize.service.util.configuration.db.DatabaseSettings;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ElasticSearchConfiguration {

  private DatabaseConnection connection;
  private DatabaseBackup backup;
  private DatabaseSecurity security;
  private int scrollTimeoutInSeconds;
  private DatabaseSettings settings;

  public ElasticSearchConfiguration() {}

  @JsonIgnore
  public Integer getConnectionTimeout() {
    return connection.getTimeout();
  }

  @JsonIgnore
  public Integer getResponseConsumerBufferLimitInMb() {
    return connection.getResponseConsumerBufferLimitInMb();
  }

  @JsonIgnore
  public ProxyConfiguration getProxyConfig() {
    final ProxyConfiguration proxyConfiguration = connection.getProxy();
    if (proxyConfiguration != null) {
      proxyConfiguration.validate();
    }
    return proxyConfiguration;
  }

  @JsonIgnore
  public Boolean getSkipHostnameVerification() {
    return connection.getSkipHostnameVerification();
  }

  @JsonIgnore
  public String getPathPrefix() {
    return connection.getPathPrefix();
  }

  @JsonIgnore
  public String getRefreshInterval() {
    return settings.getIndex().getRefreshInterval();
  }

  @JsonIgnore
  public void setRefreshInterval(final String refreshInterval) {
    settings.getIndex().setRefreshInterval(refreshInterval);
  }

  @JsonIgnore
  public Integer getNumberOfShards() {
    return settings.getIndex().getNumberOfShards();
  }

  @JsonIgnore
  public Integer getNumberOfReplicas() {
    return settings.getIndex().getNumberOfReplicas();
  }

  @JsonIgnore
  public void setNumberOfReplicas(final int replicaCount) {
    settings.getIndex().setNumberOfReplicas(replicaCount);
  }

  @JsonIgnore
  public Integer getNestedDocumentsLimit() {
    final Integer values = settings.getIndex().getNestedDocumentsLimit();
    ensureGreaterThanZero(values);
    return values;
  }

  @JsonIgnore
  public void setNestedDocumentsLimit(final int nestedDocumentLimit) {
    settings.getIndex().setNestedDocumentsLimit(nestedDocumentLimit);
  }

  @JsonIgnore
  public String getSecurityUsername() {
    return security.getUsername();
  }

  @JsonIgnore
  public String getSecurityPassword() {
    return security.getPassword();
  }

  @JsonIgnore
  public Boolean getSecuritySSLEnabled() {
    return security.getSsl().getEnabled();
  }

  @JsonIgnore
  public String getSecuritySSLCertificate() {
    String securitySSLCertificate = security.getSsl().getCertificate();
    if (securitySSLCertificate != null) {
      securitySSLCertificate = resolvePathAsAbsoluteUrl(securitySSLCertificate).getPath();
    }
    return securitySSLCertificate;
  }

  @JsonIgnore
  public Boolean getSecuritySslSelfSigned() {
    return security.getSsl().getSelfSigned();
  }

  @JsonIgnore
  public List<DatabaseConnectionNodeConfiguration> getConnectionNodes() {
    return connection.getConnectionNodes();
  }

  @JsonIgnore
  public List<String> getSecuritySSLCertificateAuthorities() {
    final List<String> securitySSLCertificateAuthorities =
        security.getSsl().getCertificateAuthorities().stream()
            .map(a -> resolvePathAsAbsoluteUrl(a).getPath())
            .toList();
    return Optional.ofNullable(securitySSLCertificateAuthorities).orElse(new ArrayList<>());
  }

  @JsonIgnore
  public String getSnapshotRepositoryName() {
    return backup.getSnapshotRepositoryName();
  }

  @JsonIgnore
  public Integer getAggregationBucketLimit() {
    return settings.getAggregationBucketLimit();
  }

  @JsonIgnore
  public void setAggregationBucketLimit(final int bucketLimit) {
    settings.setAggregationBucketLimit(bucketLimit);
  }

  @JsonIgnore
  public DatabaseConnectionNodeConfiguration getFirstConnectionNode() {
    return getConnectionNodes().get(0);
  }

  @JsonIgnore
  public String getIndexPrefix() {
    return settings.getIndex().getPrefix();
  }

  @JsonIgnore
  public void setIndexPrefix(final String prefix) {
    settings.getIndex().setPrefix(prefix);
  }

  public DatabaseConnection getConnection() {
    return connection;
  }

  public void setConnection(final DatabaseConnection connection) {
    this.connection = connection;
  }

  public DatabaseBackup getBackup() {
    return backup;
  }

  public void setBackup(final DatabaseBackup backup) {
    this.backup = backup;
  }

  public DatabaseSecurity getSecurity() {
    return security;
  }

  public void setSecurity(final DatabaseSecurity security) {
    this.security = security;
  }

  public int getScrollTimeoutInSeconds() {
    return scrollTimeoutInSeconds;
  }

  public void setScrollTimeoutInSeconds(final int scrollTimeoutInSeconds) {
    this.scrollTimeoutInSeconds = scrollTimeoutInSeconds;
  }

  public DatabaseSettings getSettings() {
    return settings;
  }

  public void setSettings(final DatabaseSettings settings) {
    this.settings = settings;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ElasticSearchConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $connection = getConnection();
    result = result * PRIME + ($connection == null ? 43 : $connection.hashCode());
    final Object $backup = getBackup();
    result = result * PRIME + ($backup == null ? 43 : $backup.hashCode());
    final Object $security = getSecurity();
    result = result * PRIME + ($security == null ? 43 : $security.hashCode());
    result = result * PRIME + getScrollTimeoutInSeconds();
    final Object $settings = getSettings();
    result = result * PRIME + ($settings == null ? 43 : $settings.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ElasticSearchConfiguration)) {
      return false;
    }
    final ElasticSearchConfiguration other = (ElasticSearchConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$connection = getConnection();
    final Object other$connection = other.getConnection();
    if (this$connection == null
        ? other$connection != null
        : !this$connection.equals(other$connection)) {
      return false;
    }
    final Object this$backup = getBackup();
    final Object other$backup = other.getBackup();
    if (this$backup == null ? other$backup != null : !this$backup.equals(other$backup)) {
      return false;
    }
    final Object this$security = getSecurity();
    final Object other$security = other.getSecurity();
    if (this$security == null ? other$security != null : !this$security.equals(other$security)) {
      return false;
    }
    if (getScrollTimeoutInSeconds() != other.getScrollTimeoutInSeconds()) {
      return false;
    }
    final Object this$settings = getSettings();
    final Object other$settings = other.getSettings();
    if (this$settings == null ? other$settings != null : !this$settings.equals(other$settings)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ElasticSearchConfiguration(connection="
        + getConnection()
        + ", backup="
        + getBackup()
        + ", security="
        + getSecurity()
        + ", scrollTimeoutInSeconds="
        + getScrollTimeoutInSeconds()
        + ", settings="
        + getSettings()
        + ")";
  }
}
