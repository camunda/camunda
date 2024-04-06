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
package io.camunda.tasklist.store.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.CompositeBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregate;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.FieldCollapse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessStoreOpenSearch implements ProcessStore {

  private static final Boolean CASE_INSENSITIVE = true;
  private static final String BPMN_PROCESS_ID_TENANT_ID_AGG_NAME = "bpmnProcessId_tenantId_buckets";
  private static final String TOP_HITS_AGG_NAME = "top_hit_doc";
  private static final String DEFINITION_ID_TERMS_SOURCE_NAME = "group_by_definition_id";
  private static final String TENANT_ID_TERMS_SOURCE_NAME = "group_by_tenant_id";

  @Autowired private ProcessIndex processIndex;

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public ProcessEntity getProcessByProcessDefinitionKey(String processDefinitionKey) {
    try {
      final FieldCollapse keyCollapse = new FieldCollapse.Builder().field(ProcessIndex.KEY).build();
      final SortOptions sortOptions =
          new SortOptions.Builder()
              .field(FieldSort.of(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc)))
              .build();

      final var searchRequestBuilder =
          new SearchRequest.Builder()
              .index(List.of(processIndex.getAlias()))
              .query(
                  q ->
                      q.term(
                          t ->
                              t.field(ProcessIndex.KEY).value(FieldValue.of(processDefinitionKey))))
              .collapse(keyCollapse)
              .sort(sortOptions)
              .size(1);

      final SearchResponse<ProcessEntity> response =
          tenantAwareClient.search(searchRequestBuilder, ProcessEntity.class);
      if (response.hits().hits().size() > 0) {
        return response.hits().hits().get(0).source();
      } else {
        throw new NotFoundException(
            String.format("Process with key %s not found", processDefinitionKey));
      }
    } catch (IOException | OpenSearchException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  @Override
  public ProcessEntity getProcessByBpmnProcessId(String bpmnProcessId) {
    return getProcessByBpmnProcessId(bpmnProcessId, null);
  }

  @Override
  public ProcessEntity getProcessByBpmnProcessId(
      final String bpmnProcessId, final String tenantId) {
    final FieldCollapse keyCollapse =
        new FieldCollapse.Builder().field(ProcessIndex.PROCESS_DEFINITION_ID).build();
    final SortOptions sortOptions =
        new SortOptions.Builder()
            .field(FieldSort.of(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc)))
            .build();

    final Query qb;
    final Query functionQuery =
        new Query.Builder()
            .term(
                term ->
                    term.field(ProcessIndex.PROCESS_DEFINITION_ID)
                        .value(FieldValue.of(bpmnProcessId)))
            .build();
    if (tasklistProperties.getMultiTenancy().isEnabled() && StringUtils.isNotBlank(tenantId)) {
      qb =
          OpenSearchUtil.joinWithAnd(
              new Query.Builder()
                  .term(term -> term.field(ProcessIndex.TENANT_ID).value(FieldValue.of(tenantId)))
                  .build(),
              functionQuery);
    } else {
      qb = functionQuery;
    }

    final SearchRequest.Builder searchRequestBuilder =
        new SearchRequest.Builder()
            .index(List.of(processIndex.getAlias()))
            .query(qb)
            .collapse(keyCollapse)
            .sort(sortOptions)
            .size(1);
    final SearchResponse<ProcessEntity> response;
    try {
      response = tenantAwareClient.search(searchRequestBuilder, ProcessEntity.class);
      if (response.hits().hits().size() > 0) {
        return response.hits().hits().get(0).source();
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", bpmnProcessId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public ProcessEntity getProcess(String processId) {

    final SearchResponse<ProcessEntity> response;
    try {
      final var searchRequestBuilder =
          new SearchRequest.Builder()
              .index(List.of(processIndex.getAlias()))
              .query(q -> q.term(t -> t.field(ProcessIndex.KEY).value(FieldValue.of(processId))));
      response = tenantAwareClient.search(searchRequestBuilder, ProcessEntity.class);

      final long totalHits = response.hits().total().value();
      if (totalHits == 1L) {
        return response.hits().hits().get(0).source();
      } else if (totalHits > 1) {
        throw new TasklistRuntimeException(
            String.format("Could not find unique process with id '%s'.", processId));
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", processId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<ProcessEntity> getProcesses(
      final List<String> processDefinitions, final String tenantId, final Boolean isStartedByForm) {
    final FieldCollapse keyCollapse =
        new FieldCollapse.Builder().field(ProcessIndex.PROCESS_DEFINITION_ID).build();
    final SortOptions sortOptions =
        new SortOptions.Builder()
            .field(FieldSort.of(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc)))
            .build();

    final Query q;

    if (tasklistProperties.isSelfManaged()) {

      if (processDefinitions.size() == 0) {
        return new ArrayList<>();
      }

      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        q =
            QueryBuilders.bool()
                .must(t -> t.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t ->
                                t.field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(FieldValue.of(""))))
                .build()
                ._toQuery();
      } else {
        q =
            QueryBuilders.bool()
                .must(
                    m ->
                        m.terms(
                            terms ->
                                terms
                                    .field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .terms(
                                        v ->
                                            v.value(
                                                processDefinitions.stream()
                                                    .map(pd -> FieldValue.of(pd))
                                                    .collect(Collectors.toList())))))
                .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t ->
                                t.field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(FieldValue.of(""))))
                .build()
                ._toQuery();
      }
    } else {
      q =
          QueryBuilders.bool()
              .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
              .mustNot(
                  mn ->
                      mn.term(
                          t ->
                              t.field(ProcessIndex.PROCESS_DEFINITION_ID).value(FieldValue.of(""))))
              .build()
              ._toQuery();
    }

    final Query applyTenantIdFilter = addFilterOnTenantIdIfRequired(q, tenantId);
    final Query finalQueryWithStartedByForm =
        addFilterIsStartedByForm(applyTenantIdFilter, isStartedByForm);

    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(finalQueryWithStartedByForm);
  }

  @Override
  public List<ProcessEntity> getProcesses(
      String search,
      final List<String> processDefinitions,
      final String tenantId,
      final Boolean isStartedByForm) {

    if (search == null || search.isBlank()) {
      return getProcesses(processDefinitions, tenantId, isStartedByForm);
    }

    final Query query;
    final String regexSearch = String.format(".*%s.*", search);

    if (tasklistProperties.getIdentity().isResourcePermissionsEnabled()) {
      if (processDefinitions.size() == 0) {
        return new ArrayList<ProcessEntity>();
      }
      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        query =
            QueryBuilders.bool()
                .should(s -> s.term(t -> t.field(ProcessIndex.ID).value(FieldValue.of(search))))
                .should(s -> s.regexp(regex -> regex.field(ProcessIndex.NAME).value(regexSearch)))
                .should(
                    s ->
                        s.regexp(
                            regex ->
                                regex.field(ProcessIndex.PROCESS_DEFINITION_ID).value(regexSearch)))
                .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t ->
                                t.field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(FieldValue.of(""))))
                .minimumShouldMatch("1")
                .build()
                ._toQuery();
      } else {
        query =
            QueryBuilders.bool()
                .should(s -> s.term(t -> t.field(ProcessIndex.ID).value(FieldValue.of(search))))
                .should(
                    s ->
                        s.regexp(
                            regex ->
                                regex
                                    .field(ProcessIndex.NAME)
                                    .value(regexSearch)
                                    .caseInsensitive(CASE_INSENSITIVE)))
                .should(
                    s ->
                        s.regexp(
                            regex ->
                                regex
                                    .field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(regexSearch)
                                    .caseInsensitive(CASE_INSENSITIVE)))
                .must(
                    m ->
                        m.terms(
                            terms ->
                                terms
                                    .field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .terms(
                                        v ->
                                            v.value(
                                                processDefinitions.stream()
                                                    .map(pd -> FieldValue.of(pd))
                                                    .collect(Collectors.toList())))))
                .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t ->
                                t.field(ProcessIndex.PROCESS_DEFINITION_ID)
                                    .value(FieldValue.of(""))))
                .minimumShouldMatch("1")
                .build()
                ._toQuery();
      }
    } else {
      query =
          QueryBuilders.bool()
              .should(s -> s.term(t -> t.field(ProcessIndex.ID).value(FieldValue.of(search))))
              .should(
                  s ->
                      s.regexp(
                          regex ->
                              regex
                                  .field(ProcessIndex.NAME)
                                  .value(regexSearch)
                                  .caseInsensitive(CASE_INSENSITIVE)))
              .should(
                  s ->
                      s.regexp(
                          regex ->
                              regex
                                  .field(ProcessIndex.PROCESS_DEFINITION_ID)
                                  .value(regexSearch)
                                  .caseInsensitive(CASE_INSENSITIVE)))
              .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
              .mustNot(
                  mn ->
                      mn.term(
                          t ->
                              t.field(ProcessIndex.PROCESS_DEFINITION_ID).value(FieldValue.of(""))))
              .minimumShouldMatch("1")
              .build()
              ._toQuery();
    }

    final Query applyTenantIdFilter = addFilterOnTenantIdIfRequired(query, tenantId);
    final Query finalQueryWithStartedByForm =
        addFilterIsStartedByForm(applyTenantIdFilter, isStartedByForm);

    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(finalQueryWithStartedByForm);
  }

  private Query addFilterOnTenantIdIfRequired(final Query query, final String tenantId) {
    if (tasklistProperties.getMultiTenancy().isEnabled() && StringUtils.isNotBlank(tenantId)) {
      final Query tenantQuery =
          new Query.Builder()
              .term(term -> term.field(ProcessIndex.TENANT_ID).value(FieldValue.of(tenantId)))
              .build();
      return OpenSearchUtil.joinWithAnd(tenantQuery, query);
    }
    return query;
  }

  private Query addFilterIsStartedByForm(final Query query, final Boolean isStartedByForm) {
    // Construct queries to check if FORM_KEY or FORM_ID is not null
    // This is in order to consider a process as started by form but not public
    if (Boolean.TRUE.equals(isStartedByForm)) {
      final Query existsFormKeyQuery =
          new Query.Builder().exists(e -> e.field(ProcessIndex.FORM_KEY)).build();
      final Query existsFormIdQuery =
          new Query.Builder().exists(e -> e.field(ProcessIndex.FORM_ID)).build();

      final Query boolQuery =
          new Query.Builder()
              .bool(
                  b ->
                      b.should(existsFormKeyQuery)
                          .should(existsFormIdQuery)
                          .minimumShouldMatch("1"))
              .build();

      return OpenSearchUtil.joinWithAnd(boolQuery, query);
    }
    if (Boolean.FALSE.equals(isStartedByForm)) {
      final Query notExistsFormKeyQuery =
          new Query.Builder()
              .bool(b -> b.mustNot(mn -> mn.exists(e -> e.field(ProcessIndex.FORM_KEY))))
              .build();
      final Query notExistsFormIdQuery =
          new Query.Builder()
              .bool(b -> b.mustNot(mn -> mn.exists(e -> e.field(ProcessIndex.FORM_ID))))
              .build();

      final Query boolQuery =
          new Query.Builder()
              .bool(b -> b.must(notExistsFormKeyQuery).must(notExistsFormIdQuery))
              .build();

      return OpenSearchUtil.joinWithAnd(boolQuery, query);
    }
    // No filter is applied if isStartedByForm is null
    return query;
  }

  private List<ProcessEntity> getProcessEntityUniqueByProcessDefinitionIdAndTenantId(Query query) {
    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(processIndex.getAlias())
            .query(query)
            .size(0) // Set size to 0 to retrieve only aggregation results
            .aggregations(
                BPMN_PROCESS_ID_TENANT_ID_AGG_NAME,
                Aggregation.of(
                    agg ->
                        agg.composite(
                                CompositeAggregation.of(
                                    ca ->
                                        ca.sources(
                                                Map.of(
                                                    DEFINITION_ID_TERMS_SOURCE_NAME,
                                                    CompositeAggregationSource.of(
                                                        cas ->
                                                            cas.terms(
                                                                TermsAggregation.of(
                                                                    ta ->
                                                                        ta.field(
                                                                            ProcessIndex
                                                                                .PROCESS_DEFINITION_ID))))),
                                                Map.of(
                                                    TENANT_ID_TERMS_SOURCE_NAME,
                                                    CompositeAggregationSource.of(
                                                        cas ->
                                                            cas.terms(
                                                                TermsAggregation.of(
                                                                    ta ->
                                                                        ta.field(
                                                                            ProcessIndex
                                                                                .TENANT_ID))))))
                                            .size(OpenSearchUtil.QUERY_MAX_SIZE)))
                            .aggregations(
                                TOP_HITS_AGG_NAME,
                                TopHitsAggregation.of(
                                        ta ->
                                            ta.sort(
                                                    SortOptions.of(
                                                        s ->
                                                            s.field(
                                                                f ->
                                                                    f.field(ProcessIndex.VERSION)
                                                                        .order(SortOrder.Desc))))
                                                .size(1))
                                    ._toAggregation())));
    try {
      final SearchResponse<ProcessEntity> response =
          tenantAwareClient.search(searchRequest, ProcessEntity.class);

      final Buckets<CompositeBucket> buckets =
          response.aggregations().get(BPMN_PROCESS_ID_TENANT_ID_AGG_NAME).composite().buckets();
      final List<ProcessEntity> results = new ArrayList<>();
      for (final CompositeBucket bucket : buckets.array()) {
        final TopHitsAggregate topHits = bucket.aggregations().get(TOP_HITS_AGG_NAME).topHits();
        results.addAll(
            OpenSearchUtil.mapSearchHits(topHits.hits().hits(), objectMapper, ProcessEntity.class));
      }

      return results;

    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<ProcessEntity> getProcessesStartedByForm() {
    final Query query =
        QueryBuilders.bool()
            .must(m -> m.exists(e -> e.field(ProcessIndex.PROCESS_DEFINITION_ID)))
            .mustNot(
                mn ->
                    mn.term(
                        t -> t.field(ProcessIndex.PROCESS_DEFINITION_ID).value(FieldValue.of(""))))
            .build()
            ._toQuery();
    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(query).stream()
        .filter(ProcessEntity::isStartedByForm)
        .toList();
  }
}
