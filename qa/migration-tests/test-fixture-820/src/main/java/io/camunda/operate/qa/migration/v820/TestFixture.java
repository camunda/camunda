/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration.v820;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.qa.util.migration.AbstractTestFixture;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.camunda.operate.property.ElasticsearchProperties.BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT;
import static io.camunda.operate.schema.templates.IncidentTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "8.2.0";

  private static final Logger logger = LoggerFactory.getLogger(TestFixture.class);

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  protected ListViewTemplate listViewTemplate;

  @Autowired
  protected IncidentTemplate incidentTemplate;

  @Override
  public void setup(TestContext testContext) {
    super.setup(testContext);
    startZeebeAndOperate();
    stopZeebeAndOperate(testContext);
    adjustIncidentToTestPendingIncidentMigration();
  }

  private void adjustIncidentToTestPendingIncidentMigration() {
    SearchRequest incidentRequest = new SearchRequest(getIndexNameFor(incidentTemplate.getIndexName()))
        .source(new SearchSourceBuilder().query(matchAllQuery()).fetchSource(new String[]{}, null));

    BulkRequest bulkRequest = new BulkRequest();
    try {
      ElasticsearchUtil.scroll(incidentRequest, rethrowConsumer(sh -> {
        for (SearchHit searchHit : sh.getHits()) {
          Long key = (Long) searchHit.getSourceAsMap().get(FLOW_NODE_INSTANCE_KEY);
          Map<String, Object> updateFields = new HashMap<>();
          updateFields.put("pendingIncident", true);
          updateFields.put("incidentKeys", new Long[] { Long.valueOf(searchHit.getId()) });
          UpdateRequest updateRequest = new UpdateRequest()
              .index(getListViewIndexName())
              .id(String.valueOf(key))
              .routing(String.valueOf(searchHit.getSourceAsMap().get(PROCESS_INSTANCE_KEY)))
              .doc(updateFields);
          bulkRequest.add(updateRequest);
        }
      }), esClient);
      logger.info("Post importer queue preparation completed. Number of processed incidents: {}", bulkRequest.requests().size());
      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT);
      esClient.indices().refresh(new RefreshRequest("operate-*"), RequestOptions.DEFAULT);
    } catch (IOException | PersistenceException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }

  }

  private String getIndexNameFor(String index) {
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
