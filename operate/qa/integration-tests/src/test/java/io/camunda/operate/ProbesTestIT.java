/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.management.IndicesCheck;
import io.camunda.operate.qa.util.TestSchemaManager;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

class ProbesTestIT {

  @Nested
  @ExtendWith(SpringExtension.class)
  @SpringBootTest(
      classes = {
        TestApplication.class,
      },
      webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  class SchemaExistsIT {
    @Autowired private IndicesCheck indicesCheck;

    @Autowired private TestSchemaManager testSchemaManager;

    @DynamicPropertySource
    static void setProperties(final DynamicPropertyRegistry registry) {
      registry.add("camunda.database.indexPrefix", () -> TestUtil.createRandomString(10));
      registry.add("camunda.database.schema-manager.createSchema", () -> true);
      registry.add("camunda.operate.zeebe.compatibility.enabled", () -> true);
    }

    @Test
    public void testIsReady() {
      assertThat(indicesCheck.indicesArePresent()).isTrue();
    }

    @AfterEach
    public void after() {
      testSchemaManager.deleteSchemaQuietly();
    }
  }

  @Nested
  @ExtendWith(SpringExtension.class)
  @SpringBootTest(
      classes = {TestApplication.class},
      webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  class SchemaNotExistsIT {
    @Autowired private IndicesCheck indicesCheck;

    @DynamicPropertySource
    static void setProperties(final DynamicPropertyRegistry registry) {
      registry.add("camunda.database.indexPrefix", () -> TestUtil.createRandomString(10));
      registry.add("camunda.database.schema-manager.createSchema", () -> false);
      registry.add("camunda.operate.zeebe.compatibility.enabled", () -> true);
    }

    @Test
    public void testIsNotReady() {
      assertThat(indicesCheck.indicesArePresent()).isFalse();
    }
  }
}
