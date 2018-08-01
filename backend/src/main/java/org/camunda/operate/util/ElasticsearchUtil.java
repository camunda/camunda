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
package org.camunda.operate.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.writer.PersistenceException;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public abstract class ElasticsearchUtil {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchUtil.class);

  public static QueryBuilder joinWithOr(BoolQueryBuilder boolQueryBuilder, QueryBuilder... queries) {
    List<QueryBuilder> notNullQueries = CollectionUtil.throwAwayNullElements(queries);
    for (QueryBuilder query: notNullQueries) {
      boolQueryBuilder.should(query);
    }
    return boolQueryBuilder;
  }

  /**
   * Join queries with OR clause. If 0 queries are passed for wrapping, then null is returned. If 1 parameter is passed, it will be returned back as ia. Otherwise, the new
   * BoolQuery will be created and returned.
   * @param queries
   * @return
   */
  public static QueryBuilder joinWithOr(QueryBuilder... queries) {
    List<QueryBuilder> notNullQueries = CollectionUtil.throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
    case 0:
      return null;
    case 1:
      return notNullQueries.get(0);
    default:
      final BoolQueryBuilder boolQ = boolQuery();
      for (QueryBuilder query: notNullQueries) {
        boolQ.should(query);
      }
      return boolQ;
    }
  }

  /**
   * Join queries with AND clause. If 0 queries are passed for wrapping, then null is returned. If 1 parameter is passed, it will be returned back as ia. Otherwise, the new
   * BoolQuery will be created and returned.
   * @param queries
   * @return
   */
  public static QueryBuilder joinWithAnd(QueryBuilder... queries) {
    List<QueryBuilder> notNullQueries = CollectionUtil.throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
    case 0:
      return null;
    case 1:
      return notNullQueries.get(0);
    default:
      final BoolQueryBuilder boolQ = boolQuery();
      for (QueryBuilder query: notNullQueries) {
        boolQ.must(query);
      }
      return boolQ;
    }
  }

  public static BoolQueryBuilder createMatchNoneQuery() {
    return boolQuery().must(QueryBuilders.wrapperQuery("{\"match_none\": {}}"));
  }

  public static List<WorkflowInstanceEntity> mapSearchHits(SearchHit[] searchHits, ObjectMapper objectMapper) {
    List<WorkflowInstanceEntity> result = new ArrayList<>();
    for (SearchHit searchHit : searchHits) {
      String searchHitAsString = searchHit.getSourceAsString();
      result.add(fromSearchHit(searchHitAsString, objectMapper));
    }
    return result;
  }

  public static WorkflowInstanceEntity fromSearchHit(String workflowInstanceString, ObjectMapper objectMapper) {
    WorkflowInstanceEntity workflowInstance;
    try {
      workflowInstance = objectMapper.readValue(workflowInstanceString, WorkflowInstanceEntity.class);
    } catch (IOException e) {
      logger.error("Error while reading workflow instance from Elasticsearch!", e);
      throw new RuntimeException("Error while reading workflow instance from Elasticsearch!", e);
    }
    return workflowInstance;
  }

  public static void processBulkRequest(BulkRequestBuilder bulkRequest) throws PersistenceException {
    processBulkRequest(bulkRequest, false);
  }

  public static void processBulkRequest(BulkRequestBuilder bulkRequest, boolean refreshImmediately) throws PersistenceException {
    if (bulkRequest.request().requests().size() > 0) {
      try {
        if (refreshImmediately) {
          bulkRequest = bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        }
        final BulkResponse bulkItemResponses = bulkRequest.execute().get();
        final BulkItemResponse[] items = bulkItemResponses.getItems();
        for (BulkItemResponse responseItem : items) {
          if (responseItem.isFailed()) {
            logger.error(String.format("%s failed for type [%s] and id [%s]: %s", responseItem.getOpType(), responseItem.getType(), responseItem.getId(),
              responseItem.getFailureMessage()), responseItem.getFailure().getCause());
            throw new PersistenceException("Operation failed: " + responseItem.getFailureMessage(), responseItem.getFailure().getCause(), responseItem.getItemId());
          }
        }
      } catch (InterruptedException | java.util.concurrent.ExecutionException ex) {
        throw new PersistenceException("Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
      }
    }
  }

  public static void executeUpdate(UpdateRequestBuilder updateRequest) throws PersistenceException {
      try {
        updateRequest.get();
      } catch (ElasticsearchException e)  {
        final String errorMessage = String.format("Update request failed for type [%s] and id [%s] with the message [%s].",
          updateRequest.request().type(), updateRequest.request().id(), e.getMessage());
        logger.error(errorMessage, e);
        throw new PersistenceException(errorMessage, e);
      }
  }

}
