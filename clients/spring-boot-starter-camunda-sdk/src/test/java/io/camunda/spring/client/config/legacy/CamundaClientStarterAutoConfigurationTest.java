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
package io.camunda.spring.client.config.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.JsonMapper;
import io.camunda.spring.client.configuration.CamundaAutoConfiguration;
import io.camunda.spring.client.configuration.CamundaClientProdAutoConfiguration;
import io.camunda.spring.client.configuration.JsonMapperConfiguration;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@TestPropertySource(
    properties = {
      "zeebe.client.broker.gatewayAddress=localhost:1234",
      "zeebe.client.broker.grpcAddress=https://localhost:1234",
      "zeebe.client.broker.restAddress=https://localhost:8088",
      "zeebe.client.requestTimeout=99s",
      "zeebe.client.job.timeout=99s",
      "zeebe.client.job.pollInterval=99s",
      "zeebe.client.worker.maxJobsActive=99",
      "zeebe.client.worker.threads=99",
      "zeebe.client.worker.defaultName=testName",
      "zeebe.client.worker.defaultType=testType",
      "zeebe.client.worker.override.foo.enabled=false",
      "zeebe.client.message.timeToLive=99s",
      "zeebe.client.security.certpath=aPath",
      "zeebe.client.security.plaintext=true"
    })
@ContextConfiguration(
    classes = {
      CamundaAutoConfiguration.class,
      CamundaClientStarterAutoConfigurationTest.TestConfig.class,
      JsonMapperConfiguration.class
    })
public class CamundaClientStarterAutoConfigurationTest {
  @MockitoBean CamundaClient camundaClient;
  @MockitoBean ZeebeClient zeebeClient;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private CamundaClientProdAutoConfiguration autoConfiguration;
  @Autowired private ApplicationContext applicationContext;

  @Test
  void getJsonMapper() {
    assertThat(jsonMapper).isNotNull();
    assertThat(autoConfiguration).isNotNull();

    final Map<String, JsonMapper> jsonMapperBeans =
        applicationContext.getBeansOfType(JsonMapper.class);
    final Object objectMapper = ReflectionTestUtils.getField(jsonMapper, "objectMapper");

    assertThat(jsonMapperBeans.size()).isEqualTo(1);
    assertThat(jsonMapperBeans.containsKey("camundaJsonMapper")).isTrue();
    assertThat(jsonMapperBeans.get("camundaJsonMapper")).isSameAs(jsonMapper);
    assertThat(objectMapper).isNotNull();
    assertThat(objectMapper).isInstanceOf(ObjectMapper.class);
    assertThat(((ObjectMapper) objectMapper).getDeserializationConfig()).isNotNull();
    assertThat(
            ((ObjectMapper) objectMapper)
                .getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES))
        .isFalse();
    assertThat(
            ((ObjectMapper) objectMapper)
                .getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
        .isFalse();
  }

  @Test
  void testClientConfiguration() {
    final CamundaClientConfiguration configuration =
        applicationContext.getBean(CamundaClientConfiguration.class);
    assertThat(configuration.getGatewayAddress()).isEqualTo("localhost:1234");
    assertThat(configuration.getGrpcAddress().toString()).isEqualTo("https://localhost:1234");
    assertThat(configuration.getRestAddress().toString()).isEqualTo("https://localhost:8088");
    assertThat(configuration.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(99));
    assertThat(configuration.getCaCertificatePath()).isEqualTo("aPath");
    assertThat(configuration.isPlaintextConnectionEnabled()).isFalse(); // grpc address is https
    assertThat(configuration.getDefaultJobWorkerMaxJobsActive()).isEqualTo(99);
    assertThat(configuration.getDefaultJobPollInterval()).isEqualTo(Duration.ofSeconds(99));
    assertThat(configuration.preferRestOverGrpc()).isFalse();
  }

  @EnableConfigurationProperties(CamundaClientProperties.class)
  public static class TestConfig {

    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
