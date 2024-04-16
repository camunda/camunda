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

import static io.camunda.tasklist.schema.indices.MetricIndex.EVENT;
import static io.camunda.tasklist.schema.indices.MetricIndex.EVENT_TIME;
import static io.camunda.tasklist.schema.indices.MetricIndex.VALUE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.MetricEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.MetricIndex;
import io.camunda.tasklist.store.TaskMetricsStore;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TaskMetricsStoreElasticSearch implements TaskMetricsStore {

  public static final String EVENT_TASK_COMPLETED_BY_ASSIGNEE = "task_completed_by_assignee";
  public static final String ASSIGNEE = "assignee";
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskMetricsStoreElasticSearch.class);
  @Autowired private MetricIndex index;
  @Autowired private RestHighLevelClient esClient;
  @Autowired private ObjectMapper objectMapper;

  @Override
  public void registerTaskCompleteEvent(TaskEntity task) {
    final MetricEntity metric = createTaskCompleteEvent(task);
    final boolean inserted = insert(metric);
    if (!inserted) {
      final String message = "Wrong response status while logging event";
      LOGGER.error(message);
      throw new TasklistRuntimeException(message);
    }
  }

  private boolean insert(MetricEntity entity) {
    try {
      final IndexRequest request =
          new IndexRequest(index.getFullQualifiedName())
              .id(entity.getId())
              .source(objectMapper.writeValueAsString(entity), XContentType.JSON);

      final IndexResponse response = esClient.index(request, RequestOptions.DEFAULT);
      return response.status() == RestStatus.CREATED;
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      throw new TasklistRuntimeException("Error while trying to upsert entity: " + entity);
    }
  }

  @Override
  public List<String> retrieveDistinctAssigneesBetweenDates(
      OffsetDateTime startTime, OffsetDateTime endTime) {

    final BoolQueryBuilder rangeQuery =
        boolQuery()
            .must(QueryBuilders.termsQuery(EVENT, EVENT_TASK_COMPLETED_BY_ASSIGNEE))
            .must(QueryBuilders.rangeQuery(EVENT_TIME).gte(startTime).lte(endTime));
    final TermsAggregationBuilder aggregation =
        AggregationBuilders.terms(ASSIGNEE).field(VALUE).size(Integer.MAX_VALUE);

    final SearchSourceBuilder source =
        SearchSourceBuilder.searchSource().query(rangeQuery).aggregation(aggregation);
    final SearchRequest searchRequest =
        new SearchRequest(index.getFullQualifiedName())
            .indicesOptions(IndicesOptions.lenientExpandOpen())
            .source(source);
    try {

      final Aggregations aggregations =
          esClient.search(searchRequest, RequestOptions.DEFAULT).getAggregations();
      if (aggregations == null) {
        throw new TasklistRuntimeException("Search with aggregation returned no aggregation");
      }

      final Aggregation group = aggregations.get(ASSIGNEE);
      if (!(group instanceof ParsedStringTerms terms)) {
        throw new TasklistRuntimeException("Unexpected response for aggregations");
      }

      final List<ParsedStringTerms.ParsedBucket> buckets =
          (List<ParsedStringTerms.ParsedBucket>) terms.getBuckets();

      return buckets.stream().map(it -> String.valueOf(it.getKey())).collect(Collectors.toList());
    } catch (IOException e) {
      LOGGER.error("Error while retrieving assigned users between dates from index: " + index, e);
      final String message = "Error while retrieving assigned users between dates";
      throw new TasklistRuntimeException(message);
    }
  }

  private MetricEntity createTaskCompleteEvent(TaskEntity task) {
    return new MetricEntity()
        .setEvent(EVENT_TASK_COMPLETED_BY_ASSIGNEE)
        .setValue(task.getAssignee())
        .setEventTime(task.getCompletionTime())
        .setTenantId(task.getTenantId());
  }
}
