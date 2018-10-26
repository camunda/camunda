/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.es.types.EventType;
import org.camunda.operate.rest.dto.EventQueryDto;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class EventReader {

  private static final Logger logger = LoggerFactory.getLogger(EventReader.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private EventType eventType;

  @Autowired
  private ObjectMapper objectMapper;

  public List<EventEntity> queryEvents(EventQueryDto eventQuery, Integer firstResult, Integer maxResults) {
    SearchRequestBuilder searchRequest = createSearchRequest(eventQuery);

    applySorting(searchRequest);

    if (firstResult != null && maxResults != null) {
      return paginate(searchRequest, firstResult, maxResults);
    } else {
      return scroll(searchRequest);
    }
  }

  private void applySorting(SearchRequestBuilder searchRequestBuilder) {
    searchRequestBuilder.addSort(EventType.DATE_TIME, SortOrder.ASC)
      .addSort(EventType.ID, SortOrder.ASC);
  }

  protected List<EventEntity> paginate(SearchRequestBuilder builder, int firstResult, int maxResults) {
    SearchResponse response = builder
      .setFrom(firstResult)
      .setSize(maxResults)
      .get();

    return mapSearchHits(response.getHits().getHits());
  }

  protected List<EventEntity> scroll(SearchRequestBuilder builder) {
    TimeValue keepAlive = new TimeValue(60000);

    SearchResponse response = builder
      .setScroll(keepAlive)
      .get();

    List<EventEntity> result = new ArrayList<>();

    do {

      SearchHits hits = response.getHits();
      String scrollId = response.getScrollId();

      result.addAll(mapSearchHits(hits.getHits()));

      response = esClient
          .prepareSearchScroll(scrollId)
          .setScroll(keepAlive)
          .get();

    } while (response.getHits().getHits().length != 0);

    return result;
  }

  protected List<EventEntity> mapSearchHits(SearchHit[] searchHits) {
    List<EventEntity> result = new ArrayList<>();
    for (SearchHit searchHit : searchHits) {
      String searchHitAsString = searchHit.getSourceAsString();
      result.add(fromSearchHit(searchHitAsString));
    }
    return result;
  }

  private EventEntity fromSearchHit(String eventEntityString) {
    EventEntity eventEntity;
    try {
      eventEntity = objectMapper.readValue(eventEntityString, EventEntity.class);
    } catch (IOException e) {
      logger.error("Error while reading event from Elasticsearch!", e);
      throw new RuntimeException("Error while reading event from Elasticsearch!", e);
    }
    return eventEntity;
  }

  private SearchRequestBuilder createSearchRequest(EventQueryDto eventQuery) {
    TermQueryBuilder workflowInstanceQ = null;
    if (eventQuery.getWorkflowInstanceId() != null) {
      workflowInstanceQ = termQuery(EventType.WORKFLOW_INSTANCE_ID, eventQuery.getWorkflowInstanceId());
    }
    TermQueryBuilder activityInstanceQ = null;
    if (eventQuery.getActivityInstanceId() != null) {
      activityInstanceQ = termQuery(EventType.ACTIVITY_INSTANCE_ID, eventQuery.getActivityInstanceId());
    }
    QueryBuilder query = ElasticsearchUtil.joinWithAnd(workflowInstanceQ, activityInstanceQ);
    if (query == null) {
      query = matchAllQuery();
    }

    ConstantScoreQueryBuilder constantScoreQuery = QueryBuilders.constantScoreQuery(query);
    logger.debug("Events search request: \n{}", constantScoreQuery.toString());

    return esClient
      .prepareSearch(eventType.getAlias())
      .setQuery(constantScoreQuery);
  }

}
