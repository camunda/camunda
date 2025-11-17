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

import static io.camunda.operate.schema.indices.MetricIndex.EVENT;
import static io.camunda.operate.schema.indices.MetricIndex.EVENT_TIME;
import static io.camunda.operate.schema.indices.MetricIndex.VALUE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.cardinalityAggregation;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.gteLte;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.or;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.store.AggregationResult;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.time.OffsetDateTime;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchMetricsStore implements MetricsStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchMetricsStore.class);
  @Autowired private MetricIndex metricIndex;
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  private AggregationResult searchWithAggregation(
      SearchRequest.Builder requestBuilder, String aggregationName) {
    final Aggregate aggregate;

    try {
      aggregate =
          richOpenSearchClient
              .doc()
              .search(requestBuilder, Object.class)
              .aggregations()
              .get(aggregationName);
    } catch (OperateRuntimeException e) {
      return AggregationResult.ERROR;
    }

    if (aggregate == null) {
      throw new OperateRuntimeException("Search with aggregation returned no aggregation");
    }

    if (!aggregate.isCardinality()) {
      throw new OperateRuntimeException("Unexpected response for aggregations");
    }

    return new AggregationResult(false, aggregate.cardinality().value());
  }

  @Override
  public Long retrieveProcessInstanceCount(OffsetDateTime startTime, OffsetDateTime endTime) {
    final var searchRequestBuilder =
        searchRequestBuilder(metricIndex.getFullQualifiedName())
            .query(
                and(
                    gteLte(EVENT_TIME, startTime, endTime),
                    or(
                        term(EVENT, MetricsStore.EVENT_PROCESS_INSTANCE_FINISHED),
                        term(EVENT, EVENT_PROCESS_INSTANCE_STARTED))))
            .aggregations(
                PROCESS_INSTANCES_AGG_NAME,
                cardinalityAggregation(VALUE, PRECISION_THRESHOLD)._toAggregation());

    return searchWithAggregation(searchRequestBuilder, PROCESS_INSTANCES_AGG_NAME).totalDocs();
  }

  @Override
  public Long retrieveDecisionInstanceCount(
      final OffsetDateTime startTime, final OffsetDateTime endTime) {
    final var searchRequestBuilder =
        searchRequestBuilder(metricIndex.getFullQualifiedName())
            .query(
                and(
                    term(EVENT, MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED),
                    gteLte(EVENT_TIME, startTime, endTime)))
            .aggregations(
                DECISION_INSTANCES_AGG_NAME,
                cardinalityAggregation(VALUE, PRECISION_THRESHOLD)._toAggregation());

    return searchWithAggregation(searchRequestBuilder, DECISION_INSTANCES_AGG_NAME).totalDocs();
  }

  @Override
  public void registerProcessInstanceStartEvent(
      String processInstanceKey,
      String tenantId,
      OffsetDateTime timestamp,
      BatchRequest batchRequest)
      throws PersistenceException {
    final MetricEntity metric =
        createProcessInstanceStartedKey(processInstanceKey, tenantId, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  @Override
  public void registerDecisionInstanceCompleteEvent(
      final String decisionInstanceKey,
      String tenantId,
      final OffsetDateTime timestamp,
      BatchRequest batchRequest)
      throws PersistenceException {
    final MetricEntity metric =
        createDecisionsInstanceEvaluatedKey(decisionInstanceKey, tenantId, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  private MetricEntity createProcessInstanceStartedKey(
      String processInstanceKey, String tenantId, OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_PROCESS_INSTANCE_STARTED)
        .setValue(processInstanceKey)
        .setTenantId(tenantId)
        .setEventTime(timestamp);
  }

  private MetricEntity createDecisionsInstanceEvaluatedKey(
      String decisionInstanceKey, String tenantId, OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(decisionInstanceKey)
        .setTenantId(tenantId)
        .setEventTime(timestamp);
  }
}
