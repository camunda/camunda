/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getIndexRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.config.operate.OperateOpensearchProperties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.util.camunda.exporter.SchemaWithExporter;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.IndexPrefixHolder;
import io.camunda.operate.util.TestUtil;
import java.io.IOException;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchContainerManager extends SearchContainerManager {
  protected static final Logger LOGGER = LoggerFactory.getLogger(OpensearchContainerManager.class);

  protected RichOpenSearchClient richOpenSearchClient;

  private final IndexPrefixHolder indexPrefixHolder;

  public OpensearchContainerManager(
      final RichOpenSearchClient richOpenSearchClient,
      final OperateProperties operateProperties,
      final SchemaManager schemaManager,
      final IndexPrefixHolder indexPrefixHolder) {
    super(operateProperties, schemaManager);
    this.richOpenSearchClient = richOpenSearchClient;
    this.indexPrefixHolder = indexPrefixHolder;
  }

  @Override
  public void startContainer() {
    if (indexPrefix == null) {
      indexPrefix = indexPrefixHolder.createNewIndexPrefix();
    }
    updatePropertiesIndexPrefix();
    if (shouldCreateSchema()) {
      final var schemaExporterHelper = new SchemaWithExporter(indexPrefix, false);
      schemaExporterHelper.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 19, 5 * 60 /*sec*/))
          .describedAs("Search %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  @Override
  protected void updatePropertiesIndexPrefix() {
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
  }

  @Override
  protected boolean shouldCreateSchema() {
    return operateProperties.getOpensearch().isCreateSchema();
  }

  @Override
  protected boolean areIndicesCreated(final String indexPrefix, final int minCountOfIndices)
      throws IOException {
    final var indexRequestBuilder =
        getIndexRequestBuilder(indexPrefix + "*")
            .ignoreUnavailable(true)
            .allowNoIndices(false)
            .expandWildcards(ExpandWildcard.Open);

    final GetIndexResponse response = richOpenSearchClient.index().get(indexRequestBuilder);

    final var result = response.result();
    return result.size() >= minCountOfIndices;
  }

  @Override
  public void stopContainer() {
    final String indexPrefix = operateProperties.getOpensearch().getIndexPrefix();
    TestUtil.removeAllIndices(
        richOpenSearchClient.index(), richOpenSearchClient.template(), indexPrefix);
    operateProperties
        .getOpensearch()
        .setIndexPrefix(OperateOpensearchProperties.DEFAULT_INDEX_PREFIX);

    assertThat(getOpenScrollContextSize())
        .describedAs("There are too many open scroll contexts left.")
        .isLessThanOrEqualTo(15);
  }

  public int getOpenScrollContextSize() {
    try {
      return richOpenSearchClient.cluster().totalOpenContexts();
    } catch (final Exception e) {
      LOGGER.error("Failed to retrieve open contexts from opensearch! Returning 0.", e);
      return 0;
    }
  }
}
