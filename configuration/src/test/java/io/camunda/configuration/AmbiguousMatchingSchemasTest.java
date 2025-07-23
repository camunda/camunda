/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

/* In this class we test the new schema in presence of a matching legacy schema. */

@SpringBootTest(classes = TestHelper.class)
@TestPropertySource(
    properties = {
      // type
      "camunda.database.type=elasticsearch",
      "camunda.data.secondary-storage.type=elasticsearch",
      // url
      "camunda.database.url=http://expected-url:4321",
      "camunda.data.secondary-storage.elasticsearch.url=http://expected-url:4321"
    })
class AmbiguousMatchingSchemasTest {

  @Autowired private Environment environment;
  @Autowired private UnifiedConfiguration unifiedConfiguration;

  @BeforeEach
  public void init() {
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @Test
  void testCamundaDataSecondaryStorageType() {
    final SecondaryStorageType expectedUrl = SecondaryStorageType.elasticsearch;
    final SecondaryStorageType actualUrl =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getType();
    assertEquals(expectedUrl, actualUrl);
  }

  @Test
  void testCamundaDataSecondaryStorageElasticsearchUrl() {
    final String expectedUrl = "http://expected-url:4321";
    final String actualUrl =
        unifiedConfiguration
            .getCamunda()
            .getData()
            .getSecondaryStorage()
            .getElasticsearch()
            .getUrl();
    assertEquals(expectedUrl, actualUrl);
  }
}
