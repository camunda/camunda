/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration.legacy;

import static io.zeebe.gateway.impl.configuration.legacy.ConfigurationDefaults.DEFAULT_CLUSTER_HOST;
import static io.zeebe.gateway.impl.configuration.legacy.ConfigurationDefaults.DEFAULT_CLUSTER_MEMBER_ID;
import static io.zeebe.gateway.impl.configuration.legacy.ConfigurationDefaults.DEFAULT_CLUSTER_NAME;
import static io.zeebe.gateway.impl.configuration.legacy.ConfigurationDefaults.DEFAULT_CLUSTER_PORT;
import static io.zeebe.gateway.impl.configuration.legacy.ConfigurationDefaults.DEFAULT_CONTACT_POINT_HOST;
import static io.zeebe.gateway.impl.configuration.legacy.ConfigurationDefaults.DEFAULT_CONTACT_POINT_PORT;
import static io.zeebe.gateway.impl.configuration.legacy.ConfigurationDefaults.DEFAULT_REQUEST_TIMEOUT;
import static io.zeebe.gateway.impl.configuration.legacy.EnvironmentConstants.ENV_GATEWAY_CLUSTER_HOST;
import static io.zeebe.gateway.impl.configuration.legacy.EnvironmentConstants.ENV_GATEWAY_CLUSTER_MEMBER_ID;
import static io.zeebe.gateway.impl.configuration.legacy.EnvironmentConstants.ENV_GATEWAY_CLUSTER_NAME;
import static io.zeebe.gateway.impl.configuration.legacy.EnvironmentConstants.ENV_GATEWAY_CLUSTER_PORT;
import static io.zeebe.gateway.impl.configuration.legacy.EnvironmentConstants.ENV_GATEWAY_CONTACT_POINT;
import static io.zeebe.gateway.impl.configuration.legacy.EnvironmentConstants.ENV_GATEWAY_REQUEST_TIMEOUT;

import io.zeebe.util.DurationUtil;
import io.zeebe.util.Environment;
import java.time.Duration;
import java.util.Objects;

@Deprecated(since = "0.23.0-alpha1")
/* Kept in order to be able to offer a migration path for old configuration.
 * It is not yet clear whether we intent to offer a migration path for old configurations.
 * This class might be moved or removed on short notice.
 */
public final class ClusterCfg {
  private String contactPoint = DEFAULT_CONTACT_POINT_HOST + ":" + DEFAULT_CONTACT_POINT_PORT;

  private String requestTimeout = DEFAULT_REQUEST_TIMEOUT;
  private String clusterName = DEFAULT_CLUSTER_NAME;
  private String memberId = DEFAULT_CLUSTER_MEMBER_ID;
  private String host = DEFAULT_CLUSTER_HOST;
  private int port = DEFAULT_CLUSTER_PORT;

  public void init(final Environment environment) {
    environment
        .get(ENV_GATEWAY_CONTACT_POINT)
        .map(v -> v.contains(":") ? v : v + ":" + DEFAULT_CONTACT_POINT_PORT)
        .ifPresent(this::setContactPoint);
    environment.get(ENV_GATEWAY_REQUEST_TIMEOUT).ifPresent(this::setRequestTimeout);
    environment.get(ENV_GATEWAY_CLUSTER_NAME).ifPresent(this::setClusterName);
    environment.get(ENV_GATEWAY_CLUSTER_MEMBER_ID).ifPresent(this::setMemberId);
    environment.get(ENV_GATEWAY_CLUSTER_HOST).ifPresent(this::setHost);
    environment.getInt(ENV_GATEWAY_CLUSTER_PORT).ifPresent(this::setPort);
  }

  public String getMemberId() {
    return memberId;
  }

  public ClusterCfg setMemberId(final String memberId) {
    this.memberId = memberId;
    return this;
  }

  public String getHost() {
    return host;
  }

  public ClusterCfg setHost(final String host) {
    this.host = host;
    return this;
  }

  public int getPort() {
    return port;
  }

  public ClusterCfg setPort(final int port) {
    this.port = port;
    return this;
  }

  public String getContactPoint() {
    return contactPoint;
  }

  public ClusterCfg setContactPoint(final String contactPoint) {
    this.contactPoint = contactPoint;
    return this;
  }

  public Duration getRequestTimeout() {
    return DurationUtil.parse(requestTimeout);
  }

  public ClusterCfg setRequestTimeout(final String requestTimeout) {
    // call parsing logic to provoke any exceptions that might occur during parsing
    DurationUtil.parse(requestTimeout);

    this.requestTimeout = requestTimeout;
    return this;
  }

  public String getClusterName() {
    return clusterName;
  }

  public ClusterCfg setClusterName(final String name) {
    clusterName = name;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(contactPoint, requestTimeout, clusterName, memberId, host, port);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterCfg that = (ClusterCfg) o;
    return port == that.port
        && Objects.equals(contactPoint, that.contactPoint)
        && Objects.equals(requestTimeout, that.requestTimeout)
        && Objects.equals(clusterName, that.clusterName)
        && Objects.equals(memberId, that.memberId)
        && Objects.equals(host, that.host);
  }

  @Override
  public String toString() {
    return "ClusterCfg{"
        + "contactPoint='"
        + contactPoint
        + '\''
        + ", requestTimeout='"
        + requestTimeout
        + '\''
        + ", clusterName='"
        + clusterName
        + '\''
        + ", memberId='"
        + memberId
        + '\''
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + '}';
  }
}
