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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.configuration.CamundaAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@TestPropertySource(
    properties = {
      "zeebe.client.cloud.clusterId=123-abc-456-def",
    })
@ContextConfiguration(
    classes = {
      CamundaAutoConfiguration.class,
      ZeebeClientStarterAutoConfigurationCloudTest.TestConfig.class
    })
public class ZeebeClientStarterAutoConfigurationCloudTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void testClientConfiguration() {
    final ZeebeClient client = applicationContext.getBean(ZeebeClient.class);
    assertThat(client.getConfiguration().getGatewayAddress())
        .isEqualTo("123-abc-456-def.bru-2.zeebe.camunda.io:443");
    assertThat(client.getConfiguration().getGrpcAddress().toString())
        .isEqualTo("https://123-abc-456-def.bru-2.zeebe.camunda.io:443");
    assertThat(client.getConfiguration().getRestAddress().toString())
        .isEqualTo("https://bru-2.zeebe.camunda.io:443/123-abc-456-def");
  }

  public static class TestConfig {

    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
