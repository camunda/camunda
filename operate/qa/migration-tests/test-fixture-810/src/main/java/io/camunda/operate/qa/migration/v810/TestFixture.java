/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.migration.v810;

import static io.camunda.operate.property.ElasticsearchProperties.BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.qa.util.migration.AbstractTestFixture;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "8.1.14";

  private static final Logger LOGGER = LoggerFactory.getLogger(TestFixture.class);
  @Autowired protected ListViewTemplate listViewTemplate;
  @Autowired protected IncidentTemplate incidentTemplate;
  @Autowired private RestHighLevelClient esClient;

  @Override
  public void setup(final TestContext testContext) {
    super.setup(testContext);
    startZeebeAndOperate();
    // no additional data is needed
    stopZeebeAndOperate(testContext);
    adjustIncidentToTestPendingIncidentMigration();
  }

  private void adjustIncidentToTestPendingIncidentMigration() {
    final SearchRequest incidentRequest =
        new SearchRequest(getIndexNameFor(incidentTemplate.getIndexName()))
            .source(
                new SearchSourceBuilder()
                    .query(matchAllQuery())
                    .fetchSource(new String[] {}, null));

    final BulkRequest bulkRequest = new BulkRequest();
    try {
      ElasticsearchUtil.scroll(
          incidentRequest,
          rethrowConsumer(
              sh -> {
                for (final SearchHit searchHit : sh.getHits()) {
                  final Long key = (Long) searchHit.getSourceAsMap().get(FLOW_NODE_INSTANCE_KEY);
                  final Map<String, Object> updateFields = new HashMap<>();
                  updateFields.put("pendingIncident", true);
                  updateFields.put("incidentKeys", new Long[] {Long.valueOf(searchHit.getId())});
                  final UpdateRequest updateRequest =
                      new UpdateRequest()
                          .index(getListViewIndexName())
                          .id(String.valueOf(key))
                          .routing(
                              String.valueOf(searchHit.getSourceAsMap().get(PROCESS_INSTANCE_KEY)))
                          .doc(updateFields);
                  bulkRequest.add(updateRequest);
                }
              }),
          esClient);
      LOGGER.info(
          "Post importer queue preparation completed. Number of processed incidents: {}",
          bulkRequest.requests().size());
      ElasticsearchUtil.processBulkRequest(
          esClient, bulkRequest, BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT);
      esClient.indices().refresh(new RefreshRequest("operate-*"), RequestOptions.DEFAULT);
    } catch (final IOException | PersistenceException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
  }

  private String getIndexNameFor(final String index) {
    return String.format("operate-%s-*", index);
  }

  private String getListViewIndexName() {
    return String.format("operate-%s-%s_", listViewTemplate.getIndexName(), "8.1.0");
  }

  @Override
  public String getVersion() {
    return VERSION;
  }
}
