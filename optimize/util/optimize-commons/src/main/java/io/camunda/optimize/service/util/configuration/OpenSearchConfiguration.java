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
import io.camunda.search.connect.plugin.PluginConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class OpenSearchConfiguration {

  private DatabaseConnection connection;

  private DatabaseBackup backup;

  private DatabaseSecurity security;

  private int scrollTimeoutInSeconds;

  private DatabaseSettings settings;

  private Map<String, PluginConfiguration> interceptorPlugins;

  public OpenSearchConfiguration() {}

  @JsonIgnore
  public Integer getConnectionTimeout() {
    return connection.getTimeout();
  }

  @JsonIgnore
  public Integer getResponseConsumerBufferLimitInMb() {
    return connection.getResponseConsumerBufferLimitInMb();
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
            .collect(Collectors.toList());
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
    return this.connection;
  }

  public DatabaseBackup getBackup() {
    return this.backup;
  }

  public DatabaseSecurity getSecurity() {
    return this.security;
  }

  public int getScrollTimeoutInSeconds() {
    return this.scrollTimeoutInSeconds;
  }

  public DatabaseSettings getSettings() {
    return this.settings;
  }

  public Map<String, PluginConfiguration> getInterceptorPlugins() {
    return this.interceptorPlugins;
  }

  public void setConnection(DatabaseConnection connection) {
    this.connection = connection;
  }

  public void setBackup(DatabaseBackup backup) {
    this.backup = backup;
  }

  public void setSecurity(DatabaseSecurity security) {
    this.security = security;
  }

  public void setScrollTimeoutInSeconds(int scrollTimeoutInSeconds) {
    this.scrollTimeoutInSeconds = scrollTimeoutInSeconds;
  }

  public void setSettings(DatabaseSettings settings) {
    this.settings = settings;
  }

  public void setInterceptorPlugins(Map<String, PluginConfiguration> interceptorPlugins) {
    this.interceptorPlugins = interceptorPlugins;
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof OpenSearchConfiguration)) {
      return false;
    }
    final OpenSearchConfiguration other = (OpenSearchConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$connection = this.getConnection();
    final Object other$connection = other.getConnection();
    if (this$connection == null
        ? other$connection != null
        : !this$connection.equals(other$connection)) {
      return false;
    }
    final Object this$backup = this.getBackup();
    final Object other$backup = other.getBackup();
    if (this$backup == null ? other$backup != null : !this$backup.equals(other$backup)) {
      return false;
    }
    final Object this$security = this.getSecurity();
    final Object other$security = other.getSecurity();
    if (this$security == null ? other$security != null : !this$security.equals(other$security)) {
      return false;
    }
    if (this.getScrollTimeoutInSeconds() != other.getScrollTimeoutInSeconds()) {
      return false;
    }
    final Object this$settings = this.getSettings();
    final Object other$settings = other.getSettings();
    if (this$settings == null ? other$settings != null : !this$settings.equals(other$settings)) {
      return false;
    }
    final Object this$interceptorPlugins = this.getInterceptorPlugins();
    final Object other$interceptorPlugins = other.getInterceptorPlugins();
    if (this$interceptorPlugins == null
        ? other$interceptorPlugins != null
        : !this$interceptorPlugins.equals(other$interceptorPlugins)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof OpenSearchConfiguration;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $connection = this.getConnection();
    result = result * PRIME + ($connection == null ? 43 : $connection.hashCode());
    final Object $backup = this.getBackup();
    result = result * PRIME + ($backup == null ? 43 : $backup.hashCode());
    final Object $security = this.getSecurity();
    result = result * PRIME + ($security == null ? 43 : $security.hashCode());
    result = result * PRIME + this.getScrollTimeoutInSeconds();
    final Object $settings = this.getSettings();
    result = result * PRIME + ($settings == null ? 43 : $settings.hashCode());
    final Object $interceptorPlugins = this.getInterceptorPlugins();
    result = result * PRIME + ($interceptorPlugins == null ? 43 : $interceptorPlugins.hashCode());
    return result;
  }

  public String toString() {
    return "OpenSearchConfiguration(connection="
        + this.getConnection()
        + ", backup="
        + this.getBackup()
        + ", security="
        + this.getSecurity()
        + ", scrollTimeoutInSeconds="
        + this.getScrollTimeoutInSeconds()
        + ", settings="
        + this.getSettings()
        + ", interceptorPlugins="
        + this.getInterceptorPlugins()
        + ")";
  }
}
