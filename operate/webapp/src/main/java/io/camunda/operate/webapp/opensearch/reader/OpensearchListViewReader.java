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

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ALL;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.operate.webapp.opensearch.OpenSearchQueryHelper;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchListViewReader implements ListViewReader {
  private static final Logger logger = LoggerFactory.getLogger(OpensearchListViewReader.class);

  private final RichOpenSearchClient richOpenSearchClient;

  private final OpenSearchQueryHelper openSearchQueryHelper;

  private final ObjectMapper objectMapper;

  private final ListViewTemplate listViewTemplate;

  private final OperationReader operationReader;

  public OpensearchListViewReader(
      final RichOpenSearchClient richOpenSearchClient,
      final OpenSearchQueryHelper openSearchQueryHelper,
      final ObjectMapper objectMapper,
      final ListViewTemplate listViewTemplate,
      final OperationReader operationReader) {
    this.richOpenSearchClient = richOpenSearchClient;
    this.openSearchQueryHelper = openSearchQueryHelper;
    this.objectMapper = objectMapper;
    this.listViewTemplate = listViewTemplate;
    this.operationReader = operationReader;
  }

  @Override
  public ListViewResponseDto queryProcessInstances(
      final ListViewRequestDto processInstanceRequest) {
    final ListViewResponseDto result = new ListViewResponseDto();

    final List<ProcessInstanceForListViewEntity> processInstanceEntities =
        queryListView(processInstanceRequest, result);
    final List<Long> processInstanceKeys =
        CollectionUtil.map(
            processInstanceEntities,
            processInstanceEntity -> Long.valueOf(processInstanceEntity.getId()));

    final Map<Long, List<OperationEntity>> operationsPerProcessInstance =
        operationReader.getOperationsPerProcessInstanceKey(processInstanceKeys);

    final List<ListViewProcessInstanceDto> processInstanceDtoList =
        ListViewProcessInstanceDto.createFrom(
            processInstanceEntities, operationsPerProcessInstance, objectMapper);
    result.setProcessInstances(processInstanceDtoList);
    return result;
  }

  @Override
  public List<ProcessInstanceForListViewEntity> queryListView(
      final ListViewRequestDto processInstanceRequest, final ListViewResponseDto result) {
    final RequestDSL.QueryType queryType =
        processInstanceRequest.getQuery().isFinished() ? ALL : ONLY_RUNTIME;
    final Query query =
        constantScore(
            withTenantCheck(
                and(
                    term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                    openSearchQueryHelper.createQueryFragment(processInstanceRequest.getQuery()))));

    logger.debug("Process instance search request: \n{}", query);

    final var searchRequestBuilder = searchRequestBuilder(listViewTemplate, queryType).query(query);

    applySorting(searchRequestBuilder, processInstanceRequest);

    searchRequestBuilder.size(processInstanceRequest.getPageSize());

    final SearchResponse<ProcessInstanceForListViewEntity> response =
        richOpenSearchClient
            .doc()
            .fixedSearch(searchRequestBuilder.build(), ProcessInstanceForListViewEntity.class);

    result.setTotalCount(response.hits().total().value());

    final List<ProcessInstanceForListViewEntity> processInstanceEntities =
        response.hits().hits().stream()
            .map(
                hit -> {
                  final ProcessInstanceForListViewEntity entity = hit.source();
                  entity.setSortValues(hit.sort().toArray());
                  return entity;
                })
            .toList();

    if (processInstanceRequest.getSearchBefore() != null) {
      return CollectionUtil.reversedView(processInstanceEntities);
    }

    return processInstanceEntities;
  }

  @Override
  public Tuple<String, String> getCalledProcessInstanceIdAndNameByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final String[] calledProcessInstanceId = {null};
    final String[] calledProcessDefinitionName = {null};
    findCalledProcessInstance(
        flowNodeInstanceId,
        hit -> {
          final var source = hit.source();
          calledProcessInstanceId[0] = hit.id();
          var processName = source.getProcessName();
          if (processName == null) {
            processName = source.getBpmnProcessId();
          }
          calledProcessDefinitionName[0] = processName;
        });
    return Tuple.of(calledProcessInstanceId[0], calledProcessDefinitionName[0]);
  }

  private void findCalledProcessInstance(
      final String flowNodeInstanceId,
      final Consumer<Hit<ProcessInstanceForListViewEntity>> processInstanceConsumer) {
    final var request =
        searchRequestBuilder(listViewTemplate.getAlias())
            .query(withTenantCheck(term(PARENT_FLOW_NODE_INSTANCE_KEY, flowNodeInstanceId)))
            .source(sourceInclude(ListViewTemplate.PROCESS_NAME, ListViewTemplate.BPMN_PROCESS_ID));
    final var response =
        richOpenSearchClient.doc().search(request, ProcessInstanceForListViewEntity.class);
    if (response.hits().total().value() >= 1) {
      processInstanceConsumer.accept(response.hits().hits().get(0));
    }
  }

  private String getSortBy(final ListViewRequestDto request) {
    if (request.getSorting() != null) {
      String sortBy = request.getSorting().getSortBy();
      if (sortBy.equals(ListViewRequestDto.SORT_BY_PARENT_INSTANCE_ID)) {
        sortBy = ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY;
      } else if (sortBy.equals(ListViewRequestDto.SORT_BY_TENANT_ID)) {
        sortBy = ListViewTemplate.TENANT_ID;
      }
      if (sortBy.equals(ListViewTemplate.ID)) {
        // we sort by id as numbers, not as strings
        sortBy = ListViewTemplate.KEY;
      }
      return sortBy;
    }
    return null;
  }

  private void applySorting(
      final SearchRequest.Builder searchRequest, final ListViewRequestDto request) {
    final String sortBy = getSortBy(request);
    final boolean directSorting =
        request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      final SortOrder directOrder =
          "asc".equals(request.getSorting().getSortOrder()) ? SortOrder.Asc : SortOrder.Desc;
      if (directSorting) {
        searchRequest.sort(sortOptions(sortBy, directOrder, "_last"));
      } else {
        searchRequest.sort(sortOptions(sortBy, reverseOrder(directOrder), "_first"));
      }
    }

    final Object[] querySearchAfter;
    if (directSorting) {
      searchRequest.sort(sortOptions(ListViewTemplate.KEY, SortOrder.Asc));
      querySearchAfter = request.getSearchAfter(objectMapper);
    } else {
      searchRequest.sort(sortOptions(ListViewTemplate.KEY, SortOrder.Desc));
      querySearchAfter = request.getSearchBefore(objectMapper);
    }
    searchRequest.size(request.getPageSize());
    if (querySearchAfter != null) {
      searchRequest.searchAfter(CollectionUtil.toSafeListOfStrings(querySearchAfter));
    }
  }
}
