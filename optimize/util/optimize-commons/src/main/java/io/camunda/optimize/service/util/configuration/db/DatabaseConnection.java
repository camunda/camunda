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

  public void setTimeout(Integer timeout) {
    this.timeout = timeout;
  }

  public void setResponseConsumerBufferLimitInMb(Integer responseConsumerBufferLimitInMb) {
    this.responseConsumerBufferLimitInMb = responseConsumerBufferLimitInMb;
  }

  public void setPathPrefix(String pathPrefix) {
    this.pathPrefix = pathPrefix;
  }

  public void setSkipHostnameVerification(Boolean skipHostnameVerification) {
    this.skipHostnameVerification = skipHostnameVerification;
  }

  @JsonProperty("nodes")
  public void setConnectionNodes(List<DatabaseConnectionNodeConfiguration> connectionNodes) {
    this.connectionNodes = connectionNodes;
  }

  public void setProxy(ProxyConfiguration proxy) {
    this.proxy = proxy;
  }

  public void setAwsEnabled(Boolean awsEnabled) {
    this.awsEnabled = awsEnabled;
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DatabaseConnection)) {
      return false;
    }
    final DatabaseConnection other = (DatabaseConnection) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$timeout = this.getTimeout();
    final Object other$timeout = other.getTimeout();
    if (this$timeout == null ? other$timeout != null : !this$timeout.equals(other$timeout)) {
      return false;
    }
    final Object this$responseConsumerBufferLimitInMb = this.getResponseConsumerBufferLimitInMb();
    final Object other$responseConsumerBufferLimitInMb = other.getResponseConsumerBufferLimitInMb();
    if (this$responseConsumerBufferLimitInMb == null
        ? other$responseConsumerBufferLimitInMb != null
        : !this$responseConsumerBufferLimitInMb.equals(other$responseConsumerBufferLimitInMb)) {
      return false;
    }
    final Object this$pathPrefix = this.getPathPrefix();
    final Object other$pathPrefix = other.getPathPrefix();
    if (this$pathPrefix == null
        ? other$pathPrefix != null
        : !this$pathPrefix.equals(other$pathPrefix)) {
      return false;
    }
    final Object this$skipHostnameVerification = this.getSkipHostnameVerification();
    final Object other$skipHostnameVerification = other.getSkipHostnameVerification();
    if (this$skipHostnameVerification == null
        ? other$skipHostnameVerification != null
        : !this$skipHostnameVerification.equals(other$skipHostnameVerification)) {
      return false;
    }
    final Object this$connectionNodes = this.getConnectionNodes();
    final Object other$connectionNodes = other.getConnectionNodes();
    if (this$connectionNodes == null
        ? other$connectionNodes != null
        : !this$connectionNodes.equals(other$connectionNodes)) {
      return false;
    }
    final Object this$proxy = this.getProxy();
    final Object other$proxy = other.getProxy();
    if (this$proxy == null ? other$proxy != null : !this$proxy.equals(other$proxy)) {
      return false;
    }
    final Object this$awsEnabled = this.getAwsEnabled();
    final Object other$awsEnabled = other.getAwsEnabled();
    if (this$awsEnabled == null
        ? other$awsEnabled != null
        : !this$awsEnabled.equals(other$awsEnabled)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DatabaseConnection;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $timeout = this.getTimeout();
    result = result * PRIME + ($timeout == null ? 43 : $timeout.hashCode());
    final Object $responseConsumerBufferLimitInMb = this.getResponseConsumerBufferLimitInMb();
    result =
        result * PRIME
            + ($responseConsumerBufferLimitInMb == null
                ? 43
                : $responseConsumerBufferLimitInMb.hashCode());
    final Object $pathPrefix = this.getPathPrefix();
    result = result * PRIME + ($pathPrefix == null ? 43 : $pathPrefix.hashCode());
    final Object $skipHostnameVerification = this.getSkipHostnameVerification();
    result =
        result * PRIME
            + ($skipHostnameVerification == null ? 43 : $skipHostnameVerification.hashCode());
    final Object $connectionNodes = this.getConnectionNodes();
    result = result * PRIME + ($connectionNodes == null ? 43 : $connectionNodes.hashCode());
    final Object $proxy = this.getProxy();
    result = result * PRIME + ($proxy == null ? 43 : $proxy.hashCode());
    final Object $awsEnabled = this.getAwsEnabled();
    result = result * PRIME + ($awsEnabled == null ? 43 : $awsEnabled.hashCode());
    return result;
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
