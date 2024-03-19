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
package io.camunda.operate.webapp.elasticsearch.reader;

import static org.elasticsearch.client.Requests.searchRequest;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class BatchOperationReader implements io.camunda.operate.webapp.reader.BatchOperationReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationReader.class);

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private UserService userService;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public List<BatchOperationEntity> getBatchOperations(
      BatchOperationRequestDto batchOperationRequestDto) {

    final SearchRequest searchRequest = createSearchRequest(batchOperationRequestDto);
    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final List<BatchOperationEntity> batchOperationEntities =
          ElasticsearchUtil.mapSearchHits(
              searchResponse.getHits().getHits(),
              (sh) -> {
                final BatchOperationEntity entity =
                    ElasticsearchUtil.fromSearchHit(
                        sh.getSourceAsString(), objectMapper, BatchOperationEntity.class);
                entity.setSortValues(sh.getSortValues());
                return entity;
              });
      if (batchOperationRequestDto.getSearchBefore() != null) {
        Collections.reverse(batchOperationEntities);
      }
      return batchOperationEntities;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while getting page of batch operations list: %s",
              e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private SearchRequest createSearchRequest(BatchOperationRequestDto batchOperationRequestDto) {
    final QueryBuilder queryBuilder =
        termQuery(BatchOperationTemplate.USERNAME, userService.getCurrentUser().getUsername());

    final SortBuilder sort1, sort2;
    final Object[] querySearchAfter;

    final Object[] searchAfter = batchOperationRequestDto.getSearchAfter(objectMapper);
    final Object[] searchBefore = batchOperationRequestDto.getSearchBefore(objectMapper);
    if (searchAfter != null
        || searchBefore == null) { // this sorting is also the default one for 1st page
      sort1 =
          new FieldSortBuilder(BatchOperationTemplate.END_DATE)
              .order(SortOrder.DESC)
              .missing("_first");
      sort2 = new FieldSortBuilder(BatchOperationTemplate.START_DATE).order(SortOrder.DESC);
      querySearchAfter = searchAfter; // may be null
    } else { // searchBefore != null
      // reverse sorting
      sort1 =
          new FieldSortBuilder(BatchOperationTemplate.END_DATE)
              .order(SortOrder.ASC)
              .missing("_last");
      sort2 = new FieldSortBuilder(BatchOperationTemplate.START_DATE).order(SortOrder.ASC);
      querySearchAfter = searchBefore;
    }

    final SearchSourceBuilder sourceBuilder =
        searchSource()
            .query(constantScoreQuery(queryBuilder))
            .sort(sort1)
            .sort(sort2)
            .size(batchOperationRequestDto.getPageSize());
    if (querySearchAfter != null) {
      sourceBuilder.searchAfter(querySearchAfter);
    }
    return searchRequest(batchOperationTemplate.getAlias()).source(sourceBuilder);
  }
}
