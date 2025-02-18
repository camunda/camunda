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
package io.camunda.spring.client.properties.common;

import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.properties.CamundaClientDeploymentProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

@Deprecated(forRemoval = true, since = "8.8")
public class ZeebeClientProperties extends ApiProperties {
  private Integer executionThreads;
  private Duration messageTimeToLive;
  private DataSize maxMessageSize;
  private DataSize maxMetadataSize;
  private Duration requestTimeout;
  private String caCertificatePath;
  private Duration keepAlive;
  private String overrideAuthority;
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/spring/client/properties/common/ZeebeClientProperties.java
  @NestedConfigurationProperty private JobWorkerValue defaults;
  @NestedConfigurationProperty private Map<String, JobWorkerValue> override;
  private boolean preferRestOverGrpc;
=======
  @NestedConfigurationProperty private ZeebeWorkerValue defaults = new ZeebeWorkerValue();
  @NestedConfigurationProperty private Map<String, ZeebeWorkerValue> override = new HashMap<>();
  private Boolean preferRestOverGrpc;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/properties/common/ZeebeClientProperties.java
  private URI grpcAddress;
  private URI restAddress;
  @NestedConfigurationProperty private CamundaClientDeploymentProperties deployment;

  @DeprecatedConfigurationProperty(replacement = "camunda.client.worker")
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

  @DeprecatedConfigurationProperty(replacement = "camunda.client.worker.defaults.request-timeout")
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

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/spring/client/properties/common/ZeebeClientProperties.java
  @DeprecatedConfigurationProperty(replacement = "camunda.client.max-message-size")
  public Integer getMaxMessageSize() {
=======
  public DataSize getMaxMessageSize() {
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/properties/common/ZeebeClientProperties.java
    return maxMessageSize;
  }

  public void setMaxMessageSize(final DataSize maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/spring/client/properties/common/ZeebeClientProperties.java
  @DeprecatedConfigurationProperty(replacement = "camunda.client.max-metadata-size")
  public Integer getMaxMetadataSize() {
=======
  public DataSize getMaxMetadataSize() {
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/properties/common/ZeebeClientProperties.java
    return maxMetadataSize;
  }

  public void setMaxMetadataSize(final DataSize maxMetadataSize) {
    this.maxMetadataSize = maxMetadataSize;
  }

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/spring/client/properties/common/ZeebeClientProperties.java
  @DeprecatedConfigurationProperty(replacement = "camunda.client.prefer-rest-over-grpc")
  public boolean isPreferRestOverGrpc() {
=======
  public Boolean getPreferRestOverGrpc() {
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/properties/common/ZeebeClientProperties.java
    return preferRestOverGrpc;
  }

  public void setPreferRestOverGrpc(final Boolean preferRestOverGrpc) {
    this.preferRestOverGrpc = preferRestOverGrpc;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.grpc-address")
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

  @DeprecatedConfigurationProperty(replacement = "camunda.client.rest-address")
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

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/spring/client/properties/common/ZeebeClientProperties.java
  @DeprecatedConfigurationProperty(replacement = "camunda.client.deployment")
  public CamundaClientDeploymentProperties getDeployment() {
    return deployment;
  }

  public void setDeployment(final CamundaClientDeploymentProperties deployment) {
    this.deployment = deployment;
=======
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
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/properties/common/ZeebeClientProperties.java
  }
}
