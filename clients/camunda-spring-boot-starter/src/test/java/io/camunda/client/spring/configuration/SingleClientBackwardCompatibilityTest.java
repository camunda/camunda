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
package io.camunda.client.spring.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.health.HealthCheck;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.spring.actuator.CamundaClientHealthIndicator;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.client.spring.event.MultiCamundaLifecycleEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Backward-compatibility coverage for the unification (#57344): a traditional single-client setup
 * ({@code camunda.client.*}) must keep working once the starter is on the single (multi-client)
 * path. The {@code EnvironmentPostProcessor} runs under {@code @SpringBootTest}, so the remap onto
 * the {@code default} client is exercised end-to-end.
 */
@SpringBootTest(
    classes = CamundaAutoConfiguration.class,
    properties = {
      "camunda.client.rest-address=https://localhost:8080",
      "camunda.client.grpc-address=https://localhost:26500"
    })
public class SingleClientBackwardCompatibilityTest {

  @Autowired private ApplicationContext applicationContext;
  @Autowired private CamundaClientRegistry registry;
  @Autowired private CamundaClient camundaClient;

  @Test
  void shouldProjectSingleClientConfigOntoDefaultClient() {
    // the single-client camunda.client.* config is remapped to one client named 'default'
    assertThat(registry.clientNames()).containsExactly("default");
  }

  @Test
  void shouldResolvePrimaryClientUnderLegacyBeanNameAlias() {
    // @Autowired CamundaClient resolves to the primary (default) client...
    assertThat(camundaClient).isNotNull();
    // ...which is registered as 'defaultCamundaClient' and aliased as the historical
    // 'camundaClient'
    assertThat(applicationContext.getBean("defaultCamundaClient", CamundaClient.class))
        .isSameAs(camundaClient);
    assertThat(applicationContext.getBean("camundaClient", CamundaClient.class))
        .isSameAs(camundaClient);
  }

  @Test
  void shouldExposeCamundaClientConfigurationBean() {
    // @Autowired CamundaClientConfiguration keeps working as on the single-client path
    assertThat(applicationContext.getBean(CamundaClientConfiguration.class)).isNotNull();
  }

  @Test
  void shouldPreserveActuatorHealthSurfaceOnSingleClientPath() {
    // the actuator health check and indicator must still be registered for a single-client app
    // (the per-client CamundaClient beans are contributed by a bean-definition post-processor, so
    // the health check must gate on the resolvable primary rather than a live candidate count)
    assertThat(applicationContext.getBeanNamesForType(HealthCheck.class)).isNotEmpty();
    assertThat(applicationContext.getBeanNamesForType(CamundaClientHealthIndicator.class))
        .isNotEmpty();
  }

  @Test
  void shouldWireWorkerRegistrationAndLifecycleOnSingleClientPath() {
    // job-worker registration infrastructure and the per-client lifecycle producer are present, so
    // @JobWorker methods are registered and the client lifecycle is driven as before
    assertThat(applicationContext.getBeanNamesForType(JobWorkerManager.class)).isNotEmpty();
    assertThat(applicationContext.getBeanNamesForType(MultiCamundaLifecycleEventProducer.class))
        .isNotEmpty();
  }
}
