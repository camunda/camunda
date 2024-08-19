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

  public DatabaseConnection() {}

  public Integer getTimeout() {
    return timeout;
  }

  public void setTimeout(final Integer timeout) {
    this.timeout = timeout;
  }

  public Integer getResponseConsumerBufferLimitInMb() {
    return responseConsumerBufferLimitInMb;
  }

  public void setResponseConsumerBufferLimitInMb(final Integer responseConsumerBufferLimitInMb) {
    this.responseConsumerBufferLimitInMb = responseConsumerBufferLimitInMb;
  }

  public ProxyConfiguration getProxy() {
    return proxy;
  }

  public void setProxy(final ProxyConfiguration proxy) {
    this.proxy = proxy;
  }

  public String getPathPrefix() {
    return pathPrefix;
  }

  public void setPathPrefix(final String pathPrefix) {
    this.pathPrefix = pathPrefix;
  }

  public Boolean getSkipHostnameVerification() {
    return skipHostnameVerification;
  }

  public void setSkipHostnameVerification(final Boolean skipHostnameVerification) {
    this.skipHostnameVerification = skipHostnameVerification;
  }

  public List<DatabaseConnectionNodeConfiguration> getConnectionNodes() {
    return connectionNodes;
  }

  @JsonProperty("nodes")
  public void setConnectionNodes(final List<DatabaseConnectionNodeConfiguration> connectionNodes) {
    this.connectionNodes = connectionNodes;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DatabaseConnection;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $timeout = getTimeout();
    result = result * PRIME + ($timeout == null ? 43 : $timeout.hashCode());
    final Object $responseConsumerBufferLimitInMb = getResponseConsumerBufferLimitInMb();
    result =
        result * PRIME
            + ($responseConsumerBufferLimitInMb == null
                ? 43
                : $responseConsumerBufferLimitInMb.hashCode());
    final Object $proxy = getProxy();
    result = result * PRIME + ($proxy == null ? 43 : $proxy.hashCode());
    final Object $pathPrefix = getPathPrefix();
    result = result * PRIME + ($pathPrefix == null ? 43 : $pathPrefix.hashCode());
    final Object $skipHostnameVerification = getSkipHostnameVerification();
    result =
        result * PRIME
            + ($skipHostnameVerification == null ? 43 : $skipHostnameVerification.hashCode());
    final Object $connectionNodes = getConnectionNodes();
    result = result * PRIME + ($connectionNodes == null ? 43 : $connectionNodes.hashCode());
    return result;
  }

  @Override
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
    final Object this$timeout = getTimeout();
    final Object other$timeout = other.getTimeout();
    if (this$timeout == null ? other$timeout != null : !this$timeout.equals(other$timeout)) {
      return false;
    }
    final Object this$responseConsumerBufferLimitInMb = getResponseConsumerBufferLimitInMb();
    final Object other$responseConsumerBufferLimitInMb = other.getResponseConsumerBufferLimitInMb();
    if (this$responseConsumerBufferLimitInMb == null
        ? other$responseConsumerBufferLimitInMb != null
        : !this$responseConsumerBufferLimitInMb.equals(other$responseConsumerBufferLimitInMb)) {
      return false;
    }
    final Object this$proxy = getProxy();
    final Object other$proxy = other.getProxy();
    if (this$proxy == null ? other$proxy != null : !this$proxy.equals(other$proxy)) {
      return false;
    }
    final Object this$pathPrefix = getPathPrefix();
    final Object other$pathPrefix = other.getPathPrefix();
    if (this$pathPrefix == null
        ? other$pathPrefix != null
        : !this$pathPrefix.equals(other$pathPrefix)) {
      return false;
    }
    final Object this$skipHostnameVerification = getSkipHostnameVerification();
    final Object other$skipHostnameVerification = other.getSkipHostnameVerification();
    if (this$skipHostnameVerification == null
        ? other$skipHostnameVerification != null
        : !this$skipHostnameVerification.equals(other$skipHostnameVerification)) {
      return false;
    }
    final Object this$connectionNodes = getConnectionNodes();
    final Object other$connectionNodes = other.getConnectionNodes();
    if (this$connectionNodes == null
        ? other$connectionNodes != null
        : !this$connectionNodes.equals(other$connectionNodes)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DatabaseConnection(timeout="
        + getTimeout()
        + ", responseConsumerBufferLimitInMb="
        + getResponseConsumerBufferLimitInMb()
        + ", proxy="
        + getProxy()
        + ", pathPrefix="
        + getPathPrefix()
        + ", skipHostnameVerification="
        + getSkipHostnameVerification()
        + ", connectionNodes="
        + getConnectionNodes()
        + ")";
  }
}
