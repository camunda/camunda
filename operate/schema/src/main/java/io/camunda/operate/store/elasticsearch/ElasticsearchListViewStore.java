/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchListViewStore implements ListViewStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private OperateProperties operateProperties;

  @Override
  public Map<Long, String> getListViewIndicesForProcessInstances(List<Long> processInstanceIds)
      throws IOException {
    final List<String> processInstanceIdsAsStrings = map(processInstanceIds, Object::toString);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, ElasticsearchUtil.QueryType.ALL);
    searchRequest
        .source()
        .query(QueryBuilders.idsQuery().addIds(toSafeArrayOfStrings(processInstanceIdsAsStrings)));

    final Map<Long, String> processInstanceId2IndexName = new HashMap<>();
    tenantAwareClient.search(
        searchRequest,
        () -> {
          ElasticsearchUtil.scrollWith(
              searchRequest,
              esClient,
              searchHits -> {
                for (SearchHit searchHit : searchHits.getHits()) {
                  final String indexName = searchHit.getIndex();
                  final Long id = Long.valueOf(searchHit.getId());
                  processInstanceId2IndexName.put(id, indexName);
                }
              });
          return null;
        });

    if (processInstanceId2IndexName.isEmpty()) {
      throw new NotFoundException(
          String.format("Process instances %s doesn't exists.", processInstanceIds));
    }
    return processInstanceId2IndexName;
  }

  @Override
  public String findProcessInstanceTreePathFor(long processInstanceKey) {
    final ElasticsearchUtil.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? ElasticsearchUtil.QueryType.ALL
            : ElasticsearchUtil.QueryType.ONLY_RUNTIME;
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType)
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(ListViewTemplate.KEY, processInstanceKey))
                    .fetchSource(ListViewTemplate.TREE_PATH, null));
    try {
      final SearchHits hits = tenantAwareClient.search(searchRequest).getHits();
      if (hits.getTotalHits().value > 0) {
        return (String) hits.getHits()[0].getSourceAsMap().get(ListViewTemplate.TREE_PATH);
      }
      return null;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for process instance tree path: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<Long> getProcessInstanceKeysWithEmptyProcessVersionFor(Long processDefinitionKey) {
    QueryBuilder queryBuilder =
        constantScoreQuery(
            joinWithAnd(
                termQuery(ListViewTemplate.PROCESS_KEY, processDefinitionKey),
                boolQuery().mustNot(existsQuery(ListViewTemplate.PROCESS_VERSION))));
    SearchRequest searchRequest =
        new SearchRequest(listViewTemplate.getAlias())
            .source(new SearchSourceBuilder().query(queryBuilder).fetchSource(false));
    try {
      return tenantAwareClient.search(
          searchRequest,
          () -> {
            return ElasticsearchUtil.scrollKeysToList(searchRequest, esClient);
          });
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining process instance that has empty versions: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
