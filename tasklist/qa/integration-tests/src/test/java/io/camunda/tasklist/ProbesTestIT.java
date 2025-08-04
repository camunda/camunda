/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.tasklist.qa.util.TestSchemaManager;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class ProbesTestIT extends TasklistIntegrationTest {

  @Nested
  @ExtendWith(SpringExtension.class)
  @SpringBootTest(
      classes = {TestApplication.class},
      webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  class SchemaExistsTest {
    @Autowired private IndexSchemaValidator indexSchemaValidator;

    @Autowired private TestSchemaManager testSchemaManager;

    @DynamicPropertySource
    static void setProperties(final DynamicPropertyRegistry registry) {
      registry.add("camunda.database.indexPrefix", () -> TestUtil.createRandomString(10));
      registry.add("camunda.database.schema-manager.createSchema", () -> true);
    }

    @Test
    public void testIsReady() {
      assertThat(indexSchemaValidator.schemaExists()).isTrue();
    }

    @AfterEach
    public void after() {
      testSchemaManager.deleteSchemaQuietly();
    }
  }

  @Nested
  @ExtendWith(SpringExtension.class)
  @SpringBootTest(
      classes = {
        TestApplication.class,
        UnifiedConfigurationHelper.class,
        UnifiedConfiguration.class
      },
      webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  class SchemaNotExistsTest {
    @Autowired private IndexSchemaValidator indexSchemaValidator;

    @DynamicPropertySource
    static void setProperties(final DynamicPropertyRegistry registry) {
      registry.add("camunda.database.indexPrefix", () -> TestUtil.createRandomString(10));
      registry.add("camunda.database.schema-manager.createSchema", () -> false);
    }

    @Test
    public void testIsNotReady() {
      assertThat(indexSchemaValidator.schemaExists()).isFalse();
    }
  }
}
