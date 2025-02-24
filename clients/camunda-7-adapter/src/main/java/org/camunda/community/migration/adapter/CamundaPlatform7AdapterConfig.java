/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter;

import org.camunda.bpm.client.spring.annotation.EnableExternalTaskClient;
import org.camunda.bpm.client.spring.impl.client.ClientConfiguration;
import org.camunda.bpm.engine.ArtifactFactory;
import org.camunda.bpm.engine.impl.el.JuelExpressionManager;
import org.camunda.bpm.engine.spring.SpringArtifactFactory;
import org.camunda.bpm.engine.spring.SpringExpressionManager;
import org.camunda.bpm.impl.juel.ExpressionFactoryImpl;
import org.camunda.bpm.impl.juel.SimpleContext;
import org.camunda.bpm.impl.juel.jakarta.el.ELContext;
import org.camunda.bpm.impl.juel.jakarta.el.ExpressionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.camunda.community.migration")
@EnableExternalTaskClient(baseUrl = "http://localhost", disableAutoFetching = true)
public class CamundaPlatform7AdapterConfig {

  @Bean
  public ExternalTaskWorkerRegistration workerRegistration() {
    final ClientConfiguration configuration = new ClientConfiguration();
    configuration.setBaseUrl("http://localhost");

    return new ExternalTaskWorkerRegistration(configuration);
  }

  @Bean
  @ConditionalOnMissingBean
  public ExpressionFactory expressionFactory() {
    return new ExpressionFactoryImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public ELContext elContext() {
    return new SimpleContext();
  }

  @Bean
  @ConditionalOnMissingBean
  public JuelExpressionManager expressionManager(ApplicationContext applicationContext) {
    return new SpringExpressionManager(applicationContext);
  }

  @Bean
  @ConditionalOnMissingBean
  public ArtifactFactory artifactFactory(ApplicationContext applicationContext) {
    return new SpringArtifactFactory(applicationContext);
  }
}
