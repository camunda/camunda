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
package io.camunda.process.test.impl.configuration;

import io.camunda.process.test.api.DataDeletionMode;
import io.camunda.process.test.impl.cleanup.CleanupStrategy;
import io.camunda.process.test.impl.cleanup.CleanupStrategyResolver;
import io.camunda.process.test.impl.deployment.DeploymentCollector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamundaProcessTestCleanupConfiguration {

  @Bean
  public CleanupStrategy cleanupStrategy(
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {
    final DataDeletionMode dataDeletionMode = runtimeConfiguration.getDataDeletionMode();
    return CleanupStrategyResolver.resolve(dataDeletionMode);
  }

  @Bean
  public DeploymentCollector deploymentCollector() {
    return new DeploymentCollector();
  }
}
