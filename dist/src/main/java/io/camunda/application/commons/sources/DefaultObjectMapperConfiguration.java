/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.sources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Configuration that provides a default ObjectMapper bean to be used. It applies the following
 * custom ObjectMapper customizers if they are present in the application context:
 *
 * <ul>
 *   <li>{@code StandardJackson2ObjectMapperBuilderCustomizer}
 *   <li>{@code operateObjectMapperCustomizer}
 *   <li>{@code tasklistObjectMapperCustomizer}
 *   <li>{@code gatewayRestObjectMapperCustomizer}
 * </ul>
 *
 * <b>Important:</b> customizer order matters as they can override the previous configurations!
 *
 * <p>Example of places where this default object mapper is used:
 *
 * <ul>
 *   <li>{@link io.camunda.authentication.handler.AuthFailureHandler}
 *   <li>{@link
 *       org.springframework.boot.actuate.autoconfigure.endpoint.jmx.JmxEndpointAutoConfiguration}
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class DefaultObjectMapperConfiguration {

  @Bean
  @Primary
  public ObjectMapper defaultObjectMapper(
      @Qualifier("operateObjectMapperCustomizer")
          final Optional<Consumer<Jackson2ObjectMapperBuilder>> operateCustomizer,
      @Qualifier("tasklistObjectMapperCustomizer")
          final Optional<Consumer<Jackson2ObjectMapperBuilder>> tasklistCustomizer,
      @Qualifier("gatewayRestObjectMapperCustomizer")
          final Optional<Consumer<Jackson2ObjectMapperBuilder>> gatewayRestCustomizer) {

    final var builder = Jackson2ObjectMapperBuilder.json();
    // Configure proper date/time serialization for date types
    builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // Apply optional customizers
    tasklistCustomizer.ifPresent(c -> c.accept(builder));
    operateCustomizer.ifPresent(c -> c.accept(builder));
    gatewayRestCustomizer.ifPresent(c -> c.accept(builder));
    return builder.build();
  }
}
