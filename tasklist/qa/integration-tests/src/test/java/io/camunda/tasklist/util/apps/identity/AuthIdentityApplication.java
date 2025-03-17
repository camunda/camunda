/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util.apps.identity;

import io.camunda.authentication.tenant.TenantService;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.tasklist.util.TestApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@SpringBootApplication
@ComponentScan(
    basePackages = "io.camunda.tasklist.webapp.security.identity",
    includeFilters = {
      @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.graphql\\..*")
    },
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.util\\.apps\\..*"),
      @ComponentScan.Filter(
          type = FilterType.ASSIGNABLE_TYPE,
          value = TasklistModuleConfiguration.class),
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class AuthIdentityApplication extends TestApplication {

  @MockBean private TenantService tenantService;
}
