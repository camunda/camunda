/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.configuration;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.spring.client.event.CamundaLifecycleEventProducer;
import io.camunda.spring.client.testsupport.CamundaSpringProcessTestContext;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Enabled by META-INF of Spring Boot Starter to provide beans for Camunda Clients */
@Configuration
@ImportAutoConfiguration({
  CamundaClientProdAutoConfiguration.class,
  CamundaClientAllAutoConfiguration.class,
  CamundaActuatorConfiguration.class,
  MetricsDefaultConfiguration.class,
  JsonMapperConfiguration.class
})
@AutoConfigureAfter(JacksonAutoConfiguration.class)
public class CamundaAutoConfiguration {

  public static final ObjectMapper DEFAULT_OBJECT_MAPPER =
      new ObjectMapper()
          .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);

  @Bean
  @ConditionalOnMissingBean(
      CamundaSpringProcessTestContext
          .class) // only run if we are not running in a test case - as otherwise the lifecycle
  // is controlled by the test
  public CamundaLifecycleEventProducer zeebeLifecycleEventProducer(
      final CamundaClient client, final ApplicationEventPublisher publisher) {
    return new CamundaLifecycleEventProducer(client, publisher);
  }
}
