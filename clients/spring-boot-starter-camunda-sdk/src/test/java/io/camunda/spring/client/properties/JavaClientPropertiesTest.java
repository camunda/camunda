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
package io.camunda.spring.client.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(
    properties = {
      "zeebe.client.gateway.address=localhost12345",
      "zeebe.client.broker.grpcAddress=https://localhost:1234",
      "zeebe.client.broker.restAddress=https://localhost:8088",
      "zeebe.client.job.pollinterval=99s",
      "zeebe.client.worker.name=testName",
      "zeebe.client.cloud.secret=processOrchestration"
    })
@ContextConfiguration(classes = JavaClientPropertiesTest.TestConfig.class)
public class JavaClientPropertiesTest {

  @Autowired private CamundaClientProperties properties;

  @Test
  public void hasGrpcAddress() {
    assertThat(properties.getGrpcAddress().toString()).isEqualTo("https://localhost:1234");
  }

  @Test
  public void hasRestAddress() {
    assertThat(properties.getRestAddress().toString()).isEqualTo("https://localhost:8088");
  }

  @Test
  public void hasWorkerName() {
    assertThat(properties.getWorker().getDefaults().getName()).isEqualTo("testName");
  }

  @Test
  public void hasJobPollInterval() {
    assertThat(properties.getWorker().getDefaults().getPollInterval())
        .isEqualTo(Duration.ofSeconds(99));
  }

  @Test
  public void hasCloudSecret() {
    assertThat(properties.getAuth().getClientSecret()).isEqualTo("processOrchestration");
  }

  @EnableConfigurationProperties(CamundaClientProperties.class)
  public static class TestConfig {
    @MockitoBean CamundaClient camundaClient;

    @Bean("jsonMapper")
    @ConditionalOnMissingBean(JsonMapper.class)
    public JsonMapper jsonMapper() {
      return new CamundaObjectMapper();
    }
  }

  @Nested
  class InvalidPropertiesTest {

    @Test
    void shouldThrowExceptionOnInvalidGrpcURI() {
      assertThatThrownBy(
              () -> {
                new CamundaClientProperties().setGrpcAddress(new URI("invalid:26500"));
              })
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("'grpcAddress' must be an absolute URI");
    }

    @Test
    void shouldThrowExceptionOnInvalidRestURI() {
      assertThatThrownBy(
              () -> {
                new CamundaClientProperties().setRestAddress(new URI("invalid:8088"));
              })
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("'restAddress' must be an absolute URI");
    }
  }
}
