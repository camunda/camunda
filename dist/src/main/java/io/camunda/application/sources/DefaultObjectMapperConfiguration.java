/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.sources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.zeebe.gateway.protocol.rest.IntegerFilter;
import io.camunda.zeebe.gateway.protocol.rest.LongFilter;
import io.camunda.zeebe.gateway.protocol.rest.StringFilter;
import io.camunda.zeebe.gateway.rest.deserializer.IntegerFilterDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.LongFilterDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.StringFilterDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Configuration that provides a default ObjectMapper bean to be used when multiple custom
 * ObjectMappers are present in the application context: operateObjectMapper, tasklistObjectMapper.
 *
 * <p>To be removed once, custom object mappers are unified.
 *
 * <p>Example of places where this default object mapper is used:
 * <li>- {@link io.camunda.zeebe.shared.security.ProblemAuthFailureHandler}
 * <li>- {@link io.camunda.authentication.handler.AuthFailureHandler}
 * <li>- {@link
 *     org.springframework.boot.actuate.autoconfigure.endpoint.jmx.JmxEndpointAutoConfiguration}
 */
@Configuration(proxyBeanMethods = false)
public class DefaultObjectMapperConfiguration {
  @Bean
  @Primary
  @ConditionalOnBean(name = {"operateObjectMapper", "tasklistObjectMapper"})
  public ObjectMapper defaultObjectMapper() {
    final var om = Jackson2ObjectMapperBuilder.json().build();

    final var module = new SimpleModule("gateway-rest-module");
    module.addDeserializer(StringFilter.class, new StringFilterDeserializer(om));
    module.addDeserializer(LongFilter.class, new LongFilterDeserializer(om));
    module.addDeserializer(IntegerFilter.class, new IntegerFilterDeserializer(om));
    om.registerModule(module);

    return om;
  }
}
