/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.identity.automation.config.IdentityConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Configuration to include the Identity based services and repositories in the Camunda application.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.identity.automation"})
@EnableConfigurationProperties(IdentityConfiguration.class)
@EnableJpaRepositories(
    basePackages = "io.camunda.identity.automation",
    enableDefaultTransactions = false)
@EntityScan("io.camunda.identity.automation")
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
