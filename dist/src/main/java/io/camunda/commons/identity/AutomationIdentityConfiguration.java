/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.commons.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Configuration to include the Identity based services and repositories in the Camunda application.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = {"io.camunda.identity"},
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.identity\\.starter\\..*")
    })
@ConfigurationPropertiesScan(basePackages = {"io.camunda.identity"})
@EnableJpaRepositories("io.camunda.identity")
@EntityScan("io.camunda.identity")
@Import(
    value = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      DataSourceTransactionManagerAutoConfiguration.class
    })
@ConditionalOnProperty(
    name = "zeebe.broker.gateway.enable",
    havingValue = "true",
    matchIfMissing = true)
public class AutomationIdentityConfiguration {}
