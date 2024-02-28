/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getIndexRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
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
  protected static final Logger logger = LoggerFactory.getLogger(OpensearchContainerManager.class);

  protected RichOpenSearchClient richOpenSearchClient;

  public OpensearchContainerManager(
      RichOpenSearchClient richOpenSearchClient,
      OperateProperties operateProperties,
      SchemaManager schemaManager) {
    super(operateProperties, schemaManager);
    this.richOpenSearchClient = richOpenSearchClient;
  }

  protected void updatePropertiesIndexPrefix() {
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
  }

  protected boolean shouldCreateSchema() {
    return operateProperties.getOpensearch().isCreateSchema();
  }

  protected boolean areIndicesCreated(String indexPrefix, int minCountOfIndices)
      throws IOException {
    var indexRequestBuilder =
        getIndexRequestBuilder(indexPrefix + "*")
            .ignoreUnavailable(true)
            .allowNoIndices(false)
            .expandWildcards(ExpandWildcard.Open);

    GetIndexResponse response = richOpenSearchClient.index().get(indexRequestBuilder);

    var result = response.result();
    return result.size() >= minCountOfIndices;
  }

  @Override
  public void stopContainer() {
    String indexPrefix = operateProperties.getOpensearch().getIndexPrefix();
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
    } catch (Exception e) {
      logger.error("Failed to retrieve open contexts from opensearch! Returning 0.", e);
      return 0;
    }
  }
}
