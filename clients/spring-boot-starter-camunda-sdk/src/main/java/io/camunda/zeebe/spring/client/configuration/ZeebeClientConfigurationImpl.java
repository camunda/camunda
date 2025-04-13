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
package io.camunda.zeebe.spring.client.configuration;

import static java.util.Optional.ofNullable;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.unit.DataSize;

public class ZeebeClientConfigurationImpl implements ZeebeClientConfiguration {
  public static final ZeebeClientBuilderImpl DEFAULT =
      (ZeebeClientBuilderImpl) new ZeebeClientBuilderImpl().withProperties(new Properties());

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeClientConfigurationImpl.class);
  private final CamundaClientProperties camundaClientProperties;
  private final JsonMapper jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;
  private final ZeebeClientExecutorService zeebeClientExecutorService;
  private final CredentialsProvider credentialsProvider;
  private final String gatewayAddress;
  private final boolean plaintext;

  @Autowired
  public ZeebeClientConfigurationImpl(
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final ZeebeClientExecutorService zeebeClientExecutorService,
      final CredentialsProvider credentialsProvider) {
    this.camundaClientProperties = camundaClientProperties;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.chainHandlers = chainHandlers;
    this.zeebeClientExecutorService = zeebeClientExecutorService;
    this.credentialsProvider = credentialsProvider;
    gatewayAddress = composeGatewayAddress();
    plaintext = composePlaintext();
  }

  @Override
  public String getGatewayAddress() {
    return ofNullable(gatewayAddress).orElse(DEFAULT.getGatewayAddress());
  }

  @Override
  public URI getRestAddress() {
    return ofNullable(camundaClientProperties.getZeebe().getRestAddress())
        .orElse(DEFAULT.getRestAddress());
  }

  @Override
  public URI getGrpcAddress() {
    return ofNullable(camundaClientProperties.getZeebe().getGrpcAddress())
        .orElse(DEFAULT.getGrpcAddress());
  }

  @Override
  public String getDefaultTenantId() {
    return ofNullable(camundaClientProperties.getTenantId()).orElse(DEFAULT.getDefaultTenantId());
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getTenantIds())
        .orElse(DEFAULT.getDefaultJobWorkerTenantIds());
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return ofNullable(camundaClientProperties.getZeebe().getExecutionThreads())
        .orElse(DEFAULT.getNumJobWorkerExecutionThreads());
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getMaxJobsActive())
        .orElse(DEFAULT.getDefaultJobWorkerMaxJobsActive());
  }

  @Override
  public String getDefaultJobWorkerName() {
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getName())
        .orElse(DEFAULT.getDefaultJobWorkerName());
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getTimeout())
        .orElse(DEFAULT.getDefaultJobTimeout());
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getPollInterval())
        .orElse(DEFAULT.getDefaultJobPollInterval());
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return ofNullable(camundaClientProperties.getZeebe().getMessageTimeToLive())
        .orElse(DEFAULT.getDefaultMessageTimeToLive());
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return ofNullable(camundaClientProperties.getZeebe().getRequestTimeout())
        .orElse(DEFAULT.getDefaultRequestTimeout());
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return plaintext;
  }

  @Override
  public String getCaCertificatePath() {
    return ofNullable(camundaClientProperties.getZeebe().getCaCertificatePath())
        .orElse(DEFAULT.getCaCertificatePath());
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  @Override
  public Duration getKeepAlive() {
    return ofNullable(camundaClientProperties.getZeebe().getKeepAlive())
        .orElse(DEFAULT.getKeepAlive());
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
    return ofNullable(camundaClientProperties.getZeebe().getOverrideAuthority())
        .orElse(DEFAULT.getOverrideAuthority());
  }

  @Override
  public int getMaxMessageSize() {
    return ofNullable(camundaClientProperties.getZeebe().getMaxMessageSize())
        .map(DataSize::toBytes)
        .map(Math::toIntExact)
        .orElse(DEFAULT.getMaxMessageSize());
  }

  @Override
  public int getMaxMetadataSize() {
    return ofNullable(camundaClientProperties.getZeebe().getMaxMetadataSize())
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
    return zeebeClientExecutorService.isOwnedByZeebeClient();
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getStreamEnabled())
        .orElse(DEFAULT.getDefaultJobWorkerStreamEnabled());
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return false;
  }

  @Override
  public boolean preferRestOverGrpc() {
    return ofNullable(camundaClientProperties.getZeebe().getPreferRestOverGrpc())
        .orElse(DEFAULT.preferRestOverGrpc());
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
    return "ZeebeClientConfigurationImpl{"
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
        + ", credentialsProvider="
        + credentialsProvider
        + '}';
  }
}
