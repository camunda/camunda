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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.LoadTesterApplication;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that {@code camunda.client.*} keys in {@code application.yaml} bind correctly onto
 * {@link CamundaClientProperties}. {@link io.camunda.zeebe.config.ConfigTest} covers the {@code
 * load-tester.*} namespace.
 */
@SpringBootTest(classes = LoadTesterApplication.class)
class CamundaClientConfigIT {

  @Autowired private CamundaClientProperties clientProps;

  @Test
  void shouldBindClientPropertiesFromApplicationYaml() {
    assertThat(clientProps.getZeebe().getGrpcAddress()).hasToString("http://localhost:26500");
    assertThat(clientProps.getZeebe().getRestAddress()).hasToString("http://localhost:8080");
    assertThat(clientProps.getZeebe().getPreferRestOverGrpc()).isFalse();
    assertThat(clientProps.getMode()).isEqualTo(ClientMode.selfManaged);
    assertThat(clientProps.getZeebe().getExecutionThreads()).isZero();

    final var workerDefaults = clientProps.getZeebe().getDefaults();
    assertThat(workerDefaults.getName()).isEqualTo("benchmark-worker");
    assertThat(workerDefaults.getType()).isEqualTo("benchmark-task");
    assertThat(workerDefaults.getStreamEnabled()).isTrue();
    assertThat(workerDefaults.getMaxJobsActive()).isEqualTo(30);
    assertThat(workerDefaults.getPollInterval()).hasSeconds(1);
    assertThat(workerDefaults.getTimeout()).hasMillis(1800);
  }
}
