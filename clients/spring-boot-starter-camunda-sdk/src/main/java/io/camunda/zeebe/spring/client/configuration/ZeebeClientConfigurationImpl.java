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

<<<<<<< HEAD
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.grpc.ClientInterceptor;
import java.io.IOException;
import java.io.InputStream;
=======
import static java.util.Optional.ofNullable;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.grpc.ClientInterceptor;
import java.net.MalformedURLException;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
<<<<<<< HEAD
=======
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.unit.DataSize;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)

@Deprecated(since = "8.6", forRemoval = true)
public class ZeebeClientConfigurationImpl implements ZeebeClientConfiguration {
<<<<<<< HEAD
  private final CamundaClientConfiguration camundaClientConfiguration;

  public ZeebeClientConfigurationImpl(final CamundaClientConfiguration camundaClientConfiguration) {
    this.camundaClientConfiguration = camundaClientConfiguration;
=======
  public static final ZeebeClientBuilderImpl DEFAULT =
      (ZeebeClientBuilderImpl) new ZeebeClientBuilderImpl().withProperties(new Properties());

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeClientConfigurationImpl.class);
  private final CamundaClientProperties camundaClientProperties;
  private final JsonMapper jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;
  private final ZeebeClientExecutorService zeebeClientExecutorService;
  private final CredentialsProvider credentialsProvider;

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
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public String getGatewayAddress() {
<<<<<<< HEAD
    return camundaClientConfiguration.getGatewayAddress();
=======
    return ofNullable(composeGatewayAddress()).orElse(DEFAULT.getGatewayAddress());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public URI getRestAddress() {
<<<<<<< HEAD
    return camundaClientConfiguration.getRestAddress();
=======
    return ofNullable(camundaClientProperties.getZeebe().getRestAddress())
        .orElse(DEFAULT.getRestAddress());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public URI getGrpcAddress() {
<<<<<<< HEAD
    return camundaClientConfiguration.getGrpcAddress();
=======
    return ofNullable(camundaClientProperties.getZeebe().getGrpcAddress())
        .orElse(DEFAULT.getGrpcAddress());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public String getDefaultTenantId() {
<<<<<<< HEAD
    return camundaClientConfiguration.getDefaultTenantId();
=======
    return ofNullable(camundaClientProperties.getTenantId()).orElse(DEFAULT.getDefaultTenantId());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
<<<<<<< HEAD
    return camundaClientConfiguration.getDefaultJobWorkerTenantIds();
=======
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getTenantIds())
        .orElse(DEFAULT.getDefaultJobWorkerTenantIds());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
<<<<<<< HEAD
    return camundaClientConfiguration.getNumJobWorkerExecutionThreads();
=======
    return ofNullable(camundaClientProperties.getZeebe().getExecutionThreads())
        .orElse(DEFAULT.getNumJobWorkerExecutionThreads());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
<<<<<<< HEAD
    return camundaClientConfiguration.getDefaultJobWorkerMaxJobsActive();
=======
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getMaxJobsActive())
        .orElse(DEFAULT.getDefaultJobWorkerMaxJobsActive());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public String getDefaultJobWorkerName() {
<<<<<<< HEAD
    return camundaClientConfiguration.getDefaultJobWorkerName();
=======
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getName())
        .orElse(DEFAULT.getDefaultJobWorkerName());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public Duration getDefaultJobTimeout() {
<<<<<<< HEAD
    return camundaClientConfiguration.getDefaultJobTimeout();
=======
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getTimeout())
        .orElse(DEFAULT.getDefaultJobTimeout());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public Duration getDefaultJobPollInterval() {
<<<<<<< HEAD
    return camundaClientConfiguration.getDefaultJobPollInterval();
=======
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getPollInterval())
        .orElse(DEFAULT.getDefaultJobPollInterval());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
<<<<<<< HEAD
    return camundaClientConfiguration.getDefaultMessageTimeToLive();
=======
    return ofNullable(camundaClientProperties.getZeebe().getMessageTimeToLive())
        .orElse(DEFAULT.getDefaultMessageTimeToLive());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public Duration getDefaultRequestTimeout() {
<<<<<<< HEAD
    return camundaClientConfiguration.getDefaultRequestTimeout();
=======
    return ofNullable(camundaClientProperties.getZeebe().getRequestTimeout())
        .orElse(DEFAULT.getDefaultRequestTimeout());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
<<<<<<< HEAD
    return camundaClientConfiguration.isPlaintextConnectionEnabled();
=======
    return composePlaintext();
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public String getCaCertificatePath() {
<<<<<<< HEAD
    return camundaClientConfiguration.getCaCertificatePath();
=======
    return ofNullable(camundaClientProperties.getZeebe().getCaCertificatePath())
        .orElse(DEFAULT.getCaCertificatePath());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
<<<<<<< HEAD
    final io.camunda.client.CredentialsProvider credentialsProvider =
        camundaClientConfiguration.getCredentialsProvider();
    return new CredentialsProviderCompat(credentialsProvider);
=======
    return credentialsProvider;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public Duration getKeepAlive() {
<<<<<<< HEAD
    return camundaClientConfiguration.getKeepAlive();
=======
    return ofNullable(camundaClientProperties.getZeebe().getKeepAlive())
        .orElse(DEFAULT.getKeepAlive());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public List<ClientInterceptor> getInterceptors() {
    return camundaClientConfiguration.getInterceptors();
  }

  @Override
  public List<AsyncExecChainHandler> getChainHandlers() {
    return camundaClientConfiguration.getChainHandlers();
  }

  @Override
  public JsonMapper getJsonMapper() {
    final io.camunda.client.api.JsonMapper jsonMapper = camundaClientConfiguration.getJsonMapper();
    return new JsonMapperCompat(jsonMapper);
  }

  @Override
  public String getOverrideAuthority() {
<<<<<<< HEAD
    return camundaClientConfiguration.getOverrideAuthority();
=======
    return ofNullable(camundaClientProperties.getZeebe().getOverrideAuthority())
        .orElse(DEFAULT.getOverrideAuthority());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public int getMaxMessageSize() {
<<<<<<< HEAD
    return camundaClientConfiguration.getMaxMessageSize();
=======
    return ofNullable(camundaClientProperties.getZeebe().getMaxMessageSize())
        .map(DataSize::toBytes)
        .map(Math::toIntExact)
        .orElse(DEFAULT.getMaxMessageSize());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public int getMaxMetadataSize() {
<<<<<<< HEAD
    return camundaClientConfiguration.getMaxMetadataSize();
=======
    return ofNullable(camundaClientProperties.getZeebe().getMaxMetadataSize())
        .map(DataSize::toBytes)
        .map(Math::toIntExact)
        .orElse(DEFAULT.getMaxMetadataSize());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return camundaClientConfiguration.jobWorkerExecutor();
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
<<<<<<< HEAD
    return camundaClientConfiguration.ownsJobWorkerExecutor();
=======
    return zeebeClientExecutorService.isOwnedByZeebeClient();
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
<<<<<<< HEAD
    return camundaClientConfiguration.getDefaultJobWorkerStreamEnabled();
=======
    return ofNullable(camundaClientProperties.getZeebe().getDefaults().getStreamEnabled())
        .orElse(DEFAULT.getDefaultJobWorkerStreamEnabled());
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return camundaClientConfiguration.useDefaultRetryPolicy();
  }

  @Override
  public boolean preferRestOverGrpc() {
<<<<<<< HEAD
    return camundaClientConfiguration.preferRestOverGrpc();
  }

  private static class CredentialsProviderCompat implements CredentialsProvider {
    private final io.camunda.client.CredentialsProvider credentialsProvider;

    public CredentialsProviderCompat(
        final io.camunda.client.CredentialsProvider credentialsProvider) {
      this.credentialsProvider = credentialsProvider;
=======
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
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
    }

    @Override
    public void applyCredentials(final CredentialsApplier applier) throws IOException {
      credentialsProvider.applyCredentials(new CredentialsApplierCompat(applier));
    }

    @Override
    public boolean shouldRetryRequest(final StatusCode statusCode) {
      return credentialsProvider.shouldRetryRequest(new StatusCodeCompat(statusCode));
    }

    private static class CredentialsApplierCompat
        implements io.camunda.client.CredentialsProvider.CredentialsApplier {
      private final CredentialsApplier credentialsApplier;

<<<<<<< HEAD
      public CredentialsApplierCompat(final CredentialsApplier credentialsApplier) {
        this.credentialsApplier = credentialsApplier;
      }

      @Override
      public void put(final String key, final String value) {
        credentialsApplier.put(key, value);
      }
    }

    private static class StatusCodeCompat
        implements io.camunda.client.CredentialsProvider.StatusCode {
      private final StatusCode statusCode;

      public StatusCodeCompat(final StatusCode statusCode) {
        this.statusCode = statusCode;
      }

      @Override
      public int code() {
        return statusCode.code();
      }

      @Override
      public boolean isUnauthorized() {
        return statusCode.isUnauthorized();
      }
    }
  }

  private static class JsonMapperCompat implements JsonMapper {
    private final io.camunda.client.api.JsonMapper jsonMapper;

    public JsonMapperCompat(final io.camunda.client.api.JsonMapper jsonMapper) {
      this.jsonMapper = jsonMapper;
    }

    @Override
    public <T> T fromJson(final String json, final Class<T> typeClass) {
      return jsonMapper.fromJson(json, typeClass);
    }

    @Override
    public Map<String, Object> fromJsonAsMap(final String json) {
      return jsonMapper.fromJsonAsMap(json);
    }

    @Override
    public Map<String, String> fromJsonAsStringMap(final String json) {
      return jsonMapper.fromJsonAsStringMap(json);
    }

    @Override
    public String toJson(final Object value) {
      return jsonMapper.toJson(value);
    }

    @Override
    public String validateJson(final String propertyName, final String jsonInput) {
      return jsonMapper.validateJson(propertyName, jsonInput);
    }

    @Override
    public String validateJson(final String propertyName, final InputStream jsonInput) {
      return jsonMapper.validateJson(propertyName, jsonInput);
    }
=======
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
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8)
  }
}
