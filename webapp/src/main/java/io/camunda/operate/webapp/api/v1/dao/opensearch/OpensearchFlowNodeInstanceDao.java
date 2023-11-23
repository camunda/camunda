/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeInstanceDao extends OpensearchKeyFilteringDao<FlowNodeInstance, FlowNodeInstance> implements FlowNodeInstanceDao {

  private final FlowNodeInstanceTemplate flowNodeInstanceIndex;
  private final ProcessCache processCache;

  public OpensearchFlowNodeInstanceDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                       FlowNodeInstanceTemplate flowNodeInstanceIndex, RichOpenSearchClient richOpenSearchClient,
                                       ProcessCache processCache, OperateProperties operateProperties) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient, operateProperties);
    this.flowNodeInstanceIndex = flowNodeInstanceIndex;
    this.processCache = processCache;
  }

  @Override
  protected String getKeyFieldName() {
    return FlowNodeInstance.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return String.format("Error in reading flownode instance for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return String.format("No flownode instance found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return String.format("Found more than one flownode instances for key %s", key);
  }

  @Override
  protected String getIndexName() {
    return flowNodeInstanceIndex.getAlias();
  }

  @Override
  protected String getUniqueSortKey() {
    return FlowNodeInstance.KEY;
  }

  @Override
  protected Class<FlowNodeInstance> getModelClass() {
    return FlowNodeInstance.class;
  }

  @Override
  protected void buildFiltering(Query<FlowNodeInstance> query, SearchRequest.Builder request) {
    FlowNodeInstance filter = query.getFilter();

    if (filter != null) {
      List<org.opensearch.client.opensearch._types.query_dsl.Query> queryTerms = new LinkedList<>();

      if (filter.getKey() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.KEY, filter.getKey()));
      }
      if (filter.getProcessInstanceKey() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
      }
      if (filter.getProcessDefinitionKey() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()));
      }
      if (filter.getStartDate() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.START_DATE, filter.getStartDate()));
      }
      if (filter.getEndDate() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.END_DATE, filter.getEndDate()));
      }
      if (filter.getState() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.STATE, filter.getState()));
      }
      if (filter.getType() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.TYPE, filter.getType()));
      }
      if (filter.getFlowNodeId() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.FLOW_NODE_ID, filter.getFlowNodeId()));
      }
      if (filter.getIncident() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.INCIDENT, filter.getIncident()));
      }
      if (filter.getIncidentKey() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.INCIDENT_KEY, filter.getIncidentKey()));
      }
      if (filter.getTenantId() != null) {
        queryTerms.add(queryDSLWrapper.term(FlowNodeInstance.TENANT_ID, filter.getTenantId()));
      }

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected FlowNodeInstance transformHitToItem(Hit<FlowNodeInstance> hit) {
    FlowNodeInstance item = hit.source();
    if (item != null && item.getFlowNodeId() != null) {
      String flowNodeName = processCache.getFlowNodeNameOrDefaultValue(item.getProcessDefinitionKey(),
          item.getFlowNodeId(), null);
      item.setFlowNodeName(flowNodeName);
    }
    return item;
  }

  @Override
  protected List<FlowNodeInstance> searchByKey(Long key) {
    List<FlowNodeInstance> results = super.searchByKey(key);

    results.forEach(node -> {
      String flowNodeName = processCache.getFlowNodeNameOrDefaultValue(node.getProcessDefinitionKey(),
          node.getFlowNodeId(), null);
      node.setFlowNodeName(flowNodeName);
    });

    return results;
  }
}
