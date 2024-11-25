/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.util.configuration.ProxyConfiguration;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import java.util.List;

public class DatabaseConnection {

  protected Integer timeout;

  protected Integer responseConsumerBufferLimitInMb;
  protected String pathPrefix;
  protected Boolean skipHostnameVerification;

  @JsonProperty("nodes")
  protected List<DatabaseConnectionNodeConfiguration> connectionNodes;

  private ProxyConfiguration proxy;
  private Boolean awsEnabled;

  private Boolean clusterTaskCheckingEnabled;

  private Boolean initSchemaEnabled;

  public DatabaseConnection() {}

  public Integer getTimeout() {
    return this.timeout;
  }

  public Integer getResponseConsumerBufferLimitInMb() {
    return this.responseConsumerBufferLimitInMb;
  }

  public String getPathPrefix() {
    return this.pathPrefix;
  }

  public Boolean getSkipHostnameVerification() {
    return this.skipHostnameVerification;
  }

  public List<DatabaseConnectionNodeConfiguration> getConnectionNodes() {
    return this.connectionNodes;
  }

  public ProxyConfiguration getProxy() {
    return this.proxy;
  }

  public Boolean getAwsEnabled() {
    return this.awsEnabled;
  }

  public void setTimeout(final Integer timeout) {
    this.timeout = timeout;
  }

  public void setResponseConsumerBufferLimitInMb(final Integer responseConsumerBufferLimitInMb) {
    this.responseConsumerBufferLimitInMb = responseConsumerBufferLimitInMb;
  }

  public void setPathPrefix(final String pathPrefix) {
    this.pathPrefix = pathPrefix;
  }

  public void setSkipHostnameVerification(final Boolean skipHostnameVerification) {
    this.skipHostnameVerification = skipHostnameVerification;
  }

  @JsonProperty("nodes")
  public void setConnectionNodes(final List<DatabaseConnectionNodeConfiguration> connectionNodes) {
    this.connectionNodes = connectionNodes;
  }

  public void setProxy(final ProxyConfiguration proxy) {
    this.proxy = proxy;
  }

  public void setAwsEnabled(final Boolean awsEnabled) {
    this.awsEnabled = awsEnabled;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DatabaseConnection;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public Boolean isClusterTaskCheckingEnabled() {
    return clusterTaskCheckingEnabled;
  }

  public void setClusterTaskCheckingEnabled(final Boolean clusterTaskCheckingEnabled) {
    this.clusterTaskCheckingEnabled = clusterTaskCheckingEnabled;
  }

  public Boolean isInitSchemaEnabled() {
    return initSchemaEnabled;
  }

  public void setInitSchemaEnabled(final Boolean initSchemaEnabled) {
    this.initSchemaEnabled = initSchemaEnabled;
  }

  public String toString() {
    return "DatabaseConnection(timeout="
        + this.getTimeout()
        + ", responseConsumerBufferLimitInMb="
        + this.getResponseConsumerBufferLimitInMb()
        + ", pathPrefix="
        + this.getPathPrefix()
        + ", skipHostnameVerification="
        + this.getSkipHostnameVerification()
        + ", connectionNodes="
        + this.getConnectionNodes()
        + ", proxy="
        + this.getProxy()
        + ", awsEnabled="
        + this.getAwsEnabled()
        + ")";
  }
}
