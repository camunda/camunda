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
import io.camunda.process.test.impl.runtime.ContainerRuntimeDefaults;
import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared utilities for creating and configuring a {@link CamundaContainer} in integration tests.
 */
final class CamundaContainerProvider {

  private CamundaContainerProvider() {}

  static CamundaContainer createCamundaContainer() {
    return new CamundaContainer(
            DockerImageName.parse(ContainerRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME)
                .withTag(ContainerRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION))
        .withImagePullPolicy(PullPolicy.ageBased(Duration.ofHours(12)));
  }

  static void registerClientProperties(
      final CamundaContainer camunda, final DynamicPropertyRegistry registry) {
    registry.add("camunda.client.zeebe.grpc-address", camunda::getGrpcApiAddress);
    registry.add("camunda.client.zeebe.rest-address", camunda::getRestApiAddress);
  }
}
