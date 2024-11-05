/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist.util;

import io.camunda.migration.tasklist.TasklistMigrationProperties;
import io.camunda.tasklist.schema.indices.ProcessIndex;

public class IndexUtil {
  private static final String INDEX_FORMAT = "%s-%s-%s_";
  private static final String TASKLIST_PREFIX = "tasklist";
  private static final String OPERATE_PREFIX = "operate";

  /** Returns the source index name based on the configuration. */
  public static String getSourceIndexName(final TasklistMigrationProperties properties) {
    return properties.getIndex() != null
            && properties.getIndex().getSourcePrefix() != null
            && !properties.getIndex().getSourcePrefix().isEmpty()
        ? INDEX_FORMAT.formatted(
            properties.getIndex().getSourcePrefix(),
            ProcessIndex.INDEX_NAME,
            ProcessIndex.INDEX_VERSION)
        : INDEX_FORMAT.formatted(
            TASKLIST_PREFIX, ProcessIndex.INDEX_NAME, ProcessIndex.INDEX_VERSION);
  }

  public static String getTargetIndexName(final TasklistMigrationProperties properties) {
    final String indexPrefix =
        properties.getIndex() != null
                && properties.getIndex().getTargetPrefix() != null
                && !properties.getIndex().getTargetPrefix().isEmpty()
            ? properties.getIndex().getTargetPrefix()
            : "";
    /* Currently issue appending prefix twice
      : OPERATE_PREFIX;
    */
    return new io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex(indexPrefix, true)
        .getFullQualifiedName();
  }
}
