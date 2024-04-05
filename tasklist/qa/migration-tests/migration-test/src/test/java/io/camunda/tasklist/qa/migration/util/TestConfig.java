/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
