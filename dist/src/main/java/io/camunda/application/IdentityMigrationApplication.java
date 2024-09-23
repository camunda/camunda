/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.identity.migration.MigrationRunner;
import io.camunda.zeebe.gateway.GatewayModuleConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.AbstractEnvironment;

@SpringBootApplication(scanBasePackages = {"io.camunda.identity.migration"})
@Import(value = {CommonsModuleConfiguration.class, GatewayModuleConfiguration.class})
public class IdentityMigrationApplication {

  @Autowired MigrationRunner migrationRunner;

  public static void main(final String[] args) {
    System.setProperty(AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME, "identity-migration");
    SpringApplication.run(IdentityMigrationApplication.class, args);
  }
}
