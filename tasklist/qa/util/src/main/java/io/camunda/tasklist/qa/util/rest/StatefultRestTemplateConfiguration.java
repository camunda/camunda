/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util.rest;

import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StatefultRestTemplateConfiguration {

  @Value("${server.servlet.context-path:/}")
  private String contextPath;

  @Bean
  public BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory() {
    return (host, port) -> new StatefulRestTemplate(host, port, contextPath);
  }
}
