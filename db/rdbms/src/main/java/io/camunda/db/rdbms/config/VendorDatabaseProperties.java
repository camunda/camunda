/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.config;

import java.util.Properties;

public class VendorDatabaseProperties {

  private static final String VARIABLE_VALUE_PREVIEW_SIZE = "variableValue.previewSize";

  private final Properties properties;

  private final int variableValuePreviewSize;

  public VendorDatabaseProperties(final Properties properties) {
    this.properties = properties;

    if (!properties.containsKey(VARIABLE_VALUE_PREVIEW_SIZE)) {
      throw new IllegalArgumentException(
          "Property '" + VARIABLE_VALUE_PREVIEW_SIZE + "' is missing");
    }
    variableValuePreviewSize =
        Integer.parseInt(properties.getProperty(VARIABLE_VALUE_PREVIEW_SIZE));
  }

  public int variableValuePreviewSize() {
    return variableValuePreviewSize;
  }

  public Properties properties() {
    return properties;
  }
}
