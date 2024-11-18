/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.zeebe.gateway.protocol.rest.BasicLongFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.IntegerFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.LongFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceStateFilterProperty;
import io.camunda.zeebe.gateway.protocol.rest.StringFilterProperty;
import io.camunda.zeebe.gateway.rest.deserializer.BasicLongFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.IntegerFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.LongFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.ProcessInstanceStateFilterPropertyDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.StringFilterPropertyDeserializer;
import java.util.function.Consumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

  @Bean("gatewayRestObjectMapperCustomizer")
  public Consumer<Jackson2ObjectMapperBuilder> gatewayRestObjectMapperCustomizer() {
    final var module = new SimpleModule("gateway-rest-module");
    module.addDeserializer(LongFilterProperty.class, new LongFilterPropertyDeserializer());
    module.addDeserializer(
        BasicLongFilterProperty.class, new BasicLongFilterPropertyDeserializer());
    module.addDeserializer(IntegerFilterProperty.class, new IntegerFilterPropertyDeserializer());
    module.addDeserializer(StringFilterProperty.class, new StringFilterPropertyDeserializer());
    module.addDeserializer(
        ProcessInstanceStateFilterProperty.class,
        new ProcessInstanceStateFilterPropertyDeserializer());
    return builder -> builder.modulesToInstall(modules -> modules.add(module));
  }

  @Bean("gatewayRestObjectMapper")
  public ObjectMapper objectMapper() {
    final var builder = Jackson2ObjectMapperBuilder.json();
    gatewayRestObjectMapperCustomizer().accept(builder);
    return builder.build();
  }
}
