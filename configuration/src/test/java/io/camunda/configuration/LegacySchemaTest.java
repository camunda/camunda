/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

/* In this class we test the legacy schema in absence of the new schema */

@SpringBootTest(classes = TestHelper.class)
@TestPropertySource(
    properties = {
      "camunda.database.type=elasticsearch",
      "camunda.database.url=http://some-url:/1234",
    })
class LegacySchemaTest {

  @Autowired private Environment environment;
  @Autowired private UnifiedConfiguration unifiedConfiguration;

  @BeforeEach
  public void init() {
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @Test
  void testCamundaDataSecondaryStorageTypeThrowsException() {
    final IllegalStateException actualException =
        assertThrows(
            IllegalStateException.class,
            () -> {
              unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getType();
            });

    assertTrue(actualException.getMessage().contains(TestHelper.PROPERTY_MUST_BE_SET_SIGNATURE));
  }

  @Test
  void testCamundaDataSecondaryStorageElasticsearchUrlThrowsException() {
    final IllegalStateException actualException =
        assertThrows(
            IllegalStateException.class,
            () -> {
              unifiedConfiguration
                  .getCamunda()
                  .getData()
                  .getSecondaryStorage()
                  .getElasticsearch()
                  .getUrl();
            });

    assertTrue(actualException.getMessage().contains(TestHelper.PROPERTY_MUST_BE_SET_SIGNATURE));
  }
}
