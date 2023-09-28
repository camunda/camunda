/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.conditions;

public class OpensearchCondition extends DatabaseCondition{

  private static final String DATABASE = "opensearch";

  @Override
  public boolean getDefaultIfEmpty() {
    return false;
  }

  @Override
  public String getDatabase() {
    return DATABASE;
  }
}
