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
   * Optional property to limit the maximum size of string column in bytes, if required by the
   * database vendor. If not set, no limit is applied.
   */
  private static final String CHAR_COLUMN_MAX_BYTES = "charColumn.maxBytes";

  /**
   * Required property to specify the maximum size of varchar columns in characters for non-indexed
   * fields (e.g., variable values, error messages, process definition names). This is used to
   * truncate values for preview purposes or to respect database vendor limitations.
   */
  private static final String VARCHAR_SIZE = "varchar.size";

  /**
   * Required property to specify the maximum size of varchar columns in characters for indexed
   * fields (e.g., all ID fields like processDefinitionId, formId, tenantId). This is limited by
   * database vendor index size constraints.
   */
  private static final String VARCHAR_INDEX_SIZE = "varcharIndex.size";

  private static final String DISABLE_FK_BEFORE_TRUNCATE = "disableFkBeforeTruncate";

  private final Properties properties;
  private final boolean disableFkBeforeTruncate;
  private final Integer charColumnMaxBytes;
  private final int varcharSize;
  private final int varcharIndexSize;

  public VendorDatabaseProperties(final Properties properties) {
    this.properties = properties;

    if (!properties.containsKey(VARCHAR_SIZE)) {
      throw new IllegalArgumentException("Property '" + VARCHAR_SIZE + "' is missing");
    }
    varcharSize = Integer.parseInt(properties.getProperty(VARCHAR_SIZE));

    if (!properties.containsKey(VARCHAR_INDEX_SIZE)) {
      throw new IllegalArgumentException("Property '" + VARCHAR_INDEX_SIZE + "' is missing");
    }
    varcharIndexSize = Integer.parseInt(properties.getProperty(VARCHAR_INDEX_SIZE));

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
  }

  /**
   * Returns the maximum size of varchar columns in characters for non-indexed fields (e.g.,
   * variable values, error messages, process definition names).
   *
   * @return the maximum varchar size
   */
  public int varcharSize() {
    return varcharSize;
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

  public Integer charColumnMaxBytes() {
    return charColumnMaxBytes;
  }

  public boolean disableFkBeforeTruncate() {
    return disableFkBeforeTruncate;
  }

  public Properties properties() {
    return properties;
  }
}
