/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.testplugin.elasticsearch.authorization.dynamic;


import org.camunda.optimize.plugin.elasticsearch.CustomHeader;
import org.camunda.optimize.plugin.elasticsearch.ElasticsearchCustomHeaderSupplier;

public class DynamicElasticsearchCustomHeaderSupplierPlugin implements ElasticsearchCustomHeaderSupplier {

  private int counter = 0;

  @Override
  public CustomHeader getElasticsearchCustomHeader() {
    final CustomHeader customHeader = new CustomHeader("Authorization", "Bearer dynamicToken_" + counter);
    counter += 1;
    return customHeader;
  }

}
