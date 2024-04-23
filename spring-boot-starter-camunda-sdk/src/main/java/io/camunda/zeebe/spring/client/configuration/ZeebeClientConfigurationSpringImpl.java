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

import static io.camunda.zeebe.spring.client.configuration.PropertyUtil.*;
import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.*;
import static org.springframework.util.StringUtils.hasText;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.client.impl.util.Environment;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.PropertiesUtil;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.DefaultNoopAuthentication;
import io.camunda.zeebe.spring.common.auth.Product;
import io.grpc.ClientInterceptor;
import io.grpc.Status;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ZeebeClientConfigurationSpringImpl implements ZeebeClientConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeClientConfiguration.class);
  private final Map<String, Object> configCache = new HashMap<>();
  private final ZeebeClientConfigurationProperties properties;
  private final CamundaClientProperties camundaClientProperties;
  private final Authentication authentication;
  private final JsonMapper jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final ZeebeClientExecutorService zeebeClientExecutorService;

  @Autowired
  public ZeebeClientConfigurationSpringImpl(
      final ZeebeClientConfigurationProperties properties,
      final CamundaClientProperties camundaClientProperties,
      final Authentication authentication,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final ZeebeClientExecutorService zeebeClientExecutorService) {
    this.properties = properties;
    this.camundaClientProperties = camundaClientProperties;
    this.authentication = authentication;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.zeebeClientExecutorService = zeebeClientExecutorService;
  }

  @PostConstruct
  public void applyLegacy() {
    // make sure environment variables and other legacy config options are taken into account
    // (duplicate, also done by  qPostConstruct, whatever)
    properties.applyOverrides();
  }

  @Override
  public String getGatewayAddress() {
    return getOrLegacyOrDefault(
        "GatewayAddress",
        this::composeGatewayAddress,
        () -> PropertiesUtil.getZeebeGatewayAddress(properties),
        DEFAULT.getGatewayAddress(),
        configCache);
  }

  @Override
  public URI getRestAddress() {
    return URI.create(camundaClientProperties.getZeebe().getBaseUrl().toString());
  }

  @Override
  public URI getGrpcAddress() {
    return URI.create(camundaClientProperties.getZeebe().getGatewayUrl().toString());
  }

  @Override
  public String getDefaultTenantId() {
    return getOrLegacyOrDefault(
        "DefaultTenantId",
        prioritized(
            DEFAULT.getDefaultTenantId(),
            List.of(
                () -> camundaClientProperties.getTenantIds().get(0),
                () -> camundaClientProperties.getZeebe().getDefaults().getTenantIds().get(0))),
        properties::getDefaultTenantId,
        DEFAULT.getDefaultTenantId(),
        configCache);
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return getOrLegacyOrDefault(
        "DefaultJobWorkerTenantIds",
        prioritized(
            DEFAULT.getDefaultJobWorkerTenantIds(),
            List.of(
                camundaClientProperties::getTenantIds,
                () -> camundaClientProperties.getZeebe().getDefaults().getTenantIds())),
        properties::getDefaultJobWorkerTenantIds,
        DEFAULT.getDefaultJobWorkerTenantIds(),
        configCache);
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return getOrLegacyOrDefault(
        "NumJobWorkerExecutionThreads",
        () -> camundaClientProperties.getZeebe().getExecutionThreads(),
        () -> properties.getWorker().getThreads(),
        DEFAULT.getNumJobWorkerExecutionThreads(),
        configCache);
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return getOrLegacyOrDefault(
        "DefaultJobWorkerMaxJobsActive",
        () -> camundaClientProperties.getZeebe().getDefaults().getMaxJobsActive(),
        () -> properties.getWorker().getMaxJobsActive(),
        DEFAULT.getDefaultJobWorkerMaxJobsActive(),
        configCache);
  }

  @Override
  public String getDefaultJobWorkerName() {
    return getOrLegacyOrDefault(
        "DefaultJobWorkerName",
        () -> camundaClientProperties.getZeebe().getDefaults().getName(),
        () -> properties.getWorker().getDefaultName(),
        DEFAULT.getDefaultJobWorkerName(),
        configCache);
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return getOrLegacyOrDefault(
        "DefaultJobTimeout",
        () -> camundaClientProperties.getZeebe().getDefaults().getTimeout(),
        () -> properties.getJob().getTimeout(),
        DEFAULT.getDefaultJobTimeout(),
        configCache);
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return getOrLegacyOrDefault(
        "DefaultJobPollInterval",
        () -> camundaClientProperties.getZeebe().getDefaults().getPollInterval(),
        () -> properties.getJob().getPollInterval(),
        DEFAULT.getDefaultJobPollInterval(),
        configCache);
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return getOrLegacyOrDefault(
        "DefaultMessageTimeToLive",
        () -> camundaClientProperties.getZeebe().getMessageTimeToLive(),
        () -> properties.getMessage().getTimeToLive(),
        DEFAULT.getDefaultMessageTimeToLive(),
        configCache);
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return getOrLegacyOrDefault(
        "DefaultRequestTimeout",
        prioritized(
            DEFAULT.getDefaultRequestTimeout(),
            List.of(
                () -> camundaClientProperties.getZeebe().getRequestTimeout(),
                () -> camundaClientProperties.getZeebe().getDefaults().getRequestTimeout())),
        properties::getRequestTimeout,
        DEFAULT.getDefaultRequestTimeout(),
        configCache);
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return getOrLegacyOrDefault(
        "PlaintextConnectionEnabled",
        this::composePlaintext,
        () -> properties.getSecurity().isPlaintext(),
        DEFAULT.isPlaintextConnectionEnabled(),
        configCache);
  }

  @Override
  public String getCaCertificatePath() {
    return getOrLegacyOrDefault(
        "CaCertificatePath",
        () -> camundaClientProperties.getZeebe().getCaCertificatePath(),
        () -> properties.getSecurity().getCertPath(),
        DEFAULT.getCaCertificatePath(),
        configCache);
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    return getOrLegacyOrDefault(
        "CredentialsProvider",
        this::identityCredentialsProvider,
        this::legacyCredentialsProvider,
        null,
        configCache);
  }

  @Override
  public Duration getKeepAlive() {
    return getOrLegacyOrDefault(
        "KeepAlive",
        () -> camundaClientProperties.getZeebe().getKeepAlive(),
        () -> properties.getBroker().getKeepAlive(),
        DEFAULT.getKeepAlive(),
        configCache);
  }

  @Override
  public List<ClientInterceptor> getInterceptors() {
    return interceptors;
  }

  @Override
  public JsonMapper getJsonMapper() {
    return jsonMapper;
  }

  @Override
  public String getOverrideAuthority() {
    return getOrLegacyOrDefault(
        "OverrideAuthority",
        () -> camundaClientProperties.getZeebe().getOverrideAuthority(),
        () -> properties.getSecurity().getOverrideAuthority(),
        DEFAULT.getOverrideAuthority(),
        configCache);
  }

  @Override
  public int getMaxMessageSize() {
    return getOrLegacyOrDefault(
        "MaxMessageSize",
        () -> camundaClientProperties.getZeebe().getMaxMessageSize(),
        () -> properties.getMessage().getMaxMessageSize(),
        DEFAULT.getMaxMessageSize(),
        configCache);
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return zeebeClientExecutorService.get();
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
    return getOrLegacyOrDefault(
        "ownsJobWorkerExecutor",
        zeebeClientExecutorService::isOwnedByZeebeClient,
        properties::ownsJobWorkerExecutor,
        DEFAULT.ownsJobWorkerExecutor(),
        configCache);
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return getOrLegacyOrDefault(
        "DefaultJobWorkerStreamEnabled",
        () -> camundaClientProperties.getZeebe().getDefaults().getStreamEnabled(),
        properties::getDefaultJobWorkerStreamEnabled,
        DEFAULT.getDefaultJobWorkerStreamEnabled(),
        configCache);
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return false;
  }

  @Override
  public boolean preferRestOverGrpc() {
    return camundaClientProperties.getZeebe().isPreferRestOverGrpc();
  }

  private CredentialsProvider identityCredentialsProvider() {
    if (authentication instanceof DefaultNoopAuthentication) {
      return null;
    }
    return new IdentityCredentialsProvider(authentication);
  }

  private String composeGatewayAddress() {
    final URL gatewayUrl = camundaClientProperties.getZeebe().getGatewayUrl();
    final int port = gatewayUrl.getPort();
    final int defaultPort = gatewayUrl.getDefaultPort();
    final String host = gatewayUrl.getHost();

    if (port != -1) {
      return composeAddressWithPort(host, port, "Gateway port is set");
    } else if (defaultPort != -1) {
      return composeAddressWithPort(host, defaultPort, "Gateway port has default");
    } else {
      // do not use any port
      LOG.debug("Gateway cannot be determined, address will be '{}'", host);
      return host;
    }
  }

  private String composeAddressWithPort(
      final String host, final int port, final String debugMessage) {
    final String gatewayAddress = host + ":" + port;
    LOG.debug(debugMessage + ", address will be '{}'", gatewayAddress);
    return gatewayAddress;
  }

  private boolean composePlaintext() {
    final String protocol = camundaClientProperties.getZeebe().getGatewayUrl().getProtocol();
    return switch (protocol) {
      case "http" -> true;
      case "https" -> false;
      default ->
          throw new IllegalStateException(
              String.format("Unrecognized zeebe protocol '%s'", protocol));
    };
  }

  private CredentialsProvider legacyCredentialsProvider() {
    if (hasText(properties.getCloud().getClientId())
        && hasText(properties.getCloud().getClientSecret())) {
      return CredentialsProvider.newCredentialsProviderBuilder()
          .clientId(properties.getCloud().getClientId())
          .clientSecret(properties.getCloud().getClientSecret())
          .audience(properties.getCloud().getAudience())
          .scope(properties.getCloud().getScope())
          .authorizationServerUrl(properties.getCloud().getAuthUrl())
          .credentialsCachePath(properties.getCloud().getCredentialsCachePath())
          .build();
    } else if (Environment.system().get("ZEEBE_CLIENT_ID") != null
        && Environment.system().get("ZEEBE_CLIENT_SECRET") != null) {
      // Copied from ZeebeClientBuilderImpl
      final OAuthCredentialsProviderBuilder builder =
          CredentialsProvider.newCredentialsProviderBuilder();
      final int separatorIndex = properties.getBroker().getGatewayAddress().lastIndexOf(58); // ":"
      if (separatorIndex > 0) {
        builder.audience(properties.getBroker().getGatewayAddress().substring(0, separatorIndex));
      }
      return builder.build();
    }
    return null;
  }

  @Override
  public String toString() {
    return "ZeebeClientConfiguration{"
        + "properties="
        + properties
        + ", camundaClientProperties="
        + camundaClientProperties
        + ", authentication="
        + authentication
        + ", jsonMapper="
        + jsonMapper
        + ", interceptors="
        + interceptors
        + ", zeebeClientExecutorService="
        + zeebeClientExecutorService
        + '}';
  }

  public static class IdentityCredentialsProvider implements CredentialsProvider {
    private final Authentication authentication;

    public IdentityCredentialsProvider(final Authentication authentication) {
      this.authentication = authentication;
    }

    @Override
    public void applyCredentials(final CredentialsApplier applier) {
      final Map.Entry<String, String> authHeader = authentication.getTokenHeader(Product.ZEEBE);
      applier.put(authHeader.getKey(), authHeader.getValue());
    }

    @Override
    public boolean shouldRetryRequest(final StatusCode statusCode) {
      return statusCode.code() == Status.Code.DEADLINE_EXCEEDED.value();
    }
  }
}
