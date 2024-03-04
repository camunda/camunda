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
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchIncidentStore implements IncidentStore {

  public static Query ACTIVE_INCIDENT_QUERY =
      TermQuery.of(
              q ->
                  q.field(IncidentTemplate.STATE).value(FieldValue.of(IncidentState.ACTIVE.name())))
          ._toQuery();
  private static final Logger logger = LoggerFactory.getLogger(OpensearchIncidentStore.class);
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private OperateProperties operateProperties;

  private Query activeIncidentConstantScore(Query q) {
    return constantScore(and(ACTIVE_INCIDENT_QUERY, q));
  }

  @Override
  public IncidentEntity getIncidentById(Long incidentKey) {
    var key = incidentKey.toString();
    var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(withTenantCheck(activeIncidentConstantScore(ids(key))));
    return richOpenSearchClient.doc().searchUnique(searchRequestBuilder, IncidentEntity.class, key);
  }

  @Override
  public List<IncidentEntity> getIncidentsWithErrorTypesFor(
      String treePath, List<Map<ErrorType, Long>> errorTypes) {
    final String errorTypesAggName = "errorTypesAgg";
    var request =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(
                withTenantCheck(
                    constantScore(
                        and(term(IncidentTemplate.TREE_PATH, treePath), ACTIVE_INCIDENT_QUERY))))
            .aggregations(
                Map.of(
                    errorTypesAggName,
                    termAggregation(
                            IncidentTemplate.ERROR_TYPE,
                            ErrorType.values().length,
                            Map.of("_key", SortOrder.Asc))
                        ._toAggregation()));

    OpenSearchDocumentOperations.AggregatedResult<IncidentEntity> result =
        richOpenSearchClient.doc().scrollValuesAndAggregations(request, IncidentEntity.class);

    result
        .aggregates()
        .get(errorTypesAggName)
        .sterms()
        .buckets()
        .array()
        .forEach(
            b -> {
              ErrorType errorType = ErrorType.valueOf(b.key());
              errorTypes.add(Map.of(errorType, b.docCount()));
            });

    return result.values();
  }

  @Override
  public List<IncidentEntity> getIncidentsByProcessInstanceKey(Long processInstanceKey) {
    var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(
                withTenantCheck(
                    activeIncidentConstantScore(
                        term(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
            .sort(sortOptions(IncidentTemplate.CREATION_TIME, SortOrder.Asc));

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, IncidentEntity.class);
  }

  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys) {
    record Result(Long processInstanceKey) {}
    final int batchSize = operateProperties.getOpensearch().getBatchSize();
    var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, RequestDSL.QueryType.ONLY_RUNTIME)
            .query(
                withTenantCheck(
                    activeIncidentConstantScore(
                        longTerms(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys))))
            .source(sourceInclude(IncidentTemplate.PROCESS_INSTANCE_KEY))
            .size(batchSize);
    final Map<Long, List<Long>> result = new HashMap<>();

    richOpenSearchClient
        .doc()
        .search(searchRequestBuilder, Result.class)
        .hits()
        .hits()
        .forEach(
            hit ->
                CollectionUtil.addToMap(
                    result, hit.source().processInstanceKey(), Long.valueOf(hit.id())));

    return result;
  }
}
