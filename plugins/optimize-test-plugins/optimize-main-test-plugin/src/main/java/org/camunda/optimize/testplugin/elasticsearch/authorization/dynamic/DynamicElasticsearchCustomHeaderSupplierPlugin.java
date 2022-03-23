/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.elasticsearch.authorization.dynamic;


import org.camunda.optimize.plugin.elasticsearch.CustomHeader;
import org.camunda.optimize.plugin.elasticsearch.ElasticsearchCustomHeaderSupplier;

public class DynamicElasticsearchCustomHeaderSupplierPlugin implements ElasticsearchCustomHeaderSupplier {

  private final Object lock = new Object();
  private int counter = 0;

  @Override
  public CustomHeader getElasticsearchCustomHeader() {
    synchronized (lock) {
      final CustomHeader customHeader = new CustomHeader("Authorization", "Bearer dynamicToken_" + counter);
      counter += 1;
      return customHeader;
    }
  }

}
