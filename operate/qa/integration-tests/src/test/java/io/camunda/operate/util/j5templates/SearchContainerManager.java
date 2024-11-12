/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.util.IndexPrefixHolder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SearchContainerManager {
  protected static final Logger LOGGER = LoggerFactory.getLogger(SearchContainerManager.class);

  protected final OperateProperties operateProperties;
  protected final SchemaManager schemaManager;
  protected String indexPrefix;
  private final IndexPrefixHolder indexPrefixHolder;

  public SearchContainerManager(
      final OperateProperties operateProperties,
      final SchemaManager schemaManager,
      final IndexPrefixHolder indexPrefixHolder) {
    this.operateProperties = operateProperties;
    this.schemaManager = schemaManager;
    this.indexPrefixHolder = indexPrefixHolder;
  }

  public void refreshIndices(final String indexPattern) {
    schemaManager.refresh(indexPattern);
  }

  public void startContainer() {
    if (indexPrefix == null) {
      indexPrefix = indexPrefixHolder.createNewIndexPrefix();
    }
    updatePropertiesIndexPrefix();
    if (shouldCreateSchema()) {
      schemaManager.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 5, 5 * 60 /*sec*/))
          .describedAs("Search %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  protected abstract void updatePropertiesIndexPrefix();

  protected abstract boolean shouldCreateSchema();

  protected boolean areIndicesCreatedAfterChecks(
      final String indexPrefix, final int minCountOfIndices, final int maxChecks) {
    boolean areCreated = false;
    int checks = 0;
    while (!areCreated && checks <= maxChecks) {
      checks++;
      try {
        areCreated = areIndicesCreated(indexPrefix, minCountOfIndices);
      } catch (final Exception t) {
        LOGGER.error(
            "Search indices (min {}) are not created yet. Waiting {}/{}",
            minCountOfIndices,
            checks,
            maxChecks);
        sleepFor(200);
      }
    }
    LOGGER.debug("Search indices are created after {} checks", checks);
    return areCreated;
  }

  protected abstract boolean areIndicesCreated(String indexPrefix, int minCountOfIndices)
      throws IOException;

  public abstract void stopContainer();
}
