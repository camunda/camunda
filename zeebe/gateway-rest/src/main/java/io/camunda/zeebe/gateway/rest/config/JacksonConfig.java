/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.camunda.zeebe.gateway.rest.serializer.LongSerializer;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class JacksonConfig {

  @Bean("gatewayRestObjectMapperCustomizer")
  public Consumer<Jackson2ObjectMapperBuilder> gatewayRestObjectMapperCustomizer() {
    final var module = new SimpleModule("gateway-rest-module");
    module.addSerializer(Long.class, new LongSerializer());
    return builder -> builder.modulesToInstall(modules -> modules.add(module));
  }

  @Bean("gatewayRestObjectMapper")
  public ObjectMapper objectMapper() {
    final var builder = Jackson2ObjectMapperBuilder.json();
    gatewayRestObjectMapperCustomizer().accept(builder);
    return builder.build();
  }

  @Bean("yamlObjectMapper")
  public ObjectMapper yamlObjectMapper() {
    final var builder = Jackson2ObjectMapperBuilder.yaml();
    builder.factory(new YAMLFactory());
    gatewayRestObjectMapperCustomizer().accept(builder);
    return builder.build();
  }

  // To avoid conflict between the default ObjectMapper and the custom one (yamlObjectMapper)
  @Bean
  public MappingJackson2HttpMessageConverter gatewayRestMessageConverter(
      final @Qualifier("gatewayRestObjectMapper") ObjectMapper gatewayRestObjectMapper) {
    return new MappingJackson2HttpMessageConverter(gatewayRestObjectMapper);
  }
}
