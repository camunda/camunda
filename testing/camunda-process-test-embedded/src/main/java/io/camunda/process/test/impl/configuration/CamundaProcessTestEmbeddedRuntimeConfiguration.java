/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.configuration;

import io.camunda.process.test.impl.runtime.CamundaSpringProcessTestRuntimeBuilder;
import io.camunda.process.test.impl.runtime.EmbeddedRuntimeBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CamundaProcessTestEmbeddedRuntimeConfiguration {

  @Bean
  @Primary
  public CamundaSpringProcessTestRuntimeBuilder embeddedCamundaSpringProcessTestRuntimeBuilder() {
    return new EmbeddedRuntimeBuilder();
  }
}
