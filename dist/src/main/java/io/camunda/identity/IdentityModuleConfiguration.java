/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the Identity modules by using the the {@link
 * io.camunda.application.Profile#IDENTITY} profile, so that the appropriate Identity application
 * properties are applied.
 */
@ComponentScan(
    basePackages = {"io.camunda.identity"},
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.identity\\.starter\\..*")
    })
@ConfigurationPropertiesScan(basePackages = {"io.camunda.identity"})
@EnableAutoConfiguration
@Profile("identity")
public class IdentityModuleConfiguration {}
