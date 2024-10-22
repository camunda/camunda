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
import io.camunda.zeebe.gateway.protocol.rest.IntegerFilter;
import io.camunda.zeebe.gateway.protocol.rest.LongFilter;
import io.camunda.zeebe.gateway.protocol.rest.StringFilter;
import io.camunda.zeebe.gateway.rest.deserializer.IntegerFilterDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.LongFilterDeserializer;
import io.camunda.zeebe.gateway.rest.deserializer.StringFilterDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnRestGatewayEnabled
public class RestConfiguration {

  /** The base object mapper. Configurations should be added here! */
  @Bean
  public ObjectMapper objectMapperBase() {
    return new ObjectMapper();
  }

  /** Extends base OM with additional module for search query API deserializers */
  @Bean
  @Primary
  public ObjectMapper objectMapper(
      @Qualifier("objectMapperBase") final ObjectMapper objectMapperBase) {
    final var om = objectMapperBase.copy();
    final var module = new SimpleModule();
    module.addDeserializer(IntegerFilter.class, new IntegerFilterDeserializer(objectMapperBase));
    module.addDeserializer(LongFilter.class, new LongFilterDeserializer(objectMapperBase));
    module.addDeserializer(StringFilter.class, new StringFilterDeserializer(objectMapperBase));
    om.registerModule(module);
    return om;
  }
}
