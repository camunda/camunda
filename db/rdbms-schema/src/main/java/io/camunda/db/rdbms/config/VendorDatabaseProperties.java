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
   * Required property to specify the maximum size of varchar columns in characters for indexed
   * fields (e.g., all ID fields like processDefinitionId, formId, tenantId). This is limited by
   * database vendor index size constraints.
   */
  private static final String VARCHAR_INDEX_SIZE = "varcharIndex.size";

  /**
   * Required property to specify the size of an Incident's errorMessage in characters. Longer error
   * messages will be truncated to this size.
   */
  private static final String ERROR_MESSAGE_SIZE = "errorMessage.size";

  /**
   * Required property to specify the size of the tree path in characters.
   */
  private static final String TREE_PATH_SIZE = "treePath.size";

  /**
   * Required property to specify the size of the variable value preview in characters. This is used
   * to truncate variable values for preview purposes.
   */
  private static final String VARIABLE_VALUE_PREVIEW_SIZE = "variableValue.previewSize";

  private static final String DISABLE_FK_BEFORE_TRUNCATE = "disableFkBeforeTruncate";

  private final Properties properties;
  private final boolean disableFkBeforeTruncate;
  private final int varcharIndexSize;
  private final int errorMessageSize;
  private final int treePathSize;
  private final int variableValuePreviewSize;

  public VendorDatabaseProperties(final Properties properties) {
    this.properties = properties;

    if (!properties.containsKey(VARCHAR_INDEX_SIZE)) {
      throw new IllegalArgumentException("Property '" + VARCHAR_INDEX_SIZE + "' is missing");
    }
    varcharIndexSize = Integer.parseInt(properties.getProperty(VARCHAR_INDEX_SIZE));

    if (!properties.containsKey(ERROR_MESSAGE_SIZE)) {
      throw new IllegalArgumentException("Property '" + ERROR_MESSAGE_SIZE + "' is missing");
    }
    errorMessageSize = Integer.parseInt(properties.getProperty(ERROR_MESSAGE_SIZE));

    if (!properties.containsKey(TREE_PATH_SIZE)) {
      throw new IllegalArgumentException("Property '" + TREE_PATH_SIZE + "' is missing");
    }
    treePathSize = Integer.parseInt(properties.getProperty(TREE_PATH_SIZE));

    if (!properties.containsKey(VARIABLE_VALUE_PREVIEW_SIZE)) {
      throw new IllegalArgumentException(
          "Property '" + VARIABLE_VALUE_PREVIEW_SIZE + "' is missing");
    }
    variableValuePreviewSize =
        Integer.parseInt(properties.getProperty(VARIABLE_VALUE_PREVIEW_SIZE));

    if (!properties.containsKey(DISABLE_FK_BEFORE_TRUNCATE)) {
      throw new IllegalArgumentException(
          "Property '" + DISABLE_FK_BEFORE_TRUNCATE + "' is missing");
    }
    disableFkBeforeTruncate =
        Boolean.parseBoolean(properties.getProperty(DISABLE_FK_BEFORE_TRUNCATE));
  }

  /**
   * Returns the maximum size of varchar columns in characters for indexed fields (e.g., all ID
   * fields like processDefinitionId, formId, tenantId).
   *
   * @return the maximum indexed varchar size
   */
  public int varcharIndexSize() {
    return varcharIndexSize;
  }

  /**
   * Returns the maximum size of error message columns in characters.
   *
   * @return the maximum error message size
   */
  public int errorMessageSize() {
    return errorMessageSize;
  }

  /**
   * Returns the maximum size of tree path columns in characters.
   *
   * @return the maximum tree path size
   */
  public int treePathSize() {
    return treePathSize;
  }

  /**
   * Returns the maximum size of variable value preview columns in characters.
   *
   * @return the maximum variable value preview size
   */
  public int variableValuePreviewSize() {
    return variableValuePreviewSize;
  }

  public boolean disableFkBeforeTruncate() {
    return disableFkBeforeTruncate;
  }

  public Properties properties() {
    return properties;
  }
}
