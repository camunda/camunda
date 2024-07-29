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
package io.camunda.zeebe.spring.client.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@TestPropertySource(
    properties = {
      "zeebe.client.gateway.address=localhost12345",
      "zeebe.client.broker.grpcAddress=https://localhost:1234",
      "zeebe.client.broker.restAddress=https://localhost:8080",
      "zeebe.client.job.pollinterval=99s",
      "zeebe.client.worker.name=testName",
      "zeebe.client.cloud.secret=processOrchestration"
    })
@ContextConfiguration(classes = JavaClientPropertiesTest.TestConfig.class)
public class JavaClientPropertiesTest {

  @Autowired private ZeebeClientConfigurationProperties properties;

  @Test
  public void hasBrokerContactPoint() {
    assertThat(PropertiesUtil.getZeebeGatewayAddress(properties)).isEqualTo("localhost12345");
  }

  @Test
  public void hasDeprecatedGatewayAddress() {
    assertThat(properties.getGatewayAddress()).isEqualTo("localhost12345");
  }

  @Test
  public void hasGrpcAddress() {
    assertThat(properties.getGrpcAddress().toString()).isEqualTo("https://localhost:1234");
  }

  @Test
  public void hasRestAddress() {
    assertThat(properties.getRestAddress().toString()).isEqualTo("https://localhost:8080");
  }

  @Test
  public void hasWorkerName() {
    assertThat(properties.getWorker().getDefaultName()).isEqualTo("testName");
  }

  @Test
  public void hasJobPollInterval() {
    assertThat(properties.getJob().getPollInterval()).isEqualTo(Duration.ofSeconds(99));
  }

  @Test
  public void hasCloudSecret() {
    assertThat(properties.getCloud().getClientSecret()).isEqualTo("processOrchestration");
  }

  @EnableConfigurationProperties(ZeebeClientConfigurationProperties.class)
  public static class TestConfig {
    @Bean("jsonMapper")
    @ConditionalOnMissingBean(JsonMapper.class)
    public JsonMapper jsonMapper() {
      return new ZeebeObjectMapper();
    }
  }
}
