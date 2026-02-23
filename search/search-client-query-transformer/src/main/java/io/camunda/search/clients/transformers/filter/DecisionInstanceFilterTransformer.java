/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.dateTimeOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.PARTITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_REQUIREMENTS_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_TYPE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_VERSION;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_FAILURE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_FAILURE_MESSAGE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.TENANT_ID;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.Operation;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class DecisionInstanceFilterTransformer
    extends IndexFilterTransformer<DecisionInstanceFilter> {

  public DecisionInstanceFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final DecisionInstanceFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(getKeysQuery(filter.decisionInstanceKeys())).ifPresent(queries::add);
    queries.addAll(getIdsQuery(filter.decisionInstanceIdOperations()));
    queries.addAll(getStatesQuery(filter.stateOperations()));
    queries.addAll(getEvaluationDateQuery(filter.evaluationDateOperations()));
    ofNullable(getEvaluationFailuresQuery(filter.evaluationFailures())).ifPresent(queries::add);
    ofNullable(getProcessDefinitionKeysQuery(filter.processDefinitionKeys()))
        .ifPresent(queries::add);
    ofNullable(getProcessInstanceKeysQuery(filter.processInstanceKeys())).ifPresent(queries::add);
    queries.addAll(getDecisionDefinitionKeysQuery(filter.decisionDefinitionKeyOperations()));
    queries.addAll(getFlowNodeInstanceKeysQuery(filter.flowNodeInstanceKeyOperations()));
    ofNullable(getDecisionDefinitionIdsQuery(filter.decisionDefinitionIds()))
        .ifPresent(queries::add);
    ofNullable(getDecisionDefinitionNamesQuery(filter.decisionDefinitionNames()))
        .ifPresent(queries::add);
    ofNullable(getDecisionDefinitionVersionsQuery(filter.decisionDefinitionVersions()))
        .ifPresent(queries::add);
    ofNullable(getDecisionDefinitionTypesQuery(filter.decisionTypes())).ifPresent(queries::add);
    queries.addAll(
        getRootDecisionDefinitionKeysQuery(filter.rootDecisionDefinitionKeyOperations()));
    queries.addAll(getDecisionRequirementsKeysQuery(filter.decisionRequirementsKeyOperations()));
    ofNullable(getTenantIdsQuery(filter.tenantIds())).ifPresent(queries::add);

    if (filter.partitionId() != null) {
      queries.add(term(PARTITION_ID, filter.partitionId()));
    }

    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(DECISION_ID, authorization.resourceIds());
  }

  private SearchQuery getKeysQuery(final List<Long> keys) {
    return longTerms(KEY, keys);
  }

  private List<SearchQuery> getIdsQuery(final List<Operation<String>> idOperations) {
    return stringOperations(ID, idOperations);
  }

  private List<SearchQuery> getStatesQuery(final List<Operation<String>> stateOperations) {
    return stringOperations(STATE, stateOperations);
  }

  private List<SearchQuery> getEvaluationDateQuery(
      final List<Operation<OffsetDateTime>> evaluationDateOperations) {
    return dateTimeOperations(EVALUATION_DATE, evaluationDateOperations);
  }

  private SearchQuery getEvaluationFailuresQuery(final List<String> evaluationFailures) {
    return or(
        stringTerms(EVALUATION_FAILURE_MESSAGE, evaluationFailures),
        stringTerms(EVALUATION_FAILURE, evaluationFailures));
  }

  private SearchQuery getProcessDefinitionKeysQuery(final List<Long> processDefinitionKeys) {
    return longTerms(PROCESS_DEFINITION_KEY, processDefinitionKeys);
  }

  private SearchQuery getProcessInstanceKeysQuery(final List<Long> processInstanceKeys) {
    return longTerms(PROCESS_INSTANCE_KEY, processInstanceKeys);
  }

  private List<SearchQuery> getDecisionDefinitionKeysQuery(
      final List<Operation<Long>> decisionDefinitionKeyOperations) {
    final var stringOperations =
        decisionDefinitionKeyOperations.stream()
            .map(
                op -> {
                  final var values = op.values().stream().map(String::valueOf).toList();
                  return new Operation<>(op.operator(), values);
                })
            .toList();
    return stringOperations(DECISION_DEFINITION_ID, stringOperations);
  }

  private List<SearchQuery> getFlowNodeInstanceKeysQuery(
      final List<Operation<Long>> flowNodeInstanceKeyOperations) {
    return longOperations(ELEMENT_INSTANCE_KEY, flowNodeInstanceKeyOperations);
  }

  private SearchQuery getDecisionDefinitionIdsQuery(final List<String> decisionDefinitionIds) {
    return stringTerms(DECISION_ID, decisionDefinitionIds);
  }

  private SearchQuery getDecisionDefinitionNamesQuery(final List<String> decisionDefinitionNames) {
    return stringTerms(DECISION_NAME, decisionDefinitionNames);
  }

  private SearchQuery getDecisionDefinitionVersionsQuery(
      final List<Integer> decisionDefinitionVersions) {
    return intTerms(DECISION_VERSION, decisionDefinitionVersions);
  }

  private SearchQuery getDecisionDefinitionTypesQuery(
      final List<DecisionDefinitionType> decisionTypes) {
    return stringTerms(
        DECISION_TYPE,
        decisionTypes != null ? decisionTypes.stream().map(Enum::name).toList() : null);
  }

  private List<SearchQuery> getRootDecisionDefinitionKeysQuery(
      final List<Operation<Long>> rootDecisionDefinitionKeyOperations) {
    final var stringOperations =
        rootDecisionDefinitionKeyOperations.stream()
            .map(
                op -> {
                  final var values = op.values().stream().map(String::valueOf).toList();
                  return new Operation<>(op.operator(), values);
                })
            .toList();
    return stringOperations(ROOT_DECISION_DEFINITION_ID, stringOperations);
  }

  private List<SearchQuery> getDecisionRequirementsKeysQuery(
      final List<Operation<Long>> decisionRequirementsKeyOperations) {
    return longOperations(DECISION_REQUIREMENTS_KEY, decisionRequirementsKeyOperations);
  }

  private SearchQuery getTenantIdsQuery(final List<String> tenantIds) {
    return stringTerms(TENANT_ID, tenantIds);
  }
}
