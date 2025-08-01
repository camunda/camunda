/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import java.util.Set;

public class TestHelper {

  public static void setupElasticsearchUrl(TestSpringApplication application, String url) {
    final Set<String> properties =
        Set.of(
            "camunda.data.secondary-storage.elasticsearch.url",
            "camunda.database.url",
            "camunda.operate.zeebeElasticsearch.url",
            "camunda.operate.elasticsearch.url",
            "camunda.tasklist.elasticsearch.url",
            "camunda.tasklist.zeebeElasticsearch.url");

    for (final String property : properties) {
      application.withProperty(property, url);
    }
  }
}
