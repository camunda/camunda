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
package io.camunda.tasklist.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.schema.indices.TaskFilterIndex;
import io.camunda.tasklist.entities.TaskFilterEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.store.TaskFilterStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TaskFilterStoreElasticSearch implements TaskFilterStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskFilterStoreElasticSearch.class);

  @Autowired private TaskFilterIndex taskFilterIndex;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private RestHighLevelClient esClient;

  @Override
  public TaskFilterEntity persistFilter(TaskFilterEntity filterEntity) {
    try {
      final IndexRequest indexRequest =
          new IndexRequest(taskFilterIndex.getFullQualifiedName())
              .source(objectMapper.writeValueAsString(filterEntity), XContentType.JSON);
      final IndexResponse indexResponse = esClient.index(indexRequest, RequestOptions.DEFAULT);
      filterEntity.setId(indexResponse.getId());
    } catch (IOException exception) {
      throw new TasklistRuntimeException(exception);
    }
    return filterEntity;
  }

  @Override
  public Optional<TaskFilterEntity> getById(final String id) {
    try {
      final GetRequest getRequest = new GetRequest(taskFilterIndex.getAlias()).id(id);
      final GetResponse response = esClient.get(getRequest, RequestOptions.DEFAULT);
      final SearchRequest searchRequest = new SearchRequest(taskFilterIndex.getAlias());
      final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
      sourceBuilder.query(QueryBuilders.idsQuery().addIds(id));
      searchRequest.source(sourceBuilder);

      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      if(searchResponse.getHits().getHits().length == 0){
        return Optional.empty();
      }

      return Optional.of(ElasticsearchUtil.fromSearchHit(response.getSourceAsString(), objectMapper, TaskFilterEntity.class));
    } catch (IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  @Override
  public List<TaskFilterEntity> getFilters(final List<String> candidateUsers,
      final List<String> candidateGroups) {
    SearchRequest searchRequest = new SearchRequest(taskFilterIndex.getAlias());
    final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
    final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

    candidateUsers.forEach(user -> boolQueryBuilder.should(QueryBuilders.termsQuery(TaskFilterIndex.CANDIDATE_USERS, user)));
    candidateGroups.forEach(group -> boolQueryBuilder.should(QueryBuilders.termsQuery(TaskFilterIndex.CANDIDATE_GROUPS, group)));

    boolQueryBuilder.should(
        QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(TaskFilterIndex.CANDIDATE_USERS))
            .mustNot(QueryBuilders.existsQuery(TaskFilterIndex.CANDIDATE_GROUPS))
    );

    sourceBuilder.query(boolQueryBuilder);
    searchRequest.source(sourceBuilder);

    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      return ElasticsearchUtil.mapSearchHits(searchResponse.getHits().getHits(), (sh) -> ElasticsearchUtil.fromSearchHit(
          sh.getSourceAsString(), objectMapper, TaskFilterEntity.class));
    } catch (IOException e) {
      LOGGER.error("Error when trying to get Task Filters", e);
      throw new TasklistRuntimeException(e);
    }
  }

  @Override
  public List<TaskFilterEntity> getFilters() {
    final SearchRequest searchRequest = new SearchRequest(taskFilterIndex.getAlias());
    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      return ElasticsearchUtil.mapSearchHits(searchResponse.getHits().getHits(), (sh) -> ElasticsearchUtil.fromSearchHit(
          sh.getSourceAsString(), objectMapper, TaskFilterEntity.class));
    } catch (IOException e) {
      LOGGER.error("Error when trying to get Task Filters", e);
      throw new TasklistRuntimeException(e);
    }
  }

}
