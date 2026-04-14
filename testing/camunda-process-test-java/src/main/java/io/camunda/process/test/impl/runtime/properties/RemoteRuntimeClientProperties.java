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

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.net.URI;
import java.util.Properties;

/**
 * Resolves the backwards-compatible remote client address properties from the properties file.
 *
 * <p>These properties ({@code remote.client.grpcAddress}, {@code remote.client.restAddress}) are
 * kept for backwards compatibility; new configurations should use the standard {@link
 * io.camunda.client.ClientProperties} keys instead.
 */
public class RemoteRuntimeClientProperties {

  public static final String PROPERTY_NAME_GRPC_ADDRESS = "remote.client.grpcAddress";
  public static final String PROPERTY_NAME_REST_ADDRESS = "remote.client.restAddress";

  private final URI grpcAddress;
  private final URI restAddress;

  public RemoteRuntimeClientProperties(final Properties properties) {
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
  }

  public URI getGrpcAddress() {
    return grpcAddress;
  }

  public URI getRestAddress() {
    return restAddress;
  }
}
