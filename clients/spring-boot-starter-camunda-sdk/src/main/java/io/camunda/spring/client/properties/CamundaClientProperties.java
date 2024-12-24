/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties;

import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.properties.common.IdentityProperties;
import io.camunda.spring.client.properties.common.ZeebeClientProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("camunda.client")
public class CamundaClientProperties {
  private Boolean enabled;
  private ClientMode mode;
  private String clusterId;
  private String region;

  @Deprecated(forRemoval = true, since = "8.7")
  @NestedConfigurationProperty
  private List<String> tenantIds;

  @NestedConfigurationProperty private CamundaClientAuthProperties auth;
  @NestedConfigurationProperty private IdentityProperties identity;
  @NestedConfigurationProperty private ZeebeClientProperties zeebe;
  private Integer executionThreads;
  private Duration messageTimeToLive;
  private Integer maxMessageSize;
  private Integer maxMetadataSize;
  private String caCertificatePath;
  private Duration keepAlive;
  private String overrideAuthority;
  @NestedConfigurationProperty private JobWorkerValue defaults;
  @NestedConfigurationProperty private Map<String, JobWorkerValue> override;
  private Boolean preferRestOverGrpc;
  private URI grpcAddress;
  private URI restAddress;
  @NestedConfigurationProperty private CamundaClientDeploymentProperties deployment;

  public JobWorkerValue getDefaults() {
    return defaults;
  }

  public void setDefaults(final JobWorkerValue defaults) {
    this.defaults = defaults;
  }

  public Map<String, JobWorkerValue> getOverride() {
    return override;
  }

  public void setOverride(final Map<String, JobWorkerValue> override) {
    this.override = override;
  }

  public Integer getExecutionThreads() {
    return executionThreads;
  }

  public void setExecutionThreads(final Integer executionThreads) {
    this.executionThreads = executionThreads;
  }

  public Duration getMessageTimeToLive() {
    return messageTimeToLive;
  }

  public void setMessageTimeToLive(final Duration messageTimeToLive) {
    this.messageTimeToLive = messageTimeToLive;
  }

  public String getCaCertificatePath() {
    return caCertificatePath;
  }

  public void setCaCertificatePath(final String caCertificatePath) {
    this.caCertificatePath = caCertificatePath;
  }

  public Duration getKeepAlive() {
    return keepAlive;
  }

  public void setKeepAlive(final Duration keepAlive) {
    this.keepAlive = keepAlive;
  }

  public String getOverrideAuthority() {
    return overrideAuthority;
  }

  public void setOverrideAuthority(final String overrideAuthority) {
    this.overrideAuthority = overrideAuthority;
  }

  public Integer getMaxMessageSize() {
    return maxMessageSize;
  }

  public void setMaxMessageSize(final Integer maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  public Integer getMaxMetadataSize() {
    return maxMetadataSize;
  }

  public void setMaxMetadataSize(final Integer maxMetadataSize) {
    this.maxMetadataSize = maxMetadataSize;
  }

  public Boolean getPreferRestOverGrpc() {
    return preferRestOverGrpc;
  }

  public void setPreferRestOverGrpc(final Boolean preferRestOverGrpc) {
    this.preferRestOverGrpc = preferRestOverGrpc;
  }

  public URI getGrpcAddress() {
    return grpcAddress;
  }

  public void setGrpcAddress(final URI grpcAddress) {
    this.grpcAddress = grpcAddress;
  }

  public URI getRestAddress() {
    return restAddress;
  }

  public void setRestAddress(final URI restAddress) {
    this.restAddress = restAddress;
  }

  public CamundaClientDeploymentProperties getDeployment() {
    return deployment;
  }

  public void setDeployment(final CamundaClientDeploymentProperties deployment) {
    this.deployment = deployment;
  }

  public ClientMode getMode() {
    return mode;
  }

  public void setMode(final ClientMode mode) {
    this.mode = mode;
  }

  public CamundaClientAuthProperties getAuth() {
    return auth;
  }

  public void setAuth(final CamundaClientAuthProperties auth) {
    this.auth = auth;
  }

  public ZeebeClientProperties getZeebe() {
    return zeebe;
  }

  public void setZeebe(final ZeebeClientProperties zeebe) {
    this.zeebe = zeebe;
  }

  public IdentityProperties getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityProperties identity) {
    this.identity = identity;
  }

  @Deprecated(forRemoval = true, since = "8.7")
  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  public enum ClientMode {
    selfManaged,
    saas
  }
}
