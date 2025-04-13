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
package io.camunda.spring.client.configuration;

import static java.util.Optional.ofNullable;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.grpc.ClientInterceptor;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

public class CamundaClientConfigurationImpl implements CamundaClientConfiguration {
  public static final CamundaClientBuilderImpl DEFAULT =
      (CamundaClientBuilderImpl) new CamundaClientBuilderImpl().withProperties(new Properties());
  private static final Logger LOG = LoggerFactory.getLogger(CamundaClientConfigurationImpl.class);
  private final CamundaClientProperties camundaClientProperties;
  private final JsonMapper jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;
  private final CamundaClientExecutorService zeebeClientExecutorService;
  private final CredentialsProvider credentialsProvider;

  public CamundaClientConfigurationImpl(
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService zeebeClientExecutorService,
      final CredentialsProvider credentialsProvider) {
    this.camundaClientProperties = camundaClientProperties;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.chainHandlers = chainHandlers;
    this.zeebeClientExecutorService = zeebeClientExecutorService;
    this.credentialsProvider = credentialsProvider;
  }

  private static <T> T propertyOrDefault(final T property, final T defaultValue) {
    if (property == null) {
      return defaultValue;
    }
    return property;
  }

  @Override
  public String getGatewayAddress() {
    return propertyOrDefault(composeGatewayAddress(), DEFAULT.getGatewayAddress());
  }

  @Override
  public URI getRestAddress() {
    return propertyOrDefault(camundaClientProperties.getRestAddress(), DEFAULT.getRestAddress());
  }

  @Override
  public URI getGrpcAddress() {
    return propertyOrDefault(camundaClientProperties.getGrpcAddress(), DEFAULT.getGrpcAddress());
  }

  @Override
  public String getDefaultTenantId() {
    return propertyOrDefault(camundaClientProperties.getTenantId(), DEFAULT.getDefaultTenantId());
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return propertyOrDefault(
        camundaClientProperties.getWorker().getDefaults().getTenantIds(),
        DEFAULT.getDefaultJobWorkerTenantIds());
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return propertyOrDefault(
        camundaClientProperties.getExecutionThreads(), DEFAULT.getNumJobWorkerExecutionThreads());
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return propertyOrDefault(
        camundaClientProperties.getWorker().getDefaults().getMaxJobsActive(),
        DEFAULT.getDefaultJobWorkerMaxJobsActive());
  }

  @Override
  public String getDefaultJobWorkerName() {
    return propertyOrDefault(
        camundaClientProperties.getWorker().getDefaults().getName(),
        DEFAULT.getDefaultJobWorkerName());
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return propertyOrDefault(
        camundaClientProperties.getWorker().getDefaults().getTimeout(),
        DEFAULT.getDefaultJobTimeout());
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return propertyOrDefault(
        camundaClientProperties.getWorker().getDefaults().getPollInterval(),
        DEFAULT.getDefaultJobPollInterval());
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return propertyOrDefault(
        camundaClientProperties.getMessageTimeToLive(), DEFAULT.getDefaultMessageTimeToLive());
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return propertyOrDefault(
        camundaClientProperties.getRequestTimeout(), DEFAULT.getDefaultRequestTimeout());
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return propertyOrDefault(composePlaintext(), DEFAULT.isPlaintextConnectionEnabled());
  }

  @Override
  public String getCaCertificatePath() {
    return propertyOrDefault(
        camundaClientProperties.getCaCertificatePath(), DEFAULT.getCaCertificatePath());
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  @Override
  public Duration getKeepAlive() {
    return propertyOrDefault(camundaClientProperties.getKeepAlive(), DEFAULT.getKeepAlive());
  }

  @Override
  public List<ClientInterceptor> getInterceptors() {
    return interceptors;
  }

  @Override
  public List<AsyncExecChainHandler> getChainHandlers() {
    return chainHandlers;
  }

  @Override
  public JsonMapper getJsonMapper() {
    return jsonMapper;
  }

  @Override
  public String getOverrideAuthority() {
    return propertyOrDefault(
        camundaClientProperties.getOverrideAuthority(), DEFAULT.getOverrideAuthority());
  }

  @Override
  public int getMaxMessageSize() {
    return ofNullable(camundaClientProperties.getMaxMessageSize())
        .map(DataSize::toBytes)
        .map(Math::toIntExact)
        .orElse(DEFAULT.getMaxMessageSize());
  }

  @Override
  public int getMaxMetadataSize() {
    return ofNullable(camundaClientProperties.getMaxMetadataSize())
        .map(DataSize::toBytes)
        .map(Math::toIntExact)
        .orElse(DEFAULT.getMaxMetadataSize());
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return zeebeClientExecutorService.get();
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
    return zeebeClientExecutorService.isOwnedByCamundaClient();
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return propertyOrDefault(
        camundaClientProperties.getWorker().getDefaults().getStreamEnabled(),
        DEFAULT.getDefaultJobWorkerStreamEnabled());
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return false;
  }

  @Override
  public boolean preferRestOverGrpc() {
    return propertyOrDefault(
        camundaClientProperties.getPreferRestOverGrpc(), DEFAULT.preferRestOverGrpc());
  }

  private String composeGatewayAddress() {
    final URI gatewayUrl = getGrpcAddress();
    final int port = gatewayUrl.getPort();
    final String host = gatewayUrl.getHost();

    // port is set
    if (port != -1) {
      return composeAddressWithPort(host, port, "Gateway port is set");
    }

    // port is not set, attempting to use default
    int defaultPort;
    try {
      defaultPort = gatewayUrl.toURL().getDefaultPort();
    } catch (final MalformedURLException e) {
      LOG.warn("Invalid gateway url: {}", gatewayUrl);
      // could not get a default port, setting it to -1 and moving to the next statement
      defaultPort = -1;
    }
    if (defaultPort != -1) {
      return composeAddressWithPort(host, defaultPort, "Gateway port has default");
    }

    // do not use any port
    LOG.debug("Gateway cannot be determined, address will be '{}'", host);
    return host;
  }

  private String composeAddressWithPort(
      final String host, final int port, final String debugMessage) {
    final String gatewayAddress = host + ":" + port;
    LOG.debug(debugMessage + ", address will be '{}'", gatewayAddress);
    return gatewayAddress;
  }

  private boolean composePlaintext() {
    final String protocol = getGrpcAddress().getScheme();
    return switch (protocol) {
      case "http", "grpc" -> true;
      case "https", "grpcs" -> false;
      default ->
          throw new IllegalStateException(
              String.format("Unrecognized zeebe protocol '%s'", protocol));
    };
  }

  @Override
  public String toString() {
    return "CamundaClientConfigurationImpl{"
        + "camundaClientProperties="
        + camundaClientProperties
        + ", jsonMapper="
        + jsonMapper
        + ", interceptors="
        + interceptors
        + ", chainHandlers="
        + chainHandlers
        + ", zeebeClientExecutorService="
        + zeebeClientExecutorService
        + '}';
  }
}
