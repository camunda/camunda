/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.testplugin.elasticsearch.custom;


import org.camunda.optimize.plugin.elasticsearch.CustomHeader;
import org.camunda.optimize.plugin.elasticsearch.ElasticsearchCustomHeaderSupplier;

public class CustomElasticsearchCustomHeaderSupplierPlugin implements ElasticsearchCustomHeaderSupplier {

  @Override
  public CustomHeader getElasticsearchCustomHeader() {
    return new CustomHeader("CustomHeader", "customValue");
  }

}
