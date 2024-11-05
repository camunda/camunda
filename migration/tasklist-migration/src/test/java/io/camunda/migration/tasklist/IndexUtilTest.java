/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.camunda.migration.tasklist.TasklistMigrationProperties.IndexConfig;
import io.camunda.migration.tasklist.util.IndexUtil;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import org.junit.jupiter.api.Test;

public class IndexUtilTest {

  @Test
  public void sourceAndTargetIndexWithoutPrefix() {
    final var properties = new TasklistMigrationProperties();
    final IndexConfig indexConfig = new IndexConfig();
    properties.setIndex(indexConfig);
    final var targetIndexName = IndexUtil.getTargetIndexName(properties);
    final var sourceIndexName = IndexUtil.getSourceIndexName(properties);

    assertEquals(
        "%s-%s-%s_".formatted("tasklist", ProcessIndex.INDEX_NAME, ProcessIndex.INDEX_VERSION),
        sourceIndexName);
    assertEquals(
        "%s-%s-%s_"
            .formatted(
                "operate",
                io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex.INDEX_NAME,
                "8.3.0"),
        targetIndexName);
  }

  @Test
  public void sourceAndTargetIndexWithPrefix() {
    final var properties = new TasklistMigrationProperties();
    final IndexConfig indexConfig = new IndexConfig();
    indexConfig.setSourcePrefix("source");
    indexConfig.setTargetPrefix("target");
    properties.setIndex(indexConfig);

    final var sourceIndexName = IndexUtil.getSourceIndexName(properties);
    final var targetIndexName = IndexUtil.getTargetIndexName(properties);

    assertEquals(
        "%s-%s-%s_".formatted("source", ProcessIndex.INDEX_NAME, ProcessIndex.INDEX_VERSION),
        sourceIndexName);
    assertEquals(
        "%s-%s-%s_"
            .formatted(
                // Currently issue appending prefix twice
                "target-operate",
                io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex.INDEX_NAME,
                "8.3.0"),
        targetIndexName);
  }
}
