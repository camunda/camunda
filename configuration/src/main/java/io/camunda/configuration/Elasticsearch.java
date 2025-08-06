/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Set;

public class Elasticsearch extends SecondaryStorageDatabase {

  @Override
  protected String prefix() {
    return "camunda.data.secondary-storage.elasticsearch";
  }

  @Override
  protected Set<String> legacyUrlProperties() {
    return Set.of(
        "camunda.database.url",
        "camunda.operate.elasticsearch.url",
        "camunda.operate.zeebeElasticsearch.url",
        "camunda.tasklist.elasticsearch.url",
        "camunda.tasklist.zeebeElasticsearch.url");
  }
}
