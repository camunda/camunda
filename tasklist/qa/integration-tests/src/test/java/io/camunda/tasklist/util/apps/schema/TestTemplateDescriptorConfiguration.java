/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util.apps.schema;

import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TestTemplateDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestTemplateDescriptorConfiguration {

  public static final String TEMPLATE_NAME = "test-template";

  @Bean
  public TestTemplateDescriptor testTemplateDescriptor() {
    return new TestTemplateDescriptor(
        TEMPLATE_NAME, getSchemaFilePath("tasklist-test-template-before.json"));
  }

  public static String getSchemaFilePath(final String filename) {
    return "/schema/%s/template/%s".formatted(TestUtil.isElasticSearch() ? "es" : "os", filename);
  }
}
