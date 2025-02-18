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
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.spring.client.configuration.CamundaAutoConfiguration;
import io.camunda.spring.client.configuration.CamundaClientProdAutoConfiguration;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.client.ZeebeClient;
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/legacy/CamundaClientStarterAutoConfigurationCustomJsonMapperTest.java
=======
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.spring.client.configuration.CamundaAutoConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientProdAutoConfiguration;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.common.json.SdkObjectMapper;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/legacy/ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.java
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/legacy/CamundaClientStarterAutoConfigurationCustomJsonMapperTest.java
import org.springframework.boot.test.mock.mockito.MockBean;
=======
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/legacy/ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.java
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/legacy/CamundaClientStarterAutoConfigurationCustomJsonMapperTest.java
=======
import org.springframework.test.context.bean.override.mockito.MockitoBean;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/legacy/ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.java
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
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
      CamundaClientStarterAutoConfigurationCustomJsonMapperTest.TestConfig.class,
      CamundaAutoConfiguration.class
    })
public class CamundaClientStarterAutoConfigurationCustomJsonMapperTest {

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/legacy/CamundaClientStarterAutoConfigurationCustomJsonMapperTest.java
  @Autowired private io.camunda.client.api.JsonMapper jsonMapper;
  @Autowired private CamundaClientProdAutoConfiguration autoConfiguration;
=======
  @MockitoBean ZeebeClient zeebeClient;
  @Autowired private io.camunda.zeebe.client.api.JsonMapper jsonMapper;
  @Autowired private ZeebeClientProdAutoConfiguration autoConfiguration;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/legacy/ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.java
  @Autowired private ApplicationContext applicationContext;
  @Autowired private ZeebeClientConfiguration zeebeClientConfiguration;

  @Test
  void getJsonMapper() {
    assertThat(jsonMapper).isNotNull();
    assertThat(autoConfiguration).isNotNull();

    final Map<String, io.camunda.client.api.JsonMapper> jsonMapperBeans =
        applicationContext.getBeansOfType(io.camunda.client.api.JsonMapper.class);
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
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/legacy/CamundaClientStarterAutoConfigurationCustomJsonMapperTest.java
    final CamundaClientConfiguration configuration =
        applicationContext.getBean(CamundaClientConfiguration.class);
    final io.camunda.client.api.JsonMapper clientJsonMapper =
        AopTestUtils.getUltimateTargetObject(configuration.getJsonMapper());
    assertThat(clientJsonMapper).isSameAs(jsonMapper);
    assertThat(clientJsonMapper).isSameAs(applicationContext.getBean("overridingJsonMapper"));
    assertThat(configuration.getGatewayAddress()).isEqualTo("localhost:1234");
    assertThat(configuration.getGrpcAddress().toString()).isEqualTo("https://localhost:1234");
    assertThat(configuration.getRestAddress().toString()).isEqualTo("https://localhost:8080");
    assertThat(configuration.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(99));
    assertThat(configuration.getCaCertificatePath()).isEqualTo("aPath");
    assertThat(configuration.isPlaintextConnectionEnabled())
        .isFalse(); // because the grpc address points to https
    assertThat(configuration.getDefaultJobWorkerMaxJobsActive()).isEqualTo(99);
    assertThat(configuration.getDefaultJobPollInterval()).isEqualTo(Duration.ofSeconds(99));
    assertThat(configuration.preferRestOverGrpc()).isFalse();
=======
    final io.camunda.zeebe.client.api.JsonMapper clientJsonMapper =
        AopTestUtils.getUltimateTargetObject(zeebeClientConfiguration.getJsonMapper());
    assertThat(clientJsonMapper).isSameAs(jsonMapper);
    assertThat(clientJsonMapper).isSameAs(applicationContext.getBean("overridingJsonMapper"));
    assertThat(zeebeClientConfiguration.getGatewayAddress()).isEqualTo("localhost:1234");
    assertThat(zeebeClientConfiguration.getGrpcAddress().toString())
        .isEqualTo("https://localhost:1234");
    assertThat(zeebeClientConfiguration.getRestAddress().toString())
        .isEqualTo("https://localhost:8080");
    assertThat(zeebeClientConfiguration.getDefaultRequestTimeout())
        .isEqualTo(Duration.ofSeconds(99));
    assertThat(zeebeClientConfiguration.getCaCertificatePath()).isEqualTo("aPath");
    assertThat(zeebeClientConfiguration.isPlaintextConnectionEnabled()).isFalse();
    assertThat(zeebeClientConfiguration.getDefaultJobWorkerMaxJobsActive()).isEqualTo(99);
    assertThat(zeebeClientConfiguration.getDefaultJobPollInterval())
        .isEqualTo(Duration.ofSeconds(99));
    assertThat(zeebeClientConfiguration.preferRestOverGrpc()).isFalse();
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/legacy/ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.java
  }

  @EnableConfigurationProperties(CamundaClientProperties.class)
  public static class TestConfig {

    @MockBean CamundaClient camundaClient;
    @MockBean ZeebeClient zeebeClient;

    @Primary
    @Bean(name = "overridingJsonMapper")
    public io.camunda.client.api.JsonMapper zeebeJsonMapper() {
      final ObjectMapper objectMapper =
          new ObjectMapper()
              .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true)
              .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
      return new CamundaObjectMapper(objectMapper);
    }

    @Bean(name = "aSecondJsonMapper")
    public io.camunda.client.api.JsonMapper aSecondJsonMapper() {
      final ObjectMapper objectMapper =
          new ObjectMapper()
              .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true)
              .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
      return new CamundaObjectMapper(objectMapper);
    }

    @Bean(name = "jsonMapper")
    public CamundaClientStarterAutoConfigurationCustomJsonMapperTest.JsonMapper jsonMapper() {
      return new CamundaClientStarterAutoConfigurationCustomJsonMapperTest.JsonMapper();
    }
  }

  private static final class JsonMapper {}
}
