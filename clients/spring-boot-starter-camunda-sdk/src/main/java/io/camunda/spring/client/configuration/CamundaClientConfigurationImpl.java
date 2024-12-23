/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.configuration;

import static io.camunda.spring.client.configuration.PropertyUtil.*;
import static io.camunda.spring.client.configuration.PropertyUtil.getProperty;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.impl.util.Environment;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientConfigurationProperties;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.spring.client.properties.PropertiesUtil;
import io.grpc.ClientInterceptor;
import jakarta.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class CamundaClientConfigurationImpl implements CamundaClientConfiguration {
  public static final CamundaClientBuilderImpl DEFAULT =
      (CamundaClientBuilderImpl) new CamundaClientBuilderImpl().withProperties(new Properties());
  private static final Logger LOG = LoggerFactory.getLogger(CamundaClientConfigurationImpl.class);
  private final Map<String, Object> configCache = new HashMap<>();
  private final CamundaClientConfigurationProperties properties;
  private final CamundaClientProperties camundaClientProperties;
  private final JsonMapper jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;
  private final CamundaClientExecutorService zeebeClientExecutorService;

  @Autowired
  public CamundaClientConfigurationImpl(
      final CamundaClientConfigurationProperties properties,
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService zeebeClientExecutorService) {
    this.properties = properties;
    this.camundaClientProperties = camundaClientProperties;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.chainHandlers = chainHandlers;
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
    return getProperty(
        "GatewayAddress",
        configCache,
        DEFAULT.getGatewayAddress(),
        this::composeGatewayAddress,
        () -> PropertiesUtil.getZeebeGatewayAddress(properties));
  }

  @Override
  public URI getRestAddress() {
    return getProperty(
        "RestAddress",
        configCache,
        DEFAULT.getRestAddress(),
        camundaClientProperties::getRestAddress,
        () -> camundaClientProperties.getZeebe().getRestAddress(),
        () -> properties.getBroker().getRestAddress());
  }

  @Override
  public URI getGrpcAddress() {
    return getProperty(
        "GrpcAddress",
        configCache,
        DEFAULT.getGrpcAddress(),
        camundaClientProperties::getGrpcAddress,
        () -> camundaClientProperties.getZeebe().getGrpcAddress(),
        properties::getGrpcAddress);
  }

  @Override
  public String getDefaultTenantId() {
    return getProperty(
        "DefaultTenantId",
        configCache,
        DEFAULT.getDefaultTenantId(),
        () -> camundaClientProperties.getDefaults().getTenantIds().get(0),
        () -> camundaClientProperties.getTenantIds().get(0),
        () -> camundaClientProperties.getZeebe().getDefaults().getTenantIds().get(0),
        properties::getDefaultTenantId);
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return getProperty(
        "DefaultJobWorkerTenantIds",
        configCache,
        DEFAULT.getDefaultJobWorkerTenantIds(),
        () -> camundaClientProperties.getDefaults().getTenantIds(),
        camundaClientProperties::getTenantIds,
        () -> camundaClientProperties.getZeebe().getDefaults().getTenantIds(),
        properties::getDefaultJobWorkerTenantIds);
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return getProperty(
        "NumJobWorkerExecutionThreads",
        configCache,
        DEFAULT.getNumJobWorkerExecutionThreads(),
        camundaClientProperties::getExecutionThreads,
        () -> camundaClientProperties.getZeebe().getExecutionThreads(),
        () -> properties.getWorker().getThreads());
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return getProperty(
        "DefaultJobWorkerMaxJobsActive",
        configCache,
        DEFAULT.getDefaultJobWorkerMaxJobsActive(),
        () -> camundaClientProperties.getDefaults().getMaxJobsActive(),
        () -> camundaClientProperties.getZeebe().getDefaults().getMaxJobsActive(),
        () -> properties.getWorker().getMaxJobsActive());
  }

  @Override
  public String getDefaultJobWorkerName() {
    return getProperty(
        "DefaultJobWorkerName",
        configCache,
        DEFAULT.getDefaultJobWorkerName(),
        () -> camundaClientProperties.getDefaults().getName(),
        () -> camundaClientProperties.getZeebe().getDefaults().getName(),
        () -> properties.getWorker().getDefaultName());
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return getProperty(
        "DefaultJobTimeout",
        configCache,
        DEFAULT.getDefaultJobTimeout(),
        () -> camundaClientProperties.getDefaults().getTimeout(),
        () -> camundaClientProperties.getZeebe().getDefaults().getTimeout(),
        () -> properties.getJob().getTimeout());
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return getProperty(
        "DefaultJobPollInterval",
        configCache,
        DEFAULT.getDefaultJobPollInterval(),
        () -> camundaClientProperties.getDefaults().getPollInterval(),
        () -> camundaClientProperties.getZeebe().getDefaults().getPollInterval(),
        () -> properties.getJob().getPollInterval());
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return getProperty(
        "DefaultMessageTimeToLive",
        configCache,
        DEFAULT.getDefaultMessageTimeToLive(),
        camundaClientProperties::getMessageTimeToLive,
        () -> camundaClientProperties.getZeebe().getMessageTimeToLive(),
        () -> properties.getMessage().getTimeToLive());
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return getProperty(
        "DefaultRequestTimeout",
        configCache,
        DEFAULT.getDefaultRequestTimeout(),
        () -> camundaClientProperties.getDefaults().getRequestTimeout(),
        () -> camundaClientProperties.getZeebe().getRequestTimeout(),
        () -> camundaClientProperties.getZeebe().getDefaults().getRequestTimeout(),
        properties::getRequestTimeout);
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return getProperty(
        "PlaintextConnectionEnabled",
        configCache,
        DEFAULT.isPlaintextConnectionEnabled(),
        this::composePlaintext,
        () -> properties.getSecurity().isPlaintext());
  }

  @Override
  public String getCaCertificatePath() {
    return getProperty(
        "CaCertificatePath",
        configCache,
        DEFAULT.getCaCertificatePath(),
        camundaClientProperties::getCaCertificatePath,
        () -> camundaClientProperties.getZeebe().getCaCertificatePath(),
        () -> properties.getSecurity().getCertPath());
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    if (!configCache.containsKey("credentialsProvider")) {
      final OAuthCredentialsProviderBuilder credBuilder =
          CredentialsProvider.newCredentialsProviderBuilder()
              .clientId(
                  getProperty(
                      "credentialsProvider.clientId",
                      configCache,
                      null,
                      () -> camundaClientProperties.getAuth().getClientId(),
                      () -> properties.getCloud().getClientId(),
                      () -> Environment.system().get("ZEEBE_CLIENT_ID")))
              .clientSecret(
                  getProperty(
                      "credentialsProvider.clientSecret",
                      configCache,
                      null,
                      () -> camundaClientProperties.getAuth().getClientSecret(),
                      () -> properties.getCloud().getClientSecret(),
                      () -> Environment.system().get("ZEEBE_CLIENT_SECRET")))
              .audience(
                  getProperty(
                      "credentialProvider.audience",
                      configCache,
                      null,
                      () -> camundaClientProperties.getAuth().getAudience(),
                      () -> camundaClientProperties.getZeebe().getAudience(),
                      () -> properties.getCloud().getAudience()))
              .scope(
                  getProperty(
                      "credentialsProvider.scope",
                      configCache,
                      null,
                      () -> camundaClientProperties.getAuth().getScope(),
                      () -> camundaClientProperties.getZeebe().getScope(),
                      () -> properties.getCloud().getScope()))
              .authorizationServerUrl(
                  getProperty(
                      "credentialsProvider.authorizationServerUrl",
                      configCache,
                      null,
                      () -> camundaClientProperties.getAuth().getIssuer().toString(),
                      () -> properties.getCloud().getAuthUrl()))
              .credentialsCachePath(
                  getProperty(
                      "credentialsProvider.credentialsCachePath",
                      configCache,
                      null,
                      () -> camundaClientProperties.getAuth().getCredentialsCachePath(),
                      () -> properties.getCloud().getCredentialsCachePath()))
              .connectTimeout(camundaClientProperties.getAuth().getConnectTimeout())
              .readTimeout(camundaClientProperties.getAuth().getReadTimeout());

      maybeConfigureIdentityProviderSSLConfig(credBuilder);
      CredentialsProvider credProvider = credBuilder.build();
      configCache.put("credentialsProvider", credProvider);
    }
    return (CredentialsProvider) configCache.get("credentialsProvider");
  }

  @Override
  public Duration getKeepAlive() {
    return getProperty(
        "KeepAlive",
        configCache,
        DEFAULT.getKeepAlive(),
        camundaClientProperties::getKeepAlive,
        () -> camundaClientProperties.getZeebe().getKeepAlive(),
        () -> properties.getBroker().getKeepAlive());
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
    return getProperty(
        "OverrideAuthority",
        configCache,
        DEFAULT.getOverrideAuthority(),
        camundaClientProperties::getOverrideAuthority,
        () -> camundaClientProperties.getZeebe().getOverrideAuthority(),
        () -> properties.getSecurity().getOverrideAuthority());
  }

  @Override
  public int getMaxMessageSize() {
    return getProperty(
        "MaxMessageSize",
        configCache,
        DEFAULT.getMaxMessageSize(),
        camundaClientProperties::getMaxMessageSize,
        () -> camundaClientProperties.getZeebe().getMaxMessageSize(),
        () -> properties.getMessage().getMaxMessageSize());
  }

  @Override
  public int getMaxMetadataSize() {
    return getProperty(
        "MaxMetadataSize",
        configCache,
        DEFAULT.getMaxMetadataSize(),
        camundaClientProperties::getMaxMetadataSize,
        () -> camundaClientProperties.getZeebe().getMaxMessageSize());
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
    return getProperty(
        "DefaultJobWorkerStreamEnabled",
        configCache,
        DEFAULT.getDefaultJobWorkerStreamEnabled(),
        () -> camundaClientProperties.getDefaults().getStreamEnabled(),
        () -> camundaClientProperties.getZeebe().getDefaults().getStreamEnabled(),
        properties::getDefaultJobWorkerStreamEnabled);
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return false;
  }

  @Override
  public boolean preferRestOverGrpc() {
    return getProperty(
        "preferRestOverGrpc",
        configCache,
        DEFAULT.preferRestOverGrpc(),
        camundaClientProperties::getPreferRestOverGrpc,
        () -> camundaClientProperties.getZeebe().isPreferRestOverGrpc());
  }

  private void maybeConfigureIdentityProviderSSLConfig(
      final OAuthCredentialsProviderBuilder builder) {
    if (camundaClientProperties.getAuth().getKeystorePath() != null) {
      final Path keyStore = Paths.get(camundaClientProperties.getAuth().getKeystorePath());
      if (Files.exists(keyStore)) {
        builder.keystorePath(keyStore);
        builder.keystorePassword(camundaClientProperties.getAuth().getKeystorePassword());
        builder.keystoreKeyPassword(camundaClientProperties.getAuth().getKeystoreKeyPassword());
      }
    }

    if (camundaClientProperties.getAuth().getTruststorePath() != null) {
      final Path trustStore = Paths.get(camundaClientProperties.getAuth().getTruststorePath());
      if (Files.exists(trustStore)) {
        builder.truststorePath(trustStore);
        builder.truststorePassword(camundaClientProperties.getAuth().getTruststorePassword());
      }
    }
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
      case "http" -> true;
      case "https" -> false;
      default ->
          throw new IllegalStateException(
              String.format("Unrecognized zeebe protocol '%s'", protocol));
    };
  }

  @Override
  public String toString() {
    return "CamundaClientConfigurationImpl{" +
        "camundaClientProperties=" + camundaClientProperties +
        ", jsonMapper=" + jsonMapper +
        ", interceptors=" + interceptors +
        ", chainHandlers=" + chainHandlers +
        ", zeebeClientExecutorService=" + zeebeClientExecutorService +
        '}';
  }
}
