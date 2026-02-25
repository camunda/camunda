/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.search.schema.SchemaManagerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class SchemaReadinessCheckConfiguration {

  @Bean
  @Qualifier(SchemaReadinessCheck.SCHEMA_READINESS_CHECK)
  public SchemaReadinessCheck schemaReadinessCheck(
      final SchemaManagerContainer schemaManagerContainer) {
    return new SchemaReadinessCheck(schemaManagerContainer);
  }
}
