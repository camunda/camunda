/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.testplugin.elasticsearch.authorization.fixed;

import io.camunda.optimize.plugin.elasticsearch.CustomHeader;
import io.camunda.optimize.plugin.elasticsearch.DatabaseCustomHeaderSupplier;

public class FixedDatabaseCustomHeaderSupplierPlugin implements DatabaseCustomHeaderSupplier {

  /** Returns the same header every time the plugin is called */
  @Override
  public CustomHeader getElasticsearchCustomHeader() {
    return new CustomHeader("Authorization", "Bearer fixedToken");
  }
}
