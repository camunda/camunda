/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.commons.identity;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration to include the Identity based services and repositories in the Camunda application.
 */
@Configuration(proxyBeanMethods = false)
// @ComponentScan(basePackages = {"io.camunda.identity.automation"})
// @ConfigurationPropertiesScan(basePackages = {"io.camunda.identity.automation"})
// @EnableJpaRepositories("io.camunda.identity.automation")
// @EntityScan("io.camunda.identity.automation")
// @Import(
//    value = {
//      DataSourceAutoConfiguration.class,
//      HibernateJpaAutoConfiguration.class,
//      DataSourceTransactionManagerAutoConfiguration.class
//    })
// @ConditionalOnProperty(
//    name = "zeebe.broker.gateway.enable",
//    havingValue = "true",
//    matchIfMissing = true)
public class AutomationIdentityConfiguration {}
