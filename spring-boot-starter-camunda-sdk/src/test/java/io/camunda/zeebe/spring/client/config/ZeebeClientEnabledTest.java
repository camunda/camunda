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
package io.camunda.zeebe.spring.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.configuration.JsonMapperConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientProdAutoConfiguration;
import io.camunda.zeebe.spring.client.properties.CommonConfigurationProperties;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

public class ZeebeClientEnabledTest {

  @Nested
  @SpringBootTest(
      classes = ZeebeClientProdAutoConfiguration.class,
      properties = {"zeebe.client.enabled=false"})
  @EnableConfigurationProperties({
    ZeebeClientConfigurationProperties.class,
    CommonConfigurationProperties.class
  })
  class CurrentConfiguration {
    @Autowired(required = false)
    ZeebeClient zeebeClient;

    @Test
    void shouldNotEnableCamundaClient() {
      assertThat(zeebeClient).isNull();
    }
  }

  @Nested
  @SpringBootTest(classes = {ZeebeClientProdAutoConfiguration.class, JsonMapperConfiguration.class})
  @EnableConfigurationProperties({
    ZeebeClientConfigurationProperties.class,
    CommonConfigurationProperties.class
  })
  class NegativeTest {
    @Autowired(required = false)
    ZeebeClient zeebeClient;

    @Test
    void shouldEnableCamundaClient() {
      assertThat(zeebeClient).isNotNull();
    }
  }
}
