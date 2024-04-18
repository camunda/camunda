/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.qa.migration.v810;

import static io.camunda.operate.property.ElasticsearchProperties.BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT;
import static io.camunda.operate.schema.templates.IncidentTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.qa.util.migration.AbstractTestFixture;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
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
  @Autowired protected OperateProperties operateProperties;
  @Autowired private RestHighLevelClient esClient;

  @Override
  public void setup(TestContext testContext) {
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
                for (SearchHit searchHit : sh.getHits()) {
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
          esClient,
          bulkRequest,
          BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT,
          operateProperties.getElasticsearch().isBulkRequestIgnoreNullIndex());
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
