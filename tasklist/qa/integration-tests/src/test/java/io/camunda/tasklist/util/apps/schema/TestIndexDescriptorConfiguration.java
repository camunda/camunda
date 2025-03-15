/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util.apps.schema;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TestIndexDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestIndexDescriptorConfiguration {

  public static final String INDEX_NAME = "test";

  @Bean
  public TestIndexDescriptor testIndexDescriptor(final TasklistProperties tasklistProperties) {
    return new TestIndexDescriptor(
        INDEX_NAME,
        getSchemaFilePath("tasklist-test-before.json"),
        tasklistProperties.getIndexPrefix(),
        TestUtil.isElasticSearch());
  }

  public static String getSchemaFilePath(final String filename) {
    return "/schema/%s/index/%s".formatted(TestUtil.isElasticSearch() ? "es" : "os", filename);
  }
}
