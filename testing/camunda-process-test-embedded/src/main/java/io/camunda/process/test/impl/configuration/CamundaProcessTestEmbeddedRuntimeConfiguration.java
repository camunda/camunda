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

@Configuration
public class CamundaProcessTestEmbeddedRuntimeConfiguration {

  @Bean
  public CamundaSpringProcessTestRuntimeBuilder camundaSpringProcessTestRuntimeBuilder() {
    return new EmbeddedRuntimeBuilder();
  }
}
