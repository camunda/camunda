/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Set;

public class Opensearch extends SecondaryStorageDatabase {

  @Override
  protected String prefix() {
    return "camunda.data.secondary-storage.opensearch";
  }

  @Override
  protected Set<String> legacyUrlProperties() {
    return Set.of(
        "camunda.database.url",
        "camunda.operate.opensearch.url",
        "camunda.operate.zeebeOpensearch.url",
        "camunda.tasklist.opensearch.url",
        "camunda.tasklist.zeebeOpensearch.url");
  }
}
