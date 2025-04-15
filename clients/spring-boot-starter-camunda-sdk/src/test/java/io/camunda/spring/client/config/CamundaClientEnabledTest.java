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
package io.camunda.spring.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.spring.client.configuration.CamundaClientProdAutoConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

public class CamundaClientEnabledTest {

  @Nested
  @SpringBootTest(
      classes = CamundaClientProdAutoConfiguration.class,
      properties = {"camunda.client.enabled=false"})
  class CurrentConfiguration {
    @Autowired(required = false)
    CamundaClient camundaClient;

    @Test
    void shouldNotEnableCamundaClient() {
      assertThat(camundaClient).isNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientProdAutoConfiguration.class,
      properties = {"camunda.client.zeebe.enabled=false"})
  class ZeebeClientConfiguration {
    @Autowired(required = false)
    CamundaClient camundaClient;

    @Test
    void shouldNotEnableCamundaClient() {
      assertThat(camundaClient).isNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientProdAutoConfiguration.class,
      properties = {"zeebe.client.enabled=false"})
  class LegacyConfiguration {
    @Autowired(required = false)
    CamundaClient camundaClient;

    @Test
    void shouldNotEnableCamundaClient() {
      assertThat(camundaClient).isNull();
    }
  }

  @Nested
  @SpringBootTest(classes = CamundaClientProdAutoConfiguration.class)
  class DefaultTest {
    @Autowired(required = false)
    CamundaClient camundaClient;

    @Test
    void shouldEnableCamundaClient() {
      assertThat(camundaClient).isNotNull();
    }
  }
}
