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
package io.camunda.client.spring.properties;

import static io.camunda.client.impl.CamundaClientBuilderImpl.*;
import static io.camunda.client.impl.util.ClientPropertiesValidationUtils.checkIfUriIsAbsolute;

import io.camunda.client.api.command.CommandWithTenantStep;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for a single Camunda client instance. Used within {@link
 * MultiCamundaClientProperties} to configure multiple named clients.
 *
 * <p>This class mirrors the properties available in {@link CamundaClientProperties} but is designed
 * for use in a multi-client configuration scenario.
 */
public class CamundaClientConfigurationProperties {

  /** Whether this client should be marked as the primary bean. */
  private boolean primary = false;

  /** Enable or disable the Camunda client. If disabled, the client bean is not created. */
  private boolean enabled = true;

  /**
   * The client mode to use. If not set, `saas` mode is detected based on the presence of a cloud
   * cluster-id.
   */
  private CamundaClientProperties.ClientMode mode;

  @NestedConfigurationProperty
  private CamundaClientCloudProperties cloud = new CamundaClientCloudProperties();

  @NestedConfigurationProperty
  private CamundaClientAuthProperties auth = new CamundaClientAuthProperties();

  /** The number of threads for invocation of job workers. */
  private Integer executionThreads = DEFAULT_NUM_JOB_WORKER_EXECUTION_THREADS;

  /** The default time-to-live for a message when no value is provided. */
  private Duration messageTimeToLive = DEFAULT_MESSAGE_TTL;

  /**
   * A custom `maxMessageSize` sets the maximum inbound message size the client can receive from
   * Camunda.
   */
  private DataSize maxMessageSize = DataSize.ofBytes(DEFAULT_MAX_MESSAGE_SIZE);

  /**
   * A custom `maxMetadataSize` sets the maximum inbound metadata size the client can receive from
   * Camunda.
   */
  private DataSize maxMetadataSize = DataSize.ofBytes(DEFAULT_MAX_METADATA_SIZE);

  /**
   * The path to a root Certificate Authority (CA) certificate to use instead of the certificate in
   * the default store.
   */
  private String caCertificatePath;

  /** The time interval between keep-alive messages sent to the gateway. */
  private Duration keepAlive = DEFAULT_KEEP_ALIVE;

  /**
   * Overrides the authority used with TLS virtual hosting to change hostname verification during
   * the TLS handshake.
   */
  private String overrideAuthority;

  @NestedConfigurationProperty
  private CamundaClientWorkerProperties worker = new CamundaClientWorkerProperties();

  /** If `true`, prefers REST over gRPC for operations supported by both protocols. */
  private boolean preferRestOverGrpc = DEFAULT_PREFER_REST_OVER_GRPC;

  /** The gRPC address of Camunda that the client can connect to. */
  private URI grpcAddress = DEFAULT_GRPC_ADDRESS;

  /** The REST API address of the Camunda instance that the client can connect to. */
  private URI restAddress = DEFAULT_REST_ADDRESS;

  @NestedConfigurationProperty
  private CamundaClientDeploymentProperties deployment = new CamundaClientDeploymentProperties();

  /** The tenant ID used for tenant-aware commands when no tenant ID is set. */
  private String tenantId = CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

  /** The request timeout to use when not overridden by a specific command. */
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

  /**
   * The request timeout client offset applies to commands that also pass the request timeout to the
   * server.
   */
  private Duration requestTimeoutOffset = DEFAULT_REQUEST_TIMEOUT_OFFSET;

  /** The maximum number of concurrent HTTP connections the client can open. */
  private int maxHttpConnections = DEFAULT_MAX_HTTP_CONNECTIONS;

  // Getters and Setters

  public boolean isPrimary() {
    return primary;
  }

  public void setPrimary(final boolean primary) {
    this.primary = primary;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public CamundaClientProperties.ClientMode getMode() {
    return mode;
  }

  public void setMode(final CamundaClientProperties.ClientMode mode) {
    this.mode = mode;
  }

  public CamundaClientCloudProperties getCloud() {
    return cloud;
  }

  public void setCloud(final CamundaClientCloudProperties cloud) {
    this.cloud = cloud;
  }

  public CamundaClientAuthProperties getAuth() {
    return auth;
  }

  public void setAuth(final CamundaClientAuthProperties auth) {
    this.auth = auth;
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

  public CamundaClientWorkerProperties getWorker() {
    return worker;
  }

  public void setWorker(final CamundaClientWorkerProperties worker) {
    this.worker = worker;
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

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
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

  public int getMaxHttpConnections() {
    return maxHttpConnections;
  }

  public void setMaxHttpConnections(final int maxHttpConnections) {
    this.maxHttpConnections = maxHttpConnections;
  }

  @Override
  public String toString() {
    return "CamundaClientConfigurationProperties{"
        + "primary="
        + primary
        + ", enabled="
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
        + ", maxHttpConnections="
        + maxHttpConnections
        + '}';
  }
}
