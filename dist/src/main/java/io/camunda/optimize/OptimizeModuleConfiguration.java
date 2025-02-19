/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the Operate modules by using the the {@link
 * io.camunda.application.Profile#OPERATE} profile, so that the appropriate Operate application
 * properties are applied.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = "io.camunda.optimize",
    excludeFilters = {})
@Profile("optimize")
public class OptimizeModuleConfiguration {

  public OptimizeModuleConfiguration() {}
}
