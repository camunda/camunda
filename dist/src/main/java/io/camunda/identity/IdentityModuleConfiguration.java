/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity;

import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = "io.camunda.identity.webapp",
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Profile("identity")
@ConditionalOnSecondaryStorageEnabled
public class IdentityModuleConfiguration {}
