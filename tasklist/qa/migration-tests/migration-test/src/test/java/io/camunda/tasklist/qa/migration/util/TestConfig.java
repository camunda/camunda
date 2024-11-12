/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration.util;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.tasklist.qa",
      "io.camunda.tasklist.schema.templates",
      "io.camunda.tasklist.property",
      "io.camunda.tasklist.schema.indices"
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class TestConfig {}
