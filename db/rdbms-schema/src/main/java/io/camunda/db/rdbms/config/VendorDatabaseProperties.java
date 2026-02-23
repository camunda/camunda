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
   * Required property to specify the size of an Incident's errorMessage in characters. Longer error
   * messages will be truncated to this size.
   */
  private static final String ERROR_MESSAGE_SIZE = "errorMessage.size";

  /**
   * Required property to specify the size of the tree path column in characters. Longer tree paths
   * will be truncated to this size.
   */
  private static final String TREE_PATH_SIZE = "treePath.size";

  /**
   * Optional property to limit the maximum size of string column in bytes, if required by the
   * database vendor. If not set, no limit is applied.
   */
  private static final String CHAR_COLUMN_MAX_BYTES = "charColumn.maxBytes";

  /**
   * Optional property to limit the maximum size of string column in bytes, if required by the
   * database vendor. If not set, no limit is applied.
   */
  private static final String USER_CHAR_COLUMN_SIZE = "userCharColumn.size";

  private static final String DISABLE_FK_BEFORE_TRUNCATE = "disableFkBeforeTruncate";

  /**
   * Optional property to indicate whether the database vendor supports insert batching (INSERT INTO
   * (..) VALUES (..), (..), (..)). Oracle 19c and earlier versions do not support this syntax. If
   * not set, defaults to true (batching is supported).
   */
  private static final String SUPPORTS_INSERT_BATCHING = "supportsInsertBatching";

  private final Properties properties;

  private final int variableValuePreviewSize;
  private final boolean disableFkBeforeTruncate;
  private final boolean supportsInsertBatching;
  private final Integer charColumnMaxBytes;
  private final int userCharColumnSize;
  private final int errorMessageSize;
  private final int treePathSize;

  public VendorDatabaseProperties(final Properties properties) {
    this.properties = properties;

    if (!properties.containsKey(VARIABLE_VALUE_PREVIEW_SIZE)) {
      throw new IllegalArgumentException(
          "Property '" + VARIABLE_VALUE_PREVIEW_SIZE + "' is missing");
    }
    variableValuePreviewSize =
        Integer.parseInt(properties.getProperty(VARIABLE_VALUE_PREVIEW_SIZE));

    if (!properties.containsKey(USER_CHAR_COLUMN_SIZE)) {
      throw new IllegalArgumentException("Property '" + USER_CHAR_COLUMN_SIZE + "' is missing");
    }
    userCharColumnSize = Integer.parseInt(properties.getProperty(USER_CHAR_COLUMN_SIZE));

    if (!properties.containsKey(ERROR_MESSAGE_SIZE)) {
      throw new IllegalArgumentException("Property '" + ERROR_MESSAGE_SIZE + "' is missing");
    }
    errorMessageSize = Integer.parseInt(properties.getProperty(ERROR_MESSAGE_SIZE));

    if (!properties.containsKey(TREE_PATH_SIZE)) {
      throw new IllegalArgumentException("Property '" + TREE_PATH_SIZE + "' is missing");
    }
    treePathSize = Integer.parseInt(properties.getProperty(TREE_PATH_SIZE));

    if (!properties.containsKey(CHAR_COLUMN_MAX_BYTES)) {
      charColumnMaxBytes = null;
    } else {
      charColumnMaxBytes = Integer.parseInt(properties.getProperty(CHAR_COLUMN_MAX_BYTES));
    }

    if (!properties.containsKey(DISABLE_FK_BEFORE_TRUNCATE)) {
      throw new IllegalArgumentException(
          "Property '" + DISABLE_FK_BEFORE_TRUNCATE + "' is missing");
    }
    disableFkBeforeTruncate =
        Boolean.parseBoolean(properties.getProperty(DISABLE_FK_BEFORE_TRUNCATE));

    // Default to true if not specified (for backward compatibility)
    supportsInsertBatching =
        !properties.containsKey(SUPPORTS_INSERT_BATCHING)
            || Boolean.parseBoolean(properties.getProperty(SUPPORTS_INSERT_BATCHING));
  }

  public int variableValuePreviewSize() {
    return variableValuePreviewSize;
  }

  public int userCharColumnSize() {
    return userCharColumnSize;
  }

  public int errorMessageSize() {
    return errorMessageSize;
  }

  public Integer charColumnMaxBytes() {
    return charColumnMaxBytes;
  }

  public boolean disableFkBeforeTruncate() {
    return disableFkBeforeTruncate;
  }

  public Properties properties() {
    return properties;
  }

  public int treePathSize() {
    return treePathSize;
  }

  public boolean supportsInsertBatching() {
    return supportsInsertBatching;
  }
}
