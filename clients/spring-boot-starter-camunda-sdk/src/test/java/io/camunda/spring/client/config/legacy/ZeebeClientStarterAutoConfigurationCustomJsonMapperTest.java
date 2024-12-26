/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.config.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.spring.client.configuration.CamundaAutoConfiguration;
import io.camunda.spring.client.configuration.CamundaClientProdAutoConfiguration;
import io.camunda.spring.client.properties.CamundaClientProperties;
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

  @Autowired private io.camunda.client.api.JsonMapper jsonMapper;
  @Autowired private CamundaClientProdAutoConfiguration autoConfiguration;
  @Autowired private ApplicationContext applicationContext;

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
    final CamundaClient client = applicationContext.getBean(CamundaClient.class);
    final io.camunda.client.api.JsonMapper clientJsonMapper =
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
    public ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.JsonMapper jsonMapper() {
      return new ZeebeClientStarterAutoConfigurationCustomJsonMapperTest.JsonMapper();
    }
  }

  private static final class JsonMapper {}
}
