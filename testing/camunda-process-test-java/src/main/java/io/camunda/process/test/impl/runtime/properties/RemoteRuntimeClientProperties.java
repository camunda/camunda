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
package io.camunda.process.test.impl.runtime.properties;

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CamundaClientCloudBuilderStep1.CamundaClientCloudBuilderStep2.CamundaClientCloudBuilderStep3.CamundaClientCloudBuilderStep4.CamundaClientCloudBuilderStep5;
import io.camunda.client.CredentialsProvider;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import io.camunda.process.test.impl.runtime.util.CptCredentialsProviderConfigurer;
import java.net.URI;
import java.time.Duration;
import java.util.Properties;

public class RemoteRuntimeClientProperties {
  public static final String PROPERTY_NAME_MODE = "remote.client.mode";
  public static final String PROPERTY_NAME_GRPC_ADDRESS = "remote.client.grpcAddress";
  public static final String PROPERTY_NAME_REST_ADDRESS = "remote.client.restAddress";
  public static final String PROPERTY_NAME_CAMUNDA_CLIENT_REQUEST_TIMEOUT =
      "remote.client.requestTimeout";

  private final ClientMode mode;
  private final URI grpcAddress;
  private final URI restAddress;
  private final Duration requestTimeout;

  private final RemoteRuntimeClientCloudProperties remoteRuntimeClientCloudProperties;
  private final RemoteRuntimeClientAuthProperties remoteRuntimeClientAuthProperties;

  public RemoteRuntimeClientProperties(final Properties properties) {
    remoteRuntimeClientAuthProperties = new RemoteRuntimeClientAuthProperties(properties);
    remoteRuntimeClientCloudProperties = new RemoteRuntimeClientCloudProperties(properties);

    mode =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_MODE,
            v -> {
              try {
                return ClientMode.valueOf(v.toLowerCase());
              } catch (final Throwable t) {
                return remoteRuntimeClientCloudProperties.getClusterId() != null
                    ? ClientMode.saas
                    : ClientMode.selfManaged;
              }
            },
            ClientMode.selfManaged);

    grpcAddress =
        getPropertyOrNull(
            properties,
            PROPERTY_NAME_GRPC_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.REMOTE_CLIENT_GRPC_ADDRESS;
              }
            });

    restAddress =
        getPropertyOrNull(
            properties,
            PROPERTY_NAME_REST_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.REMOTE_CLIENT_REST_ADDRESS;
              }
            });

    requestTimeout =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_CAMUNDA_CLIENT_REQUEST_TIMEOUT,
            v -> {
              try {
                return Duration.parse(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.DEFAULT_CAMUNDA_CLIENT_REQUEST_TIMEOUT;
              }
            },
            CamundaProcessTestRuntimeDefaults.DEFAULT_CAMUNDA_CLIENT_REQUEST_TIMEOUT);
  }

  public ClientMode getMode() {
    return mode;
  }

  public URI getGrpcAddress() {
    return grpcAddress;
  }

  public URI getRestAddress() {
    return restAddress;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public RemoteRuntimeClientCloudProperties getCloudProperties() {
    return remoteRuntimeClientCloudProperties;
  }

  public RemoteRuntimeClientAuthProperties getAuthProperties() {
    return remoteRuntimeClientAuthProperties;
  }

  public CamundaClientBuilderFactory getClientBuilderFactory() {
    final CamundaClientBuilder camundaClientBuilder =
        createCamundaClient(mode).defaultRequestTimeout(requestTimeout);

    if (CamundaProcessTestRuntimeDefaults.REMOTE_CLIENT_GRPC_ADDRESS != null) {
      camundaClientBuilder.grpcAddress(
          CamundaProcessTestRuntimeDefaults.REMOTE_CLIENT_GRPC_ADDRESS);
    }

    if (CamundaProcessTestRuntimeDefaults.REMOTE_CLIENT_REST_ADDRESS != null) {
      camundaClientBuilder.restAddress(
          CamundaProcessTestRuntimeDefaults.REMOTE_CLIENT_REST_ADDRESS);
    }

    return () -> camundaClientBuilder;
  }

  private CamundaClientBuilder createCamundaClient(final ClientMode mode) {
    return mode == ClientMode.saas ? buildCloudClientFactory() : buildSelfManagedClientFactory();
  }

  private CamundaClientBuilder buildCloudClientFactory() {

    final CamundaClientCloudBuilderStep5 cloudBuilderStep =
        CamundaClient.newCloudClientBuilder()
            .withClusterId(remoteRuntimeClientCloudProperties.getClusterId())
            .withClientId(remoteRuntimeClientAuthProperties.getClientId())
            .withClientSecret(remoteRuntimeClientAuthProperties.getClientSecret())
            .withRegion(remoteRuntimeClientCloudProperties.getRegion());

    if (remoteRuntimeClientAuthProperties.getMethod() != null) {
      final CredentialsProvider credentialsProvider =
          CptCredentialsProviderConfigurer.configure(this);
      cloudBuilderStep.credentialsProvider(credentialsProvider);
    }

    return cloudBuilderStep;
  }

  private CamundaClientBuilder buildSelfManagedClientFactory() {
    return CamundaClient.newClientBuilder().usePlaintext();
  }

  public enum ClientMode {
    selfManaged,
    saas
  }
}
