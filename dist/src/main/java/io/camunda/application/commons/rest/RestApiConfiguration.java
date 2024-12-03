/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.util.XmlUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.camunda.zeebe.gateway.rest")
@ConditionalOnRestGatewayEnabled
public class RestApiConfiguration {

  @Bean
  public XmlUtil xmlUtil(final ProcessDefinitionServices processDefinitionServices) {
    return new XmlUtil(processDefinitionServices);
  }
}
