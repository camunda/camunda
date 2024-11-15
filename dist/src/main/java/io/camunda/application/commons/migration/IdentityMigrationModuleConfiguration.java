/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.commons.service.CamundaServicesConfiguration;
import io.camunda.application.commons.service.ServiceSecurityConfiguration;
import io.camunda.zeebe.gateway.GatewayModuleConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.migration.identity"})
@Profile(MigrationProfilePostProcessor.IDENTITY_MIGRATION_PROFILE)
@Import({
  CommonsModuleConfiguration.class,
  GatewayModuleConfiguration.class,
  ServiceSecurityConfiguration.class,
  CamundaServicesConfiguration.class
})
public class IdentityMigrationModuleConfiguration {}
