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

import static org.springframework.util.StringUtils.hasText;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.client.impl.util.Environment;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CommonConfigurationProperties;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.DefaultNoopAuthentication;
import io.camunda.zeebe.spring.common.auth.Product;
import io.grpc.ClientInterceptor;
import io.grpc.Status.Code;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

public class ZeebeClientConfigurationSpringImpl implements ZeebeClientConfiguration {

  @Autowired private ZeebeClientConfigurationProperties properties;

  @Autowired private CommonConfigurationProperties commonConfigurationProperties;

  @Autowired private Authentication authentication;

  @Lazy // Must be lazy, otherwise we get circular dependencies on beans
  @Autowired
  private JsonMapper jsonMapper;

  @Lazy
  @Autowired(required = false)
  private List<ClientInterceptor> interceptors;

  @Lazy @Autowired private ZeebeClientExecutorService zeebeClientExecutorService;
  private CredentialsProvider credentialsProvider;

  @PostConstruct
  public void applyLegacy() {
    // make sure environment variables and other legacy config options are taken into account
    // (duplicate, also done by  qPostConstruct, whatever)
    properties.applyOverrides();
  }

  @Override
  public String getGatewayAddress() {
    return properties.getGatewayAddress();
  }

  @Override
  public URI getRestAddress() {
    return properties.getRestAddress();
  }

  @Override
  public URI getGrpcAddress() {
    return properties.getGrpcAddress();
  }

  @Override
  public String getDefaultTenantId() {
    return properties.getDefaultTenantId();
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return properties.getDefaultJobWorkerTenantIds();
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return properties.getNumJobWorkerExecutionThreads();
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return properties.getDefaultJobWorkerMaxJobsActive();
  }

  @Override
  public String getDefaultJobWorkerName() {
    return properties.getDefaultJobWorkerName();
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return properties.getDefaultJobTimeout();
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return properties.getDefaultJobPollInterval();
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return properties.getDefaultMessageTimeToLive();
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return properties.getDefaultRequestTimeout();
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return properties.isPlaintextConnectionEnabled();
  }

  @Override
  public String getCaCertificatePath() {
    return properties.getCaCertificatePath();
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    if (credentialsProvider == null) {
      credentialsProvider = initCredentialsProvider();
    }
    return credentialsProvider;
  }

  @Override
  public Duration getKeepAlive() {
    return properties.getKeepAlive();
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
    return properties.getOverrideAuthority();
  }

  @Override
  public int getMaxMessageSize() {
    return properties.getMaxMessageSize();
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return zeebeClientExecutorService.get();
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
    return properties.ownsJobWorkerExecutor();
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return properties.getDefaultJobWorkerStreamEnabled();
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return properties.useDefaultRetryPolicy();
  }

  @Override
  public boolean preferRestOverGrpc() {
    return false;
  }

  private CredentialsProvider initCredentialsProvider() {

    if (commonConfigurationProperties.getEnabled()
        && !(authentication instanceof DefaultNoopAuthentication)) {
      return new CredentialsProvider() {
        @Override
        public void applyCredentials(final CredentialsApplier applier) {
          final Map.Entry<String, String> authHeader = authentication.getTokenHeader(Product.ZEEBE);
          applier.put(authHeader.getKey(), authHeader.getValue());
        }

        @Override
        public boolean shouldRetryRequest(final StatusCode statusCode) {
          return statusCode.code() == Code.DEADLINE_EXCEEDED.value();
        }
      };
    }
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
    }
    if (Environment.system().get("ZEEBE_CLIENT_ID") != null
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
        + ", commonConfigurationProperties="
        + commonConfigurationProperties
        + ", authentication="
        + authentication
        + ", jsonMapper="
        + jsonMapper
        + ", interceptors="
        + interceptors
        + '}';
  }
}
