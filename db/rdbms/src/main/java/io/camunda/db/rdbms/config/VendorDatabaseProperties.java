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

  /**
   * Required property to specify the size of the variable value preview in characters. This is used
   * to truncate variable values for preview purposes.
   */
  private static final String VARIABLE_VALUE_PREVIEW_SIZE = "variableValue.previewSize";

  /**
   * Optional property to limit the maximum size of variable values in bytes, if required by the
   * database vendor. If not set, no limit is applied.
   */
  private static final String VARIABLE_VALUE_MAX_BYTES = "variableValue.maxBytes";

  private static final String DISABLE_FK_BEFORE_TRUNCATE = "disableFkBeforeTruncate";

  private final Properties properties;

  private final int variableValuePreviewSize;
  private final boolean disableFkBeforeTruncate;
  private final Integer variableValueMaxBytes;

  public VendorDatabaseProperties(final Properties properties) {
    this.properties = properties;

    if (!properties.containsKey(VARIABLE_VALUE_PREVIEW_SIZE)) {
      throw new IllegalArgumentException(
          "Property '" + VARIABLE_VALUE_PREVIEW_SIZE + "' is missing");
    }
    variableValuePreviewSize =
        Integer.parseInt(properties.getProperty(VARIABLE_VALUE_PREVIEW_SIZE));

    if (!properties.containsKey(VARIABLE_VALUE_MAX_BYTES)) {
      variableValueMaxBytes = null;
    } else {
      variableValueMaxBytes = Integer.parseInt(properties.getProperty(VARIABLE_VALUE_MAX_BYTES));
    }

    if (!properties.containsKey(DISABLE_FK_BEFORE_TRUNCATE)) {
      throw new IllegalArgumentException(
          "Property '" + DISABLE_FK_BEFORE_TRUNCATE + "' is missing");
    }
    disableFkBeforeTruncate =
        Boolean.parseBoolean(properties.getProperty(DISABLE_FK_BEFORE_TRUNCATE));
  }

  public int variableValuePreviewSize() {
    return variableValuePreviewSize;
  }

  public Integer variableValueMaxBytes() {
    return variableValueMaxBytes;
  }

  public boolean disableFkBeforeTruncate() {
    return disableFkBeforeTruncate;
  }

  public Properties properties() {
    return properties;
  }
}
