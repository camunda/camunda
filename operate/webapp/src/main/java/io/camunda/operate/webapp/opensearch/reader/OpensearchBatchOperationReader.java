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
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.reversedView;
import static io.camunda.operate.util.ConversionUtils.toStringArray;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.reader.BatchOperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import java.util.Arrays;
import java.util.List;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBatchOperationReader implements BatchOperationReader {
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private UserService<?> userService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Override
  public List<BatchOperationEntity> getBatchOperations(
      BatchOperationRequestDto batchOperationRequestDto) {
    final var searchRequestBuilder = createSearchRequest(batchOperationRequestDto);
    final List<BatchOperationEntity> batchOperationEntities =
        richOpenSearchClient
            .doc()
            .search(searchRequestBuilder, BatchOperationEntity.class)
            .hits()
            .hits()
            .stream()
            .map(
                hit -> {
                  final BatchOperationEntity entity = hit.source();
                  entity.setSortValues(hit.sort().toArray());
                  return entity;
                })
            .toList();

    if (batchOperationRequestDto.getSearchBefore() != null) {
      return reversedView(batchOperationEntities);
    }
    return batchOperationEntities;
  }

  private SearchRequest.Builder createSearchRequest(
      BatchOperationRequestDto batchOperationRequestDto) {
    final SortOptions sort1, sort2;
    final Object[] querySearchAfter;

    final Object[] searchAfter = batchOperationRequestDto.getSearchAfter(objectMapper);
    final Object[] searchBefore = batchOperationRequestDto.getSearchBefore(objectMapper);
    if (searchAfter != null
        || searchBefore == null) { // this sorting is also the default one for 1st page
      sort1 = sortOptions(BatchOperationTemplate.END_DATE, SortOrder.Desc, "_first");
      sort2 = sortOptions(BatchOperationTemplate.START_DATE, SortOrder.Desc);
      querySearchAfter = searchAfter; // may be null
    } else { // searchBefore != null
      // reverse sorting
      sort1 = sortOptions(BatchOperationTemplate.END_DATE, SortOrder.Asc, "_last");
      sort2 = sortOptions(BatchOperationTemplate.START_DATE, SortOrder.Asc);
      querySearchAfter = searchBefore;
    }

    final var searchRequestBuilder =
        searchRequestBuilder(batchOperationTemplate.getAlias())
            .query(
                constantScore(
                    term(
                        BatchOperationTemplate.USERNAME,
                        userService.getCurrentUser().getUsername())))
            .sort(sort1, sort2)
            .size(batchOperationRequestDto.getPageSize());

    if (querySearchAfter != null) {
      searchRequestBuilder.searchAfter(Arrays.asList(toStringArray(querySearchAfter)));
    }

    return searchRequestBuilder;
  }
}
