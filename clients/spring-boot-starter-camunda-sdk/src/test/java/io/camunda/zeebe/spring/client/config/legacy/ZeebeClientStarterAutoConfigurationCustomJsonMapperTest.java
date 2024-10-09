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
package io.camunda.zeebe.spring.client.config.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.spring.client.configuration.CamundaAutoConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientProdAutoConfiguration;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.common.json.SdkObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(SpringExtension.class)
@TestPropertySource(
    properties = {
      "zeebe.client.broker.gatewayAddress=localhost:1234",
      "zeebe.client.broker.grpcAddress=https://localhost:1234",
      "zeebe.client.broker.restAddress=https://localhost:8080",
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
      ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.TestConfig.class,
      CamundaAutoConfiguration.class
    })
public class ZeebeClientStarterAutoConfigurationCustomJsonMapperTest {

  @Autowired private io.camunda.zeebe.client.api.JsonMapper jsonMapper;
  @Autowired private ZeebeClientProdAutoConfiguration autoConfiguration;
  @Autowired private ApplicationContext applicationContext;

  @Test
  void getJsonMapper() {
    assertThat(jsonMapper).isNotNull();
    assertThat(autoConfiguration).isNotNull();

    final Map<String, io.camunda.zeebe.client.api.JsonMapper> jsonMapperBeans =
        applicationContext.getBeansOfType(io.camunda.zeebe.client.api.JsonMapper.class);
    final Object objectMapper = ReflectionTestUtils.getField(jsonMapper, "objectMapper");

    assertThat(jsonMapperBeans.size()).isEqualTo(2);
    assertThat(jsonMapperBeans.containsKey("overridingJsonMapper")).isTrue();
    assertThat(jsonMapperBeans.get("overridingJsonMapper")).isSameAs(jsonMapper);
    assertThat(jsonMapperBeans.containsKey("aSecondJsonMapper")).isTrue();
    assertThat(jsonMapperBeans.get("aSecondJsonMapper")).isNotSameAs(jsonMapper);
    assertThat(objectMapper).isNotNull();
    assertThat(objectMapper).isInstanceOf(ObjectMapper.class);
    assertThat(((ObjectMapper) objectMapper).getDeserializationConfig()).isNotNull();
    assertThat(
            ((ObjectMapper) objectMapper)
                .getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES))
        .isTrue();
    assertThat(
            ((ObjectMapper) objectMapper)
                .getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
        .isTrue();
  }

  @Test
  void testClientConfiguration() {
    final ZeebeClient client = applicationContext.getBean(ZeebeClient.class);
    final io.camunda.zeebe.client.api.JsonMapper clientJsonMapper =
        AopTestUtils.getUltimateTargetObject(client.getConfiguration().getJsonMapper());
    assertThat(clientJsonMapper).isSameAs(jsonMapper);
    assertThat(clientJsonMapper).isSameAs(applicationContext.getBean("overridingJsonMapper"));
    assertThat(client.getConfiguration().getGatewayAddress()).isEqualTo("localhost:1234");
    assertThat(client.getConfiguration().getGrpcAddress().toString())
        .isEqualTo("https://localhost:1234");
    assertThat(client.getConfiguration().getRestAddress().toString())
        .isEqualTo("https://localhost:8080");
    assertThat(client.getConfiguration().getDefaultRequestTimeout())
        .isEqualTo(Duration.ofSeconds(99));
    assertThat(client.getConfiguration().getCaCertificatePath()).isEqualTo("aPath");
    assertThat(client.getConfiguration().isPlaintextConnectionEnabled()).isTrue();
    assertThat(client.getConfiguration().getDefaultJobWorkerMaxJobsActive()).isEqualTo(99);
    assertThat(client.getConfiguration().getDefaultJobPollInterval())
        .isEqualTo(Duration.ofSeconds(99));
    assertThat(client.getConfiguration().preferRestOverGrpc()).isFalse();
  }

  @EnableConfigurationProperties(CamundaClientProperties.class)
  public static class TestConfig {
    @Bean
    public io.camunda.zeebe.spring.common.json.JsonMapper commonJsonMapper() {
      return new SdkObjectMapper();
    }

    @Primary
    @Bean(name = "overridingJsonMapper")
    public io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper() {
      final ObjectMapper objectMapper =
          new ObjectMapper()
              .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true)
              .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
      return new ZeebeObjectMapper(objectMapper);
    }

    @Bean(name = "aSecondJsonMapper")
    public io.camunda.zeebe.client.api.JsonMapper aSecondJsonMapper() {
      final ObjectMapper objectMapper =
          new ObjectMapper()
              .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true)
              .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
      return new ZeebeObjectMapper(objectMapper);
    }

    @Bean(name = "jsonMapper")
    public ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.JsonMapper jsonMapper() {
      return new ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.JsonMapper();
    }
  }

  private static final class JsonMapper {}
}
