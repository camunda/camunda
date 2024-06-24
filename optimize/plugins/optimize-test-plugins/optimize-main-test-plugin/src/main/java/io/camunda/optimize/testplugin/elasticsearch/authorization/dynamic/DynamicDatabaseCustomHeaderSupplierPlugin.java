/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.elasticsearch.authorization.dynamic;

import io.camunda.optimize.plugin.elasticsearch.CustomHeader;
import io.camunda.optimize.plugin.elasticsearch.DatabaseCustomHeaderSupplier;

public class DynamicDatabaseCustomHeaderSupplierPlugin implements DatabaseCustomHeaderSupplier {

  private final Object lock = new Object();
  private int counter = 0;

  @Override
  public CustomHeader getElasticsearchCustomHeader() {
    synchronized (lock) {
      final CustomHeader customHeader =
          new CustomHeader("Authorization", "Bearer dynamicToken_" + counter);
      counter += 1;
      return customHeader;
    }
  }
}
