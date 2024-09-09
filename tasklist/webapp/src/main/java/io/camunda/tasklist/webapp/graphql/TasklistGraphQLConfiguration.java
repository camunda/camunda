/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.graphql;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.execution.instrumentation.Instrumentation;
import graphql.kickstart.execution.config.ObjectMapperProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TasklistGraphQLConfiguration {

  @Bean
  public ObjectMapperProvider objectMapperProvider(
      @Qualifier("tasklistObjectMapper") final ObjectMapper objectMapper) {
    final InjectableValues.Std injectableValues = new InjectableValues.Std();
    injectableValues.addValue(ObjectMapper.class, objectMapper);
    objectMapper.setInjectableValues(injectableValues);
    return () -> objectMapper;
  }

  @Bean
  @ConditionalOnProperty(
      value = "camunda.tasklist.graphql-introspection-enabled",
      havingValue = "false",
      matchIfMissing = true)
  public Instrumentation disableIntrospectionInstrumentation() {
    return new DisableIntrospectionInstrumentation();
  }
}
