/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeInstanceDao extends OpensearchKeyFilteringDao<FlowNodeInstance, FlowNodeInstance> implements FlowNodeInstanceDao {

  private final FlowNodeInstanceTemplate flowNodeInstanceIndex;
  private final ProcessCache processCache;

  private final OpensearchProperties opensearchProperties;

  public OpensearchFlowNodeInstanceDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                       RichOpenSearchClient richOpenSearchClient, FlowNodeInstanceTemplate flowNodeInstanceIndex,
                                       ProcessCache processCache, OperateProperties operateProperties) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.flowNodeInstanceIndex = flowNodeInstanceIndex;
    this.processCache = processCache;
    this.opensearchProperties = operateProperties.getOpensearch();
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
  protected Class<FlowNodeInstance> getInternalDocumentModelClass() {
    return FlowNodeInstance.class;
  }

  @Override
  protected void buildFiltering(Query<FlowNodeInstance> query, SearchRequest.Builder request) {
    FlowNodeInstance filter = query.getFilter();

    if (filter != null) {
      var queryTerms = Stream.of(
          queryDSLWrapper.term(FlowNodeInstance.KEY, filter.getKey()),
          queryDSLWrapper.term(FlowNodeInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
          queryDSLWrapper.term(FlowNodeInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
          queryDSLWrapper.matchDateQuery(FlowNodeInstance.START_DATE, filter.getStartDate(), opensearchProperties.getDateFormat()),
          queryDSLWrapper.matchDateQuery(FlowNodeInstance.END_DATE, filter.getEndDate(), opensearchProperties.getDateFormat()),
          queryDSLWrapper.term(FlowNodeInstance.STATE, filter.getState()),
          queryDSLWrapper.term(FlowNodeInstance.TYPE, filter.getType()),
          queryDSLWrapper.term(FlowNodeInstance.FLOW_NODE_ID, filter.getFlowNodeId()),
          queryDSLWrapper.term(FlowNodeInstance.INCIDENT, filter.getIncident()),
          queryDSLWrapper.term(FlowNodeInstance.INCIDENT_KEY, filter.getIncidentKey()),
          queryDSLWrapper.term(FlowNodeInstance.TENANT_ID, filter.getTenantId())
      ).filter(Objects::nonNull).collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected FlowNodeInstance convertInternalToApiResult(FlowNodeInstance internalResult) {
    if (internalResult != null && internalResult.getFlowNodeId() != null) {
      String flowNodeName = processCache.getFlowNodeNameOrDefaultValue(internalResult.getProcessDefinitionKey(),
          internalResult.getFlowNodeId(), null);
      internalResult.setFlowNodeName(flowNodeName);
    }
    return internalResult;
  }
}
