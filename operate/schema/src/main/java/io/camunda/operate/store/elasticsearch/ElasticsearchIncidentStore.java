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
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchIncidentStore implements IncidentStore {

  public static final QueryBuilder ACTIVE_INCIDENT_QUERY =
      termQuery(IncidentTemplate.STATE, IncidentState.ACTIVE);
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchIncidentStore.class);
  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private OperateProperties operateProperties;

  @Override
  public IncidentEntity getIncidentById(Long incidentKey) {
    final IdsQueryBuilder idsQ = idsQuery().addIds(incidentKey.toString());
    final ConstantScoreQueryBuilder query =
        constantScoreQuery(joinWithAnd(idsQ, ACTIVE_INCIDENT_QUERY));
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(new SearchSourceBuilder().query(query));
    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        return ElasticsearchUtil.fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(),
            objectMapper,
            IncidentEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Could not find unique incident with key '%s'.", incidentKey));
      } else {
        throw new NotFoundException(
            String.format("Could not find incident with key '%s'.", incidentKey));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining incident: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<IncidentEntity> getIncidentsWithErrorTypesFor(
      String treePath, List<Map<ErrorType, Long>> errorTypes) {
    final TermQueryBuilder processInstanceQuery = termQuery(IncidentTemplate.TREE_PATH, treePath);

    final String errorTypesAggName = "errorTypesAgg";

    final TermsAggregationBuilder errorTypesAgg =
        terms(errorTypesAggName)
            .field(IncidentTemplate.ERROR_TYPE)
            .size(ErrorType.values().length)
            .order(BucketOrder.key(true));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(
                        constantScoreQuery(
                            joinWithAnd(processInstanceQuery, ACTIVE_INCIDENT_QUERY)))
                    .aggregation(errorTypesAgg));

    try {
      return tenantAwareClient.search(
          searchRequest,
          () -> {
            return ElasticsearchUtil.scroll(
                searchRequest,
                IncidentEntity.class,
                objectMapper,
                esClient,
                null,
                aggs ->
                    ((Terms) aggs.get(errorTypesAggName))
                        .getBuckets()
                        .forEach(
                            b -> {
                              final ErrorType errorType = ErrorType.valueOf(b.getKeyAsString());
                              errorTypes.add(Map.of(errorType, b.getDocCount()));
                            }));
          });
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<IncidentEntity> getIncidentsByProcessInstanceKey(Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final ConstantScoreQueryBuilder query =
        constantScoreQuery(joinWithAnd(processInstanceKeyQuery, ACTIVE_INCIDENT_QUERY));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(query)
                    .sort(IncidentTemplate.CREATION_TIME, SortOrder.ASC));

    try {
      return tenantAwareClient.search(
          searchRequest,
          () -> {
            return ElasticsearchUtil.scroll(
                searchRequest, IncidentEntity.class, objectMapper, esClient);
          });
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys) {
    final QueryBuilder processInstanceKeysQuery =
        constantScoreQuery(
            joinWithAnd(
                termsQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys),
                ACTIVE_INCIDENT_QUERY));
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(processInstanceKeysQuery)
                    .fetchSource(IncidentTemplate.PROCESS_INSTANCE_KEY, null)
                    .size(batchSize));

    final Map<Long, List<Long>> result = new HashMap<>();
    try {
      tenantAwareClient.search(
          searchRequest,
          () -> {
            scrollWith(
                searchRequest,
                esClient,
                searchHits -> {
                  for (SearchHit hit : searchHits.getHits()) {
                    CollectionUtil.addToMap(
                        result,
                        Long.valueOf(
                            hit.getSourceAsMap()
                                .get(IncidentTemplate.PROCESS_INSTANCE_KEY)
                                .toString()),
                        Long.valueOf(hit.getId()));
                  }
                },
                null,
                null);
            return null;
          });
      return result;
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
}
