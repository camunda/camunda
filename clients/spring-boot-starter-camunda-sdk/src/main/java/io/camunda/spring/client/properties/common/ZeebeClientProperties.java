/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties.common;

import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.properties.CamundaClientDeploymentProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Deprecated(forRemoval = true, since = "8.7")
public class ZeebeClientProperties extends ApiProperties {
  private Integer executionThreads;
  private Duration messageTimeToLive;
  private Integer maxMessageSize;
  private Integer maxMetadataSize;
  private Duration requestTimeout;
  private String caCertificatePath;
  private Duration keepAlive;
  private String overrideAuthority;
  @NestedConfigurationProperty private JobWorkerValue defaults;
  @NestedConfigurationProperty private Map<String, JobWorkerValue> override;
  private boolean preferRestOverGrpc;
  private URI grpcAddress;
  private URI restAddress;
  @NestedConfigurationProperty private CamundaClientDeploymentProperties deployment;

  @DeprecatedConfigurationProperty(replacement = "camunda.client.defaults")
  public JobWorkerValue getDefaults() {
    return defaults;
  }

  public void setDefaults(final JobWorkerValue defaults) {
    this.defaults = defaults;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.override")
  public Map<String, JobWorkerValue> getOverride() {
    return override;
  }

  public void setOverride(final Map<String, JobWorkerValue> override) {
    this.override = override;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.execution-threads")
  public Integer getExecutionThreads() {
    return executionThreads;
  }

  public void setExecutionThreads(final Integer executionThreads) {
    this.executionThreads = executionThreads;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.message-time-to-live")
  public Duration getMessageTimeToLive() {
    return messageTimeToLive;
  }

  public void setMessageTimeToLive(final Duration messageTimeToLive) {
    this.messageTimeToLive = messageTimeToLive;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.defaults.request-timeout")
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.ca-certificate-path")
  public String getCaCertificatePath() {
    return caCertificatePath;
  }

  public void setCaCertificatePath(final String caCertificatePath) {
    this.caCertificatePath = caCertificatePath;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.keep-alive")
  public Duration getKeepAlive() {
    return keepAlive;
  }

  public void setKeepAlive(final Duration keepAlive) {
    this.keepAlive = keepAlive;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.override-authority")
  public String getOverrideAuthority() {
    return overrideAuthority;
  }

  public void setOverrideAuthority(final String overrideAuthority) {
    this.overrideAuthority = overrideAuthority;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.max-message-size")
  public Integer getMaxMessageSize() {
    return maxMessageSize;
  }

  public void setMaxMessageSize(final Integer maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.max-metadata-size")
  public Integer getMaxMetadataSize() {
    return maxMetadataSize;
  }

  public void setMaxMetadataSize(final Integer maxMetadataSize) {
    this.maxMetadataSize = maxMetadataSize;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.prefer-rest-over-grpc")
  public boolean isPreferRestOverGrpc() {
    return preferRestOverGrpc;
  }

  public void setPreferRestOverGrpc(final boolean preferRestOverGrpc) {
    this.preferRestOverGrpc = preferRestOverGrpc;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.grpc-address")
  public URI getGrpcAddress() {
    return grpcAddress;
  }

  public void setGrpcAddress(final URI grpcAddress) {
    this.grpcAddress = grpcAddress;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.rest-address")
  public URI getRestAddress() {
    return restAddress;
  }

  public void setRestAddress(final URI restAddress) {
    this.restAddress = restAddress;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.deployment")
  public CamundaClientDeploymentProperties getDeployment() {
    return deployment;
  }

  public void setDeployment(final CamundaClientDeploymentProperties deployment) {
    this.deployment = deployment;
  }
}
