/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rollingupdate;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;

public class OpensearchRollingUpdateIT extends SecondaryStorageRollingUpdateIT {
  @Override
  StorageTestCase getStorageTestCase() {
    return new StorageTestCase(
        "opensearch",
        SecondaryStorageType.opensearch,
        "opensearch",
        9200,
        TestSearchContainers::createDefaultOpensearchContainer,
        null,
        null);
  }
}
