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
package io.camunda.zeebe.spring.client.properties.common;

import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

public class ZeebeClientProperties extends ApiProperties {
  private Integer executionThreads;
  private Duration messageTimeToLive;
  private DataSize maxMessageSize;
  private DataSize maxMetadataSize;
  private Duration requestTimeout;
  private String caCertificatePath;
  private Duration keepAlive;
  private String overrideAuthority;
  @NestedConfigurationProperty private ZeebeWorkerValue defaults = new ZeebeWorkerValue();
  @NestedConfigurationProperty private Map<String, ZeebeWorkerValue> override = new HashMap<>();
  private Boolean preferRestOverGrpc;
  private URI grpcAddress;
  private URI restAddress;

  public ZeebeWorkerValue getDefaults() {
    return defaults;
  }

  public void setDefaults(final ZeebeWorkerValue defaults) {
    this.defaults = defaults;
  }

  public Map<String, ZeebeWorkerValue> getOverride() {
    return override;
  }

  public void setOverride(final Map<String, ZeebeWorkerValue> override) {
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

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
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

  public DataSize getMaxMessageSize() {
    return maxMessageSize;
  }

  public void setMaxMessageSize(final DataSize maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  public DataSize getMaxMetadataSize() {
    return maxMetadataSize;
  }

  public void setMaxMetadataSize(final DataSize maxMetadataSize) {
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
    /*
     * Validates that the provided gRPC address is an absolute URI.
     *
     * <p>We use {@code URI.getHost() == null} to check for absolute URIs because:
     * <ul>
     *   <li>For absolute URIs (with a scheme) (e.g., "https://example.com"), {@code URI.getHost()} returns the hostname (e.g., "example.com").</li>
     *   <li>For relative URIs (without a scheme) (e.g., "example.com"), {@code URI.getHost()} returns {@code null}.</li>
     * </ul>
     */
    if (grpcAddress != null && grpcAddress.getHost() == null) {
      throw new IllegalArgumentException("grpcAddress must be an absolute URI");
    }
    this.grpcAddress = grpcAddress;
  }

  public URI getRestAddress() {
    return restAddress;
  }

  public void setRestAddress(final URI restAddress) {
    /*
     * Validates that the provided rest address is an absolute URI.
     *
     * <p>We use {@code URI.getHost() == null} to check for absolute URIs because:
     * <ul>
     *   <li>For absolute URIs (with a scheme) (e.g., "https://example.com"), {@code URI.getHost()} returns the hostname (e.g., "example.com").</li>
     *   <li>For relative URIs (without a scheme) (e.g., "example.com"), {@code URI.getHost()} returns {@code null}.</li>
     * </ul>
     */
    if (restAddress != null && restAddress.getHost() == null) {
      throw new IllegalArgumentException("restAddress must be an absolute URI");
    }
    this.restAddress = restAddress;
  }

  @Override
  public String toString() {
    return "ZeebeClientProperties{"
        + "executionThreads="
        + executionThreads
        + ", messageTimeToLive="
        + messageTimeToLive
        + ", maxMessageSize="
        + maxMessageSize
        + ", maxMetadataSize="
        + maxMetadataSize
        + ", requestTimeout="
        + requestTimeout
        + ", caCertificatePath='"
        + caCertificatePath
        + '\''
        + ", keepAlive="
        + keepAlive
        + ", overrideAuthority='"
        + overrideAuthority
        + '\''
        + ", defaults="
        + defaults
        + ", override="
        + override
        + ", preferRestOverGrpc="
        + preferRestOverGrpc
        + ", grpcAddress="
        + grpcAddress
        + ", restAddress="
        + restAddress
        + "} "
        + super.toString();
  }
}
