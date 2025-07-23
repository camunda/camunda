/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

/* In this class we test the new schema in presence of a mismatching legacy schema. */

@SpringBootTest(classes = TestHelper.class)
@TestPropertySource(
    properties = {
      // type
      "camunda.database.type=opensearch",
      "camunda.data.secondary-storage.type=elasticsearch",
      // url
      "camunda.database.url=http://old-url:4321",
      "camunda.data.secondary-storage.elasticsearch.url=http://new-url:4321"
    })
class AmbiguousMismatchingSchemasTest {

  @Autowired private Environment environment;
  @Autowired private UnifiedConfiguration unifiedConfiguration;

  @BeforeEach
  public void init() {
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @Test
  void testCamundaDataSecondaryStorageTypeThrowsException() {
    final RuntimeException actualException =
        assertThrows(
            RuntimeException.class,
            () -> unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getType());

    assertTrue(
        actualException.getMessage().contains(TestHelper.PROPERTIES_MUST_BE_REMOVED_SIGNATURE));
  }

  @Test
  void testCamundaDataSecondaryStorageElasticsearchUrlThrowsException() {
    final RuntimeException actualException =
        assertThrows(
            RuntimeException.class,
            () ->
                unifiedConfiguration
                    .getCamunda()
                    .getData()
                    .getSecondaryStorage()
                    .getElasticsearch()
                    .getUrl());

    assertTrue(
        actualException.getMessage().contains(TestHelper.PROPERTIES_MUST_BE_REMOVED_SIGNATURE));
  }
}
