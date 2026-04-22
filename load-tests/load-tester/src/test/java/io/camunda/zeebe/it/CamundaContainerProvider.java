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
package io.camunda.zeebe.it;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Boots a Camunda + Elasticsearch stack for the integration tests.
 *
 * <p>On 8.7, the Camunda image runs operate, tasklist, and the broker as separate components and
 * uses Elasticsearch as backing storage. {@link CamundaContainerRuntime} wires both containers
 * together on a shared Docker network. We disable the {@code auth} Spring profile so the cluster
 * accepts unauthenticated traffic — the load-tester app's {@code mode: ~} config in {@code
 * application-it.yaml} matches this with a no-op credentials provider.
 *
 * <p>The runtime is started once and reused across every IT in the failsafe fork; a JVM shutdown
 * hook closes it when the process exits.
 */
final class CamundaContainerProvider {

  private static volatile CamundaContainerRuntime runtime;

  private CamundaContainerProvider() {}

  static synchronized CamundaContainer getCamundaContainer() {
    if (runtime == null) {
      final CamundaContainerRuntime started =
          CamundaContainerRuntime.newBuilder()
              .withConnectorsEnabled(false)
              .withCamundaEnv("SPRING_PROFILES_ACTIVE", "operate,tasklist,broker")
              .build();
      started.start();
      runtime = started;
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      started.close();
                    } catch (final Exception ignored) {
                      // best effort
                    }
                  }));
    }
    return runtime.getCamundaContainer();
  }

  static void registerClientProperties(
      final CamundaContainer camunda, final DynamicPropertyRegistry registry) {
    registry.add("camunda.client.zeebe.grpc-address", camunda::getGrpcApiAddress);
    registry.add("camunda.client.zeebe.rest-address", camunda::getRestApiAddress);
  }
}
