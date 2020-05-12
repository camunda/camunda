/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util.rest;

import java.util.function.BiFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StatefultRestTemplateConfiguration {

  @Bean
  public BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory() {
    return (host, port) -> new StatefulRestTemplate(host, port);
  }

}
