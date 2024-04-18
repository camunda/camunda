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
import static io.camunda.tasklist.store.elasticsearch.TaskMetricsStoreElasticSearch.ASSIGNEE;
import static io.camunda.tasklist.store.elasticsearch.TaskMetricsStoreElasticSearch.EVENT_TASK_COMPLETED_BY_ASSIGNEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.entities.MetricEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.MetricIndex;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaskMetricsStoreElasticSearchTest {

  private static final String METRIC_INDEX_NAME = "tasklist_metric_x.0.0";
  @Mock private MetricIndex index;
  @Mock private RestHighLevelClient esClient;
  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;

  @InjectMocks private TaskMetricsStoreElasticSearch instance;

  @BeforeEach
  void setUp() {
    when(index.getFullQualifiedName()).thenReturn(METRIC_INDEX_NAME);
  }

  @Test
  public void verifyRegisterEventWasCalledWithRightArgument() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final String assignee = "John Lennon";
    final TaskEntity task = new TaskEntity().setCompletionTime(now).setAssignee(assignee);
    final MetricEntity expectedEntry =
        new MetricEntity()
            .setValue(assignee)
            .setEventTime(now)
            .setEvent(EVENT_TASK_COMPLETED_BY_ASSIGNEE);
    final var indexResponse = mock(IndexResponse.class);
    when(indexResponse.status()).thenReturn(RestStatus.CREATED);
    final IndexRequest expectedIndexRequest =
        new IndexRequest(METRIC_INDEX_NAME)
            .source(objectMapper.writeValueAsString(expectedEntry), XContentType.JSON);
    when(esClient.index(any(), eq(RequestOptions.DEFAULT))).thenReturn(indexResponse);

    // When
    instance.registerTaskCompleteEvent(task);

    // Then
    verify(esClient).index(refEq(expectedIndexRequest), eq(RequestOptions.DEFAULT));
  }

  @Test
  public void exceptionIsNotHandledOnReaderWriterLevel() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final SearchRequest searchRequest = buildSearchRequest(now, oneHourBefore);
    when(esClient.search(refEq(searchRequest), eq(RequestOptions.DEFAULT)))
        .thenThrow(new IOException("IO exception raised"));

    // When - Then
    assertThatThrownBy(() -> instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("Error while retrieving assigned users between dates");
  }

  @Test
  public void throwErrorWhenErrorResponse() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    when(esClient.search(any(), eq(RequestOptions.DEFAULT)))
        .thenThrow(new IOException("IO exception occurred"));

    // When - Then
    assertThatThrownBy(() -> instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("Error while retrieving assigned users between dates");
  }

  @Test
  public void expectedResponseWhenResultsAreEmpty() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final SearchRequest searchRequest = buildSearchRequest(now, oneHourBefore);
    final SearchResponse searchResponse = mock(SearchResponse.class);
    when(esClient.search(refEq(searchRequest), eq(RequestOptions.DEFAULT)))
        .thenReturn(searchResponse);
    final Aggregations aggregations = mock(Aggregations.class);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    final var aggregation = mock(ParsedStringTerms.class);
    when(aggregations.get(ASSIGNEE)).thenReturn(aggregation);
    when(aggregation.getBuckets()).thenReturn(Collections.emptyList());

    // When
    final var result = instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now);

    // Then
    assertThat(result).isEmpty();
  }

  private SearchRequest buildSearchRequest(OffsetDateTime now, OffsetDateTime oneHourBefore) {
    final BoolQueryBuilder rangeQuery =
        boolQuery()
            .must(QueryBuilders.termsQuery(EVENT, EVENT_TASK_COMPLETED_BY_ASSIGNEE))
            .must(QueryBuilders.rangeQuery(EVENT_TIME).gte(oneHourBefore).lte(now));
    final TermsAggregationBuilder aggregation =
        AggregationBuilders.terms(ASSIGNEE).field(VALUE).size(Integer.MAX_VALUE);

    final SearchSourceBuilder source =
        SearchSourceBuilder.searchSource().query(rangeQuery).aggregation(aggregation);
    final SearchRequest searchRequest =
        new SearchRequest(index.getFullQualifiedName())
            .indicesOptions(IndicesOptions.lenientExpandOpen())
            .source(source);
    return searchRequest;
  }

  @Test
  public void expectedResponseWhenResultsAreReturned() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final SearchRequest searchRequest = buildSearchRequest(now, oneHourBefore);
    final SearchResponse searchResponse = mock(SearchResponse.class);
    when(esClient.search(refEq(searchRequest), eq(RequestOptions.DEFAULT)))
        .thenReturn(searchResponse);
    final Aggregations aggregations = mock(Aggregations.class);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    final var aggregation = mock(ParsedStringTerms.class);
    when(aggregations.get(ASSIGNEE)).thenReturn(aggregation);
    final var bucket = mock(ParsedStringTerms.ParsedBucket.class);
    when(bucket.getKey()).thenReturn("key");
    when((List<ParsedStringTerms.ParsedBucket>) aggregation.getBuckets())
        .thenReturn(List.of(bucket));

    // When
    final var result = instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now);

    // Then
    assertThat(result).containsExactly("key");
  }
}
