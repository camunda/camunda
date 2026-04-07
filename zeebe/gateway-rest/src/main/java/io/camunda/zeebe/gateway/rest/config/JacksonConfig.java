/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.function.Consumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

  @Bean("gatewayRestObjectMapperCustomizer")
  public Consumer<Jackson2ObjectMapperBuilder> gatewayRestObjectMapperCustomizer() {
    return builder ->
        builder
            .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .featuresToDisable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .postConfigurer(
                om ->
                    om.coercionConfigDefaults()
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
  }

  @Bean("gatewayRestObjectMapper")
  public ObjectMapper objectMapper() {
    final var builder = Jackson2ObjectMapperBuilder.json();
    builder
        .featuresToDisable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .modulesToInstall(new JavaTimeModule(), new Jdk8Module())
        .postConfigurer(
            om -> {
              // this also prevents coercion for string target types from non-string types
              om.coercionConfigDefaults()
                  .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
            });
    gatewayRestObjectMapperCustomizer().accept(builder);
    return builder.build();
  }
}
