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

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.net.URI;
import java.util.Properties;

public class RemoteRuntimeClientProperties {
  public static final String PROPERTY_NAME_REMOTE_CLIENT_GRPC_ADDRESS = "remote.client.grpcAddress";
  public static final String PROPERTY_NAME_REMOTE_CLIENT_REST_ADDRESS = "remote.client.restAddress";

  private final URI grpcAddress;
  private final URI restAddress;

  public RemoteRuntimeClientProperties(final Properties properties) {
    grpcAddress =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_REMOTE_CLIENT_GRPC_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.REMOTE_CLIENT_GRPC_ADDRESS;
              }
            },
            null);

    restAddress =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_REMOTE_CLIENT_REST_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.REMOTE_CLIENT_REST_ADDRESS;
              }
            },
            null);
  }

  public URI getGrpcAddress() {
    return grpcAddress;
  }

  public URI getRestAddress() {
    return restAddress;
  }
}
