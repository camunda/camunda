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
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ThreadUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchFlowNodeStore implements FlowNodeStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private OperateProperties operateProperties;

  @Override
  public String getFlowNodeIdByFlowNodeInstanceId(final String flowNodeInstanceId) {
    // TODO Elasticsearch changes
    final ElasticsearchUtil.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? ElasticsearchUtil.QueryType.ALL
            : ElasticsearchUtil.QueryType.ONLY_RUNTIME;
    final QueryBuilder query =
        joinWithAnd(
            termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
            termQuery(ListViewTemplate.ID, flowNodeInstanceId));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType)
            .source(new SearchSourceBuilder().query(query).fetchSource(ACTIVITY_ID, null));
    final SearchResponse response;
    try {
      response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value != 1) {
        throw new OperateRuntimeException("Flow node instance is not found: " + flowNodeInstanceId);
      } else {
        return String.valueOf(response.getHits().getAt(0).getSourceAsMap().get(ACTIVITY_ID));
      }
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Error occurred when searching for flow node instance: " + flowNodeInstanceId, e);
    }
  }

  @Override
  public Map<String, String> getFlowNodeIdsForFlowNodeInstances(
      final Set<String> flowNodeInstanceIds) {
    final Map<String, String> flowNodeIdsMap = new HashMap<>();
    final QueryBuilder q = termsQuery(FlowNodeInstanceTemplate.ID, flowNodeInstanceIds);
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(q)
                    .fetchSource(
                        new String[] {
                          FlowNodeInstanceTemplate.ID, FlowNodeInstanceTemplate.FLOW_NODE_ID
                        },
                        null));
    try {
      tenantAwareClient.search(
          request,
          () -> {
            scrollWith(
                request,
                esClient,
                searchHits -> {
                  Arrays.stream(searchHits.getHits())
                      .forEach(
                          h ->
                              flowNodeIdsMap.put(
                                  h.getId(),
                                  (String)
                                      h.getSourceAsMap()
                                          .get(FlowNodeInstanceTemplate.FLOW_NODE_ID)));
                },
                null,
                null);
            return null;
          });
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred when searching for flow node ids: " + e.getMessage(), e);
    }
    return flowNodeIdsMap;
  }

  @Override
  public String findParentTreePathFor(final long parentFlowNodeInstanceKey) {
    return findParentTreePath(parentFlowNodeInstanceKey, 0);
  }

  private String findParentTreePath(final long parentFlowNodeInstanceKey, final int attemptCount) {
    final ElasticsearchUtil.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? ElasticsearchUtil.QueryType.ALL
            : ElasticsearchUtil.QueryType.ONLY_RUNTIME;
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate, queryType)
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(FlowNodeInstanceTemplate.KEY, parentFlowNodeInstanceKey))
                    .fetchSource(FlowNodeInstanceTemplate.TREE_PATH, null));
    try {
      final SearchHits hits = tenantAwareClient.search(searchRequest).getHits();
      if (hits.getTotalHits().value > 0) {
        return (String) hits.getHits()[0].getSourceAsMap().get(FlowNodeInstanceTemplate.TREE_PATH);
      } else if (attemptCount < 1 && operateProperties.getImporter().isRetryReadingParents()) {
        // retry for the case, when ELS has not yet refreshed the indices
        ThreadUtil.sleepFor(2000L);
        return findParentTreePath(parentFlowNodeInstanceKey, attemptCount + 1);
      } else {
        return null;
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for parent flow node instance processes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
