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
package io.camunda.spring.client.properties;

import static io.camunda.client.impl.util.ClientPropertiesValidationUtils.validateGrpcAddress;
import static io.camunda.client.impl.util.ClientPropertiesValidationUtils.validateRestAddress;

import io.camunda.spring.client.properties.common.IdentityProperties;
import io.camunda.spring.client.properties.common.ZeebeClientProperties;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("camunda.client")
public class CamundaClientProperties {
  private Boolean enabled;
  private ClientMode mode;

  @Deprecated(forRemoval = true, since = "8.8")
  private String clusterId;

  @Deprecated(forRemoval = true, since = "8.8")
  private String region;

  @NestedConfigurationProperty
  private CamundaClientCloudProperties cloud = new CamundaClientCloudProperties();

  @Deprecated(forRemoval = true, since = "8.8")
  @NestedConfigurationProperty
  private List<String> tenantIds = new ArrayList<>();

  @NestedConfigurationProperty
  private CamundaClientAuthProperties auth = new CamundaClientAuthProperties();

  @NestedConfigurationProperty private IdentityProperties identity = new IdentityProperties();

  @NestedConfigurationProperty
  @Deprecated(forRemoval = true, since = "8.8")
  private ZeebeClientProperties zeebe = new ZeebeClientProperties();

  private Integer executionThreads;
  private Duration messageTimeToLive;
  private DataSize maxMessageSize;
  private DataSize maxMetadataSize;
  private String caCertificatePath;
  private Duration keepAlive;
  private String overrideAuthority;

  @NestedConfigurationProperty
  private CamundaClientWorkerProperties worker = new CamundaClientWorkerProperties();

  private Boolean preferRestOverGrpc;
  private URI grpcAddress;
  private URI restAddress;

  @NestedConfigurationProperty
  private CamundaClientDeploymentProperties deployment = new CamundaClientDeploymentProperties();

  private String tenantId;
  private Duration requestTimeout;

  public CamundaClientCloudProperties getCloud() {
    return cloud;
  }

  public void setCloud(final CamundaClientCloudProperties cloud) {
    this.cloud = cloud;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public CamundaClientWorkerProperties getWorker() {
    return worker;
  }

  public void setWorker(final CamundaClientWorkerProperties worker) {
    this.worker = worker;
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
    validateGrpcAddress(grpcAddress);
    this.grpcAddress = grpcAddress;
  }

  public URI getRestAddress() {
    return restAddress;
  }

  public void setRestAddress(final URI restAddress) {
    validateRestAddress(restAddress);
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

  @Deprecated(forRemoval = true, since = "8.8")
  @DeprecatedConfigurationProperty(replacement = "camunda.client")
  public ZeebeClientProperties getZeebe() {
    return zeebe;
  }

  @Deprecated
  public void setZeebe(final ZeebeClientProperties zeebe) {
    this.zeebe = zeebe;
  }

  @Deprecated(forRemoval = true, since = "8.8")
  @DeprecatedConfigurationProperty
  public IdentityProperties getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityProperties identity) {
    this.identity = identity;
  }

  @Deprecated(forRemoval = true, since = "8.8")
  @DeprecatedConfigurationProperty(replacement = "camunda.client.worker.defaults.tenant-ids")
  public List<String> getTenantIds() {
    return tenantIds;
  }

  @Deprecated(forRemoval = true, since = "8.8")
  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  @Deprecated(forRemoval = true, since = "8.8")
  @DeprecatedConfigurationProperty(replacement = "camunda.client.cloud.cluster-id")
  public String getClusterId() {
    return clusterId;
  }

  @Deprecated(forRemoval = true)
  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  @Deprecated(forRemoval = true, since = "8.8")
  @DeprecatedConfigurationProperty(replacement = "camunda.client.cloud.region")
  public String getRegion() {
    return region;
  }

  @Deprecated(forRemoval = true, since = "8.8")
  public void setRegion(final String region) {
    this.region = region;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public String toString() {
    return "CamundaClientProperties{"
        + "enabled="
        + enabled
        + ", mode="
        + mode
        + ", cloud="
        + cloud
        + ", auth="
        + auth
        + ", identity="
        + identity
        + ", executionThreads="
        + executionThreads
        + ", messageTimeToLive="
        + messageTimeToLive
        + ", maxMessageSize="
        + maxMessageSize
        + ", maxMetadataSize="
        + maxMetadataSize
        + ", caCertificatePath='"
        + caCertificatePath
        + '\''
        + ", keepAlive="
        + keepAlive
        + ", overrideAuthority='"
        + overrideAuthority
        + '\''
        + ", worker="
        + worker
        + ", preferRestOverGrpc="
        + preferRestOverGrpc
        + ", grpcAddress="
        + grpcAddress
        + ", restAddress="
        + restAddress
        + ", deployment="
        + deployment
        + ", tenantId='"
        + tenantId
        + '\''
        + ", requestTimeout="
        + requestTimeout
        + '}';
  }

  public enum ClientMode {
    selfManaged,
    saas
  }
}
