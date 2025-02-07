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
package io.camunda.process.test.config;

import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.configuration.CamundaContainerRuntimeConfiguration;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntimeBuilder;
import io.camunda.process.test.impl.spec.ProcessSpecRunner;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessSpecRunnerConfig {

  private final List<AutoCloseable> clients = new ArrayList<>();

  @Bean
  public ProcessSpecRunner processSpecRunner(
      final CamundaProcessTestContext camundaProcessTestContext) {

    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(5));

    return new ProcessSpecRunner(camundaProcessTestContext);
  }

  @Bean
  public CamundaProcessTestContext camundaProcessTestContext(
      final CamundaContainerRuntime camundaContainerRuntime) {
    return new CamundaProcessTestContextImpl(
        camundaContainerRuntime.getCamundaContainer(),
        camundaContainerRuntime.getConnectorsContainer(),
        clients::add);
  }

  @Bean
  public CamundaContainerRuntime camundaContainerRuntime(
      final CamundaContainerRuntimeConfiguration runtimeConfiguration) {
    final CamundaContainerRuntimeBuilder containerRuntimeBuilder =
        CamundaContainerRuntime.newBuilder();

    containerRuntimeBuilder
        .withCamundaDockerImageVersion(runtimeConfiguration.getCamundaVersion())
        .withCamundaDockerImageName(runtimeConfiguration.getCamundaDockerImageName())
        .withCamundaEnv(runtimeConfiguration.getCamundaEnvVars());

    runtimeConfiguration
        .getCamundaExposedPorts()
        .forEach(containerRuntimeBuilder::withCamundaExposedPort);

    containerRuntimeBuilder
        .withConnectorsEnabled(runtimeConfiguration.isConnectorsEnabled())
        .withConnectorsDockerImageName(runtimeConfiguration.getConnectorsDockerImageName())
        .withConnectorsDockerImageVersion(runtimeConfiguration.getConnectorsDockerImageVersion())
        .withConnectorsEnv(runtimeConfiguration.getConnectorsEnvVars())
        .withConnectorsSecrets(runtimeConfiguration.getConnectorsSecrets());

    final CamundaContainerRuntime containerRuntime = containerRuntimeBuilder.build();

    containerRuntime.start();

    return containerRuntime;
  }

  @PreDestroy
  public void closeClients() {
    for (final AutoCloseable client : clients) {
      try {
        client.close();
      } catch (final Exception e) {
        // ignore
      }
    }
  }
}
