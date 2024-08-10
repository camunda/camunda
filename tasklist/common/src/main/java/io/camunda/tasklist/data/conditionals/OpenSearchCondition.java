/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.data.conditionals;

public class OpenSearchCondition extends DataBaseCondition {
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
