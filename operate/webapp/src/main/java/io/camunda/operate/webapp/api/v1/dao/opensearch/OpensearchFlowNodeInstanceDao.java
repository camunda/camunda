/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeInstanceDao
    extends OpensearchKeyFilteringDao<FlowNodeInstance, FlowNodeInstance>
    implements FlowNodeInstanceDao {

  private final FlowNodeInstanceTemplate flowNodeInstanceIndex;
  private final ProcessCache processCache;

  private final OperateDateTimeFormatter dateTimeFormatter;

  public OpensearchFlowNodeInstanceDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final @Qualifier("operateFlowNodeInstanceTemplate") FlowNodeInstanceTemplate
              flowNodeInstanceIndex,
      final ProcessCache processCache,
      final OperateDateTimeFormatter dateTimeFormatter) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.flowNodeInstanceIndex = flowNodeInstanceIndex;
    this.processCache = processCache;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  protected String getKeyFieldName() {
    return FlowNodeInstance.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(final Long key) {
    return String.format("Error in reading flownode instance for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(final Long key) {
    return String.format("No flownode instance found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(final Long key) {
    return String.format("Found more than one flownode instances for key %s", key);
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
  protected String getIndexName() {
    return flowNodeInstanceIndex.getAlias();
  }

  @Override
  protected void buildFiltering(
      final Query<FlowNodeInstance> query, final SearchRequest.Builder request) {
    final FlowNodeInstance filter = query.getFilter();

    if (filter != null) {
      final var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(FlowNodeInstance.KEY, filter.getKey()),
                  queryDSLWrapper.term(
                      FlowNodeInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
                  queryDSLWrapper.term(
                      FlowNodeInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
                  queryDSLWrapper.matchDateQuery(
                      FlowNodeInstance.START_DATE,
                      filter.getStartDate(),
                      dateTimeFormatter.getApiDateTimeFormatString()),
                  queryDSLWrapper.matchDateQuery(
                      FlowNodeInstance.END_DATE,
                      filter.getEndDate(),
                      dateTimeFormatter.getApiDateTimeFormatString()),
                  queryDSLWrapper.term(FlowNodeInstance.STATE, filter.getState()),
                  queryDSLWrapper.term(FlowNodeInstance.TYPE, filter.getType()),
                  queryDSLWrapper.term(FlowNodeInstance.FLOW_NODE_ID, filter.getFlowNodeId()),
                  queryDSLWrapper.term(FlowNodeInstance.INCIDENT, filter.getIncident()),
                  queryDSLWrapper.term(FlowNodeInstance.INCIDENT_KEY, filter.getIncidentKey()),
                  queryDSLWrapper.term(FlowNodeInstance.TENANT_ID, filter.getTenantId()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected FlowNodeInstance convertInternalToApiResult(final FlowNodeInstance internalResult) {
    if (internalResult != null) {
      if (StringUtils.isNotEmpty(internalResult.getStartDate())) {
        internalResult.setStartDate(
            dateTimeFormatter.convertGeneralToApiDateTime(internalResult.getStartDate()));
      }
      if (StringUtils.isNotEmpty(internalResult.getEndDate())) {
        internalResult.setEndDate(
            dateTimeFormatter.convertGeneralToApiDateTime(internalResult.getEndDate()));
      }

      if (internalResult.getFlowNodeId() != null) {
        final String flowNodeName =
            processCache.getFlowNodeNameOrDefaultValue(
                internalResult.getProcessDefinitionKey(), internalResult.getFlowNodeId(), null);
        internalResult.setFlowNodeName(flowNodeName);
      }
    }
    return internalResult;
  }
}
