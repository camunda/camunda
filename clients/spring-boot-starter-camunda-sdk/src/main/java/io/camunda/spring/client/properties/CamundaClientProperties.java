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

import static io.camunda.client.impl.CamundaClientBuilderImpl.*;
import static io.camunda.client.impl.util.ClientPropertiesValidationUtils.checkIfUriIsAbsolute;

import io.camunda.client.api.command.CommandWithTenantStep;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("camunda.client")
public class CamundaClientProperties {

  /** Whether the Camunda client is enabled. If disabled, no bean is created. */
  private boolean enabled = true;

  /**
   * The client mode to be used. If not set, `saas` mode will be detected based on the presence of a
   * `camunda.client.cloud.cluster-id`.
   */
  private ClientMode mode;

  @NestedConfigurationProperty
  private CamundaClientCloudProperties cloud = new CamundaClientCloudProperties();

  @NestedConfigurationProperty
  private CamundaClientAuthProperties auth = new CamundaClientAuthProperties();

  /** The number of threads for invocation of job workers. */
  private Integer executionThreads = DEFAULT_NUM_JOB_WORKER_EXECUTION_THREADS;

  /** The time-to-live which is used when none is provided for a message. */
  private Duration messageTimeToLive = DEFAULT_MESSAGE_TTL;

  /**
   * A custom maxMessageSize allows the client to receive larger or smaller responses from Camunda.
   * Technically, it specifies the maxInboundMessageSize of the gRPC channel.
   */
  private DataSize maxMessageSize = DataSize.ofBytes(DEFAULT_MAX_MESSAGE_SIZE);

  /**
   * A custom maxMetadataSize allows the client to receive larger or smaller response headers from
   * Camunda. Technically, it specifies the maxInboundMetadataSize of the gRPC channel.
   */
  private DataSize maxMetadataSize = DataSize.ofBytes(DEFAULT_MAX_METADATA_SIZE);

  /** Path to a root CA certificate to be used instead of the certificate in the default store. */
  private String caCertificatePath;

  /** Time interval between keep alive messages sent to the gateway. */
  private Duration keepAlive = DEFAULT_KEEP_ALIVE;

  /**
   * Overrides the authority used with TLS virtual hosting. Specifically, to override hostname
   * verification in the TLS handshake. It does not change what host is actually connected to.
   */
  private String overrideAuthority;

  @NestedConfigurationProperty
  private CamundaClientWorkerProperties worker = new CamundaClientWorkerProperties();

  /**
   * If true, will prefer to use REST over gRPC for calls which can be done over both REST and gRPC.
   */
  private boolean preferRestOverGrpc = DEFAULT_PREFER_REST_OVER_GRPC;

  /**
   * The gRPC address of Camunda that the client can connect to. The address must be an absolute
   * URL, including the scheme. An alternative default is set by both `camunda.client.mode`.
   */
  private URI grpcAddress = DEFAULT_GRPC_ADDRESS;

  /**
   * The REST API address of Camunda that the client can connect to. The address must be an absolute
   * URL, including the scheme. An alternative default is set by both`camunda.client.mode`.
   */
  private URI restAddress = DEFAULT_REST_ADDRESS;

  @NestedConfigurationProperty
  private CamundaClientDeploymentProperties deployment = new CamundaClientDeploymentProperties();

  /**
   * The tenant identifier which is used for tenant-aware commands when no tenant identifier is set.
   */
  private String tenantId = CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

  /** The request timeout used if not overridden by the command. */
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

  /**
   * The request timeout client offset is used in commands where the request timeout is also passed
   * to the server. This ensures that the client timeout does not occur before the server timeout.
   * The client-side timeout for these commands is calculated as the sum of request timeout plus
   * offset.
   */
  private Duration requestTimeoutOffset = DEFAULT_REQUEST_TIMEOUT_OFFSET;

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

  public Duration getRequestTimeoutOffset() {
    return requestTimeoutOffset;
  }

  public void setRequestTimeoutOffset(final Duration requestTimeoutOffset) {
    this.requestTimeoutOffset = requestTimeoutOffset;
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

  public boolean getPreferRestOverGrpc() {
    return preferRestOverGrpc;
  }

  public void setPreferRestOverGrpc(final boolean preferRestOverGrpc) {
    this.preferRestOverGrpc = preferRestOverGrpc;
  }

  public URI getGrpcAddress() {
    return grpcAddress;
  }

  public void setGrpcAddress(final URI grpcAddress) {
    checkIfUriIsAbsolute(grpcAddress, "grpcAddress");
    this.grpcAddress = grpcAddress;
  }

  public URI getRestAddress() {
    return restAddress;
  }

  public void setRestAddress(final URI restAddress) {
    checkIfUriIsAbsolute(restAddress, "restAddress");
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

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
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
