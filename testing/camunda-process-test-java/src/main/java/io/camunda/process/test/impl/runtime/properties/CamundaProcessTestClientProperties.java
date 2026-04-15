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

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.ClientProperties;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import java.net.URI;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves the backwards-compatible remote client address properties ({@code
 * remote.client.grpcAddress} and {@code remote.client.restAddress}) and creates a {@link
 * CamundaClientBuilderFactory} that applies all standard {@link ClientProperties} from the
 * properties passed to the constructor.
 *
 * <p>The remote address properties are applied as overrides on top of all other properties to
 * preserve backwards compatibility.
 */
public class CamundaProcessTestClientProperties {

  /** Backwards-compatible property for the remote gRPC address. */
  public static final String PROPERTY_NAME_REMOTE_GRPC_ADDRESS = "remote.client.grpcAddress";

  /** Backwards-compatible property for the remote REST address. */
  public static final String PROPERTY_NAME_REMOTE_REST_ADDRESS = "remote.client.restAddress";

  private final Properties properties;
  private final URI remoteGrpcAddress;
  private final URI remoteRestAddress;

  public CamundaProcessTestClientProperties(final Properties properties) {
    this.properties = properties;
    remoteGrpcAddress =
        getPropertyOrNull(properties, PROPERTY_NAME_REMOTE_GRPC_ADDRESS, URI::create);
    remoteRestAddress =
        getPropertyOrNull(properties, PROPERTY_NAME_REMOTE_REST_ADDRESS, URI::create);
  }

  /**
   * Creates a {@link CamundaClientBuilderFactory} that applies all standard {@link
   * ClientProperties} from the properties passed to the constructor, plus backwards-compatible
   * remote address overrides.
   *
   * <p>If {@code camunda.client.cloud.clusterId} is set, a cloud client builder is used; otherwise
   * a self-managed builder is used.
   *
   * @return a factory that creates pre-configured {@link CamundaClientBuilder} instances
   */
  public CamundaClientBuilderFactory createCamundaClientBuilderFactory() {
    // Pre-compute the remote address overrides once so every factory invocation is lightweight.
    final URI grpcOverride = remoteGrpcAddress;
    final URI restOverride = remoteRestAddress;
    return () -> {
      final CamundaClientBuilder builder = createBaseBuilder(properties);
      builder.withProperties(properties);
      Optional.ofNullable(grpcOverride).ifPresent(builder::grpcAddress);
      Optional.ofNullable(restOverride).ifPresent(builder::restAddress);
      return builder;
    };
  }

  public URI getGrpcAddress() {
    return remoteGrpcAddress;
  }

  public URI getRestAddress() {
    return remoteRestAddress;
  }

  private static CamundaClientBuilder createBaseBuilder(final Properties properties) {
    final String clusterId = properties.getProperty(ClientProperties.CLOUD_CLUSTER_ID);
    if (clusterId != null && !clusterId.isEmpty()) {
      return CamundaClient.newCloudClientBuilder()
          .withClusterId(clusterId)
          .withClientId(properties.getProperty(ClientProperties.CLOUD_CLIENT_ID))
          .withClientSecret(properties.getProperty(ClientProperties.CLOUD_CLIENT_SECRET))
          .withRegion(properties.getProperty(ClientProperties.CLOUD_REGION));
    }
    return CamundaClient.newClientBuilder();
  }
}
