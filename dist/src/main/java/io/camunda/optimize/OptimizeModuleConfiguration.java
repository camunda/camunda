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
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the Optimize modules by using the the {@link
 * io.camunda.application.Profile#OPTIMIZE} profile, so that the appropriate Optimize application
 * properties are applied.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = "io.camunda.optimize",
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Profile("optimize")
public class OptimizeModuleConfiguration {

  public OptimizeModuleConfiguration() {}
}
