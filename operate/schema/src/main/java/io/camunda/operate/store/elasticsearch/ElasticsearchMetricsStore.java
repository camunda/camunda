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

import static io.camunda.operate.schema.indices.MetricIndex.*;
import static io.camunda.operate.schema.indices.MetricIndex.VALUE;
import static io.camunda.operate.store.elasticsearch.dao.Query.range;
import static io.camunda.operate.store.elasticsearch.dao.Query.whereEquals;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.dao.Query;
import io.camunda.operate.store.elasticsearch.dao.UsageMetricDAO;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchMetricsStore implements MetricsStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchMetricsStore.class);
  @Autowired private MetricIndex metricIndex;

  @Autowired private UsageMetricDAO dao;

  @Override
  public Long retrieveProcessInstanceCount(OffsetDateTime startTime, OffsetDateTime endTime) {
    final Query query =
        Query.whereEquals(EVENT, MetricsStore.EVENT_PROCESS_INSTANCE_FINISHED)
            .or(whereEquals(EVENT, EVENT_PROCESS_INSTANCE_STARTED))
            .and(range(EVENT_TIME, startTime, endTime))
            .aggregate(PROCESS_INSTANCES_AGG_NAME, VALUE, PRECISION_THRESHOLD);

    final var response = dao.searchWithAggregation(query);
    if (response.hasError()) {
      final String message = "Error while retrieving process instance count between dates";
      LOGGER.error(message);
      throw new OperateRuntimeException(message);
    }

    return response.totalDocs();
  }

  @Override
  public Long retrieveDecisionInstanceCount(
      final OffsetDateTime startTime, final OffsetDateTime endTime) {
    final Query query =
        Query.whereEquals(EVENT, MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED)
            .and(range(EVENT_TIME, startTime, endTime))
            .aggregate(DECISION_INSTANCES_AGG_NAME, VALUE, PRECISION_THRESHOLD);

    final var response = dao.searchWithAggregation(query);
    if (response.hasError()) {
      final String message = "Error while retrieving decision instance count between dates";
      LOGGER.error(message);
      throw new OperateRuntimeException(message);
    }

    return response.totalDocs();
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
        .setEventTime(timestamp)
        .setTenantId(tenantId);
  }

  private MetricEntity createDecisionsInstanceEvaluatedKey(
      String decisionInstanceKey, String tenantId, OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(decisionInstanceKey)
        .setEventTime(timestamp)
        .setTenantId(tenantId);
  }
}
