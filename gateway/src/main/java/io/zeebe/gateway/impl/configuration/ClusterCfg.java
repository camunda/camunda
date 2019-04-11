/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_HOST;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_MEMBER_ID;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_NAME;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_PORT;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CONTACT_POINT_HOST;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CONTACT_POINT_PORT;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_REQUEST_TIMEOUT;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_TRANSPORT_BUFFER_SIZE;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CLUSTER_HOST;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CLUSTER_MEMBER_ID;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CLUSTER_NAME;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CLUSTER_PORT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_CONTACT_POINT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_REQUEST_TIMEOUT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_TRANSPORT_BUFFER;

import io.zeebe.util.ByteValue;
import io.zeebe.util.DurationUtil;
import io.zeebe.util.Environment;
import java.time.Duration;
import java.util.Objects;

public class ClusterCfg {
  private String contactPoint = DEFAULT_CONTACT_POINT_HOST + ":" + DEFAULT_CONTACT_POINT_PORT;
  private String transportBuffer = DEFAULT_TRANSPORT_BUFFER_SIZE;
  private String requestTimeout = DEFAULT_REQUEST_TIMEOUT;
  private String clusterName = DEFAULT_CLUSTER_NAME;
  private String memberId = DEFAULT_CLUSTER_MEMBER_ID;
  private String host = DEFAULT_CLUSTER_HOST;
  private int port = DEFAULT_CLUSTER_PORT;

  public void init(Environment environment) {
    environment
        .get(ENV_GATEWAY_CONTACT_POINT)
        .map(v -> v.contains(":") ? v : v + ":" + DEFAULT_CONTACT_POINT_PORT)
        .ifPresent(this::setContactPoint);
    environment.get(ENV_GATEWAY_TRANSPORT_BUFFER).ifPresent(this::setTransportBuffer);
    environment.get(ENV_GATEWAY_REQUEST_TIMEOUT).ifPresent(this::setRequestTimeout);
    environment.get(ENV_GATEWAY_CLUSTER_NAME).ifPresent(this::setClusterName);
    environment.get(ENV_GATEWAY_CLUSTER_MEMBER_ID).ifPresent(this::setMemberId);
    environment.get(ENV_GATEWAY_CLUSTER_HOST).ifPresent(this::setHost);
    environment.getInt(ENV_GATEWAY_CLUSTER_PORT).ifPresent(this::setPort);
  }

  public String getMemberId() {
    return memberId;
  }

  public ClusterCfg setMemberId(String memberId) {
    this.memberId = memberId;
    return this;
  }

  public String getHost() {
    return host;
  }

  public ClusterCfg setHost(String host) {
    this.host = host;
    return this;
  }

  public int getPort() {
    return port;
  }

  public ClusterCfg setPort(int port) {
    this.port = port;
    return this;
  }

  public String getContactPoint() {
    return contactPoint;
  }

  public ClusterCfg setContactPoint(String contactPoint) {
    this.contactPoint = contactPoint;
    return this;
  }

  public ByteValue getTransportBuffer() {
    return new ByteValue(transportBuffer);
  }

  public ClusterCfg setTransportBuffer(String transportBuffer) {
    this.transportBuffer = transportBuffer;
    return this;
  }

  public Duration getRequestTimeout() {
    return DurationUtil.parse(requestTimeout);
  }

  public ClusterCfg setRequestTimeout(String requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  public String getClusterName() {
    return clusterName;
  }

  public ClusterCfg setClusterName(String name) {
    this.clusterName = name;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterCfg that = (ClusterCfg) o;
    return Objects.equals(contactPoint, that.contactPoint)
        && Objects.equals(transportBuffer, that.transportBuffer)
        && Objects.equals(requestTimeout, that.requestTimeout)
        && Objects.equals(clusterName, that.clusterName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(contactPoint, transportBuffer, requestTimeout, clusterName);
  }

  @Override
  public String toString() {
    return "ClusterCfg{"
        + "contactPoint='"
        + contactPoint
        + '\''
        + ", transportBuffer='"
        + transportBuffer
        + '\''
        + ", requestTimeout='"
        + requestTimeout
        + '\''
        + ", clusterName='"
        + clusterName
        + '\''
        + '}';
  }
}
