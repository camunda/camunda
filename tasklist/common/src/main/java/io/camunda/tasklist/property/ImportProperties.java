/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

public class ImportProperties {

  public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;

  /** Variable size under which we won't store preview separately. */
  private int variableSizeThreshold = DEFAULT_VARIABLE_SIZE_THRESHOLD;

  public int getVariableSizeThreshold() {
    return variableSizeThreshold;
  }

  public ImportProperties setVariableSizeThreshold(final int variableSizeThreshold) {
    this.variableSizeThreshold = variableSizeThreshold;
    return this;
  }
}
