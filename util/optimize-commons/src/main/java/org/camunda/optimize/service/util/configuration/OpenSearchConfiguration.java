/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.camunda.optimize.service.util.configuration.db.DatabaseBackup;
import org.camunda.optimize.service.util.configuration.db.DatabaseConnection;
import org.camunda.optimize.service.util.configuration.db.DatabaseSecurity;
import org.camunda.optimize.service.util.configuration.db.DatabaseSettings;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.ensureGreaterThanZero;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.resolvePathAsAbsoluteUrl;

@Data
public class OpenSearchConfiguration {

  private DatabaseConnection connection;

  private DatabaseBackup backup;

  private DatabaseSecurity security;

  private int scrollTimeoutInSeconds;

  private DatabaseSettings settings;

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
    ProxyConfiguration proxyConfiguration = connection.getProxy();
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
  public Integer getNumberOfShards() {
    return settings.getIndex().getNumberOfShards();
  }

  @JsonIgnore
  public Integer getNumberOfReplicas() {
    return settings.getIndex().getNumberOfReplicas();
  }

  @JsonIgnore
  public Integer getNestedDocumentsLimit() {
    Integer values = settings.getIndex().getNestedDocumentsLimit();
    ensureGreaterThanZero(values);
    return values;
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
    List<String> securitySSLCertificateAuthorities = security.getSsl().getCertificateAuthorities().stream()
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
  public DatabaseConnectionNodeConfiguration getFirstConnectionNode() {
    return getConnectionNodes().get(0);
  }

  @JsonIgnore
  public String getIndexPrefix() {
    return settings.getIndex().getPrefix();
  }

  @JsonIgnore
  public void setAggregationBucketLimit(final int bucketLimit) {
    settings.setAggregationBucketLimit(bucketLimit);
  }

  @JsonIgnore
  public void setIndexPrefix(final String prefix) {
    settings.getIndex().setPrefix(prefix);
  }

  @JsonIgnore
  public void setRefreshInterval(final String refreshInterval) {
    settings.getIndex().setRefreshInterval(refreshInterval);
  }

  @JsonIgnore
  public void setNumberOfReplicas(final int replicaCount) {
    settings.getIndex().setNumberOfReplicas(replicaCount);
  }

  @JsonIgnore
  public void setNestedDocumentsLimit(final int nestedDocumentLimit) {
    settings.getIndex().setNestedDocumentsLimit(nestedDocumentLimit);
  }

}

