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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.impl.util.AddressUtil;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.grpc.ClientInterceptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;

@Deprecated(since = "8.6", forRemoval = true)
public class ZeebeClientConfigurationImpl implements ZeebeClientConfiguration {
  private final CamundaClientConfiguration camundaClientConfiguration;

  public ZeebeClientConfigurationImpl(final CamundaClientConfiguration camundaClientConfiguration) {
    this.camundaClientConfiguration = camundaClientConfiguration;
  }

  @Override
  public String getGatewayAddress() {
    return AddressUtil.composeGatewayAddress(getGrpcAddress());
  }

  @Override
  public URI getRestAddress() {
    return camundaClientConfiguration.getRestAddress();
  }

  @Override
  public URI getGrpcAddress() {
    return camundaClientConfiguration.getGrpcAddress();
  }

  @Override
  public String getDefaultTenantId() {
    return camundaClientConfiguration.getDefaultTenantId();
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return camundaClientConfiguration.getDefaultJobWorkerTenantIds();
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return camundaClientConfiguration.getNumJobWorkerExecutionThreads();
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return camundaClientConfiguration.getDefaultJobWorkerMaxJobsActive();
  }

  @Override
  public String getDefaultJobWorkerName() {
    return camundaClientConfiguration.getDefaultJobWorkerName();
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return camundaClientConfiguration.getDefaultJobTimeout();
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return camundaClientConfiguration.getDefaultJobPollInterval();
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return camundaClientConfiguration.getDefaultMessageTimeToLive();
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return camundaClientConfiguration.getDefaultRequestTimeout();
  }

  @Override
  public Duration getDefaultRequestTimeoutOffset() {
    return camundaClientConfiguration.getDefaultRequestTimeoutOffset();
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return AddressUtil.isPlaintextConnection(getGrpcAddress());
  }

  @Override
  public String getCaCertificatePath() {
    return camundaClientConfiguration.getCaCertificatePath();
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    final io.camunda.client.CredentialsProvider credentialsProvider =
        camundaClientConfiguration.getCredentialsProvider();
    return new CredentialsProviderCompat(credentialsProvider);
  }

  @Override
  public Duration getKeepAlive() {
    return camundaClientConfiguration.getKeepAlive();
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
    return camundaClientConfiguration.getOverrideAuthority();
  }

  @Override
  public int getMaxMessageSize() {
    return camundaClientConfiguration.getMaxMessageSize();
  }

  @Override
  public int getMaxMetadataSize() {
    return camundaClientConfiguration.getMaxMetadataSize();
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return camundaClientConfiguration.jobWorkerExecutor();
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
    return camundaClientConfiguration.ownsJobWorkerExecutor();
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return camundaClientConfiguration.getDefaultJobWorkerStreamEnabled();
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return camundaClientConfiguration.useDefaultRetryPolicy();
  }

  @Override
  public boolean preferRestOverGrpc() {
    return camundaClientConfiguration.preferRestOverGrpc();
  }

  @Override
  public String toString() {
    return "ZeebeClientConfigurationImpl{"
        + "camundaClientConfiguration="
        + camundaClientConfiguration
        + '}';
  }

  private static class CredentialsProviderCompat implements CredentialsProvider {
    private final io.camunda.client.CredentialsProvider credentialsProvider;

    public CredentialsProviderCompat(
        final io.camunda.client.CredentialsProvider credentialsProvider) {
      this.credentialsProvider = credentialsProvider;
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
  }
}
