/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers;

import io.camunda.search.aggregation.AggregationBase;
import io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation;
import io.camunda.search.aggregation.GlobalJobStatisticsAggregation;
import io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation;
import io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation;
import io.camunda.search.aggregation.JobTypeStatisticsAggregation;
import io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation;
import io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation;
import io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation;
import io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation;
import io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation;
import io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation;
import io.camunda.search.aggregation.UsageMetricsAggregation;
import io.camunda.search.aggregation.UsageMetricsTUAggregation;
import io.camunda.search.aggregation.result.AggregationResultBase;
import io.camunda.search.aggregation.result.DecisionDefinitionLatestVersionAggregationResult;
import io.camunda.search.aggregation.result.GlobalJobStatisticsAggregationResult;
import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByDefinitionAggregationResult;
import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByErrorAggregationResult;
import io.camunda.search.aggregation.result.JobTypeStatisticsAggregationResult;
import io.camunda.search.aggregation.result.ProcessDefinitionFlowNodeStatisticsAggregationResult;
import io.camunda.search.aggregation.result.ProcessDefinitionInstanceStatisticsAggregationResult;
import io.camunda.search.aggregation.result.ProcessDefinitionInstanceVersionStatisticsAggregationResult;
import io.camunda.search.aggregation.result.ProcessDefinitionLatestVersionAggregationResult;
import io.camunda.search.aggregation.result.ProcessDefinitionMessageSubscriptionStatisticsAggregationResult;
import io.camunda.search.aggregation.result.ProcessInstanceFlowNodeStatisticsAggregationResult;
import io.camunda.search.aggregation.result.UsageMetricsAggregationResult;
import io.camunda.search.aggregation.result.UsageMetricsTUAggregationResult;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.aggregation.AggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.DecisionDefinitionLatestVersionAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.GlobalJobStatisticsAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.IncidentProcessInstanceStatisticsByErrorAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.JobTypeStatisticsAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.ProcessDefinitionFlowNodeStatisticsAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.ProcessDefinitionInstanceStatisticsAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.ProcessDefinitionLatestVersionAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.ProcessInstanceFlowNodeStatisticsAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.UsageMetricsAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.UsageMetricsTUAggregationTransformer;
import io.camunda.search.clients.transformers.aggregation.result.AggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.DecisionDefinitionLatestVersionAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.GlobalJobStatisticsAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.IncidentProcessInstanceStatisticsByDefinitionAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.IncidentProcessInstanceStatisticsByErrorAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.JobTypeStatisticsAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.ProcessDefinitionFlowNodeStatisticsAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.ProcessDefinitionInstanceStatisticsAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.ProcessDefinitionInstanceVersionStatisticsAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.ProcessDefinitionLatestVersionAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.ProcessDefinitionMessageSubscriptionStatisticsAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.ProcessInstanceFlowNodeStatisticsAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.UsageMetricsAggregationResultTransformer;
import io.camunda.search.clients.transformers.aggregation.result.UsageMetricsTUAggregationResultTransformer;
import io.camunda.search.clients.transformers.entity.AuditLogEntityTransformer;
import io.camunda.search.clients.transformers.entity.AuthorizationEntityTransformer;
import io.camunda.search.clients.transformers.entity.BatchOperationEntityTransformer;
import io.camunda.search.clients.transformers.entity.BatchOperationItemEntityTransformer;
import io.camunda.search.clients.transformers.entity.ClusterVariableEntityTransformer;
import io.camunda.search.clients.transformers.entity.CorrelatedMessageSubscriptionEntityTransformer;
import io.camunda.search.clients.transformers.entity.DecisionDefinitionEntityTransformer;
import io.camunda.search.clients.transformers.entity.DecisionInstanceEntityTransformer;
import io.camunda.search.clients.transformers.entity.DecisionRequirementsEntityTransformer;
import io.camunda.search.clients.transformers.entity.FlowNodeInstanceEntityTransformer;
import io.camunda.search.clients.transformers.entity.FormEntityTransformer;
import io.camunda.search.clients.transformers.entity.GroupEntityTransformer;
import io.camunda.search.clients.transformers.entity.GroupMemberEntityTransformer;
import io.camunda.search.clients.transformers.entity.IncidentEntityTransformer;
import io.camunda.search.clients.transformers.entity.JobEntityTransformer;
import io.camunda.search.clients.transformers.entity.MappingRuleEntityTransformer;
import io.camunda.search.clients.transformers.entity.MessageSubscriptionEntityTransformer;
import io.camunda.search.clients.transformers.entity.ProcessDefinitionEntityTransfomer;
import io.camunda.search.clients.transformers.entity.ProcessInstanceEntityTransformer;
import io.camunda.search.clients.transformers.entity.RoleEntityTransformer;
import io.camunda.search.clients.transformers.entity.RoleMemberEntityTransformer;
import io.camunda.search.clients.transformers.entity.SequenceFlowEntityTransformer;
import io.camunda.search.clients.transformers.entity.TenantEntityTransformer;
import io.camunda.search.clients.transformers.entity.TenantMemberEntityTransformer;
import io.camunda.search.clients.transformers.entity.UserEntityTransformer;
import io.camunda.search.clients.transformers.entity.UserTaskEntityTransformer;
import io.camunda.search.clients.transformers.entity.VariableEntityTransformer;
import io.camunda.search.clients.transformers.filter.AuditLogFilterTransformer;
import io.camunda.search.clients.transformers.filter.AuthorizationFilterTransformer;
import io.camunda.search.clients.transformers.filter.BatchOperationFilterTransformer;
import io.camunda.search.clients.transformers.filter.BatchOperationItemFilterTransformer;
import io.camunda.search.clients.transformers.filter.ClusterVariableFilterTransformer;
import io.camunda.search.clients.transformers.filter.CorrelatedMessageSubscriptionFilterTransformer;
import io.camunda.search.clients.transformers.filter.DateValueFilterTransformer;
import io.camunda.search.clients.transformers.filter.DecisionDefinitionFilterTransformer;
import io.camunda.search.clients.transformers.filter.DecisionInstanceFilterTransformer;
import io.camunda.search.clients.transformers.filter.DecisionRequirementsFilterTransformer;
import io.camunda.search.clients.transformers.filter.FilterTransformer;
import io.camunda.search.clients.transformers.filter.FlownodeInstanceFilterTransformer;
import io.camunda.search.clients.transformers.filter.FormFilterTransformer;
import io.camunda.search.clients.transformers.filter.GlobalJobStatisticsFilterTransformer;
import io.camunda.search.clients.transformers.filter.GroupFilterTransformer;
import io.camunda.search.clients.transformers.filter.GroupMemberFilterTransformer;
import io.camunda.search.clients.transformers.filter.IncidentFilterTransformer;
import io.camunda.search.clients.transformers.filter.JobFilterTransformer;
import io.camunda.search.clients.transformers.filter.JobTypeStatisticsFilterTransformer;
import io.camunda.search.clients.transformers.filter.MappingRuleFilterTransformer;
import io.camunda.search.clients.transformers.filter.MessageSubscriptionFilterTransformer;
import io.camunda.search.clients.transformers.filter.ProcessDefinitionFilterTransformer;
import io.camunda.search.clients.transformers.filter.ProcessDefinitionInstanceVersionStatisticsFilterTransformer;
import io.camunda.search.clients.transformers.filter.ProcessDefinitionStatisticsFilterTransformer;
import io.camunda.search.clients.transformers.filter.ProcessInstanceFilterTransformer;
import io.camunda.search.clients.transformers.filter.ProcessInstanceStatisticsFilterTransformer;
import io.camunda.search.clients.transformers.filter.RoleFilterTransformer;
import io.camunda.search.clients.transformers.filter.RoleMemberFilterTransformer;
import io.camunda.search.clients.transformers.filter.SequenceFlowFilterTransformer;
import io.camunda.search.clients.transformers.filter.TenantFilterTransformer;
import io.camunda.search.clients.transformers.filter.TenantMemberFilterTransformer;
import io.camunda.search.clients.transformers.filter.UsageMetricsFilterTransformer;
import io.camunda.search.clients.transformers.filter.UsageMetricsTUFilterTransformer;
import io.camunda.search.clients.transformers.filter.UserFilterTransformer;
import io.camunda.search.clients.transformers.filter.UserTaskFilterTransformer;
import io.camunda.search.clients.transformers.filter.VariableFilterTransformer;
import io.camunda.search.clients.transformers.filter.VariableValueFilterTransformer;
import io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer;
import io.camunda.search.clients.transformers.result.DecisionInstanceResultConfigTransformer;
import io.camunda.search.clients.transformers.result.DecisionRequirementsResultConfigTransformer;
import io.camunda.search.clients.transformers.result.ProcessInstanceResultConfigTransformer;
import io.camunda.search.clients.transformers.sort.AuditLogSortTransformer;
import io.camunda.search.clients.transformers.sort.AuthorizationFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.BatchOperationFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.BatchOperationItemFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.ClusterVariableFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.CorrelatedMessageSubscriptionFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.DecisionDefinitionFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.DecisionInstanceFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.DecisionRequirementsFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.FieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.FlowNodeInstanceFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.FormFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.GroupFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.GroupMemberFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.IncidentFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.JobFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.MappingRuleFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.MessageSubscriptionFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.ProcessDefinitionFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.ProcessInstanceFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.RoleFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.RoleMemberFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.TenantFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.TenantMemberFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.UsageMetricsFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.UserFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.UserTaskFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.VariableFieldSortingTransformer;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.BatchOperationItemFilter;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.CorrelatedMessageSubscriptionFilter;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.FormFilter;
import io.camunda.search.filter.GlobalJobStatisticsFilter;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.filter.GroupMemberFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.filter.JobTypeStatisticsFilter;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.filter.RoleMemberFilter;
import io.camunda.search.filter.SequenceFlowFilter;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.filter.TenantMemberFilter;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.filter.UsageMetricsTUFilter;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UsageMetricsTUQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.result.DecisionInstanceQueryResultConfig;
import io.camunda.search.result.DecisionRequirementsQueryResultConfig;
import io.camunda.search.result.ProcessInstanceQueryResultConfig;
import io.camunda.search.sort.AuditLogSort;
import io.camunda.search.sort.AuthorizationSort;
import io.camunda.search.sort.BatchOperationItemSort;
import io.camunda.search.sort.BatchOperationSort;
import io.camunda.search.sort.ClusterVariableSort;
import io.camunda.search.sort.CorrelatedMessageSubscriptionSort;
import io.camunda.search.sort.DecisionDefinitionSort;
import io.camunda.search.sort.DecisionInstanceSort;
import io.camunda.search.sort.DecisionRequirementsSort;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.search.sort.FormSort;
import io.camunda.search.sort.GroupMemberSort;
import io.camunda.search.sort.GroupSort;
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByDefinitionSort;
import io.camunda.search.sort.IncidentSort;
import io.camunda.search.sort.JobSort;
import io.camunda.search.sort.MappingRuleSort;
import io.camunda.search.sort.MessageSubscriptionSort;
import io.camunda.search.sort.ProcessDefinitionSort;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.RoleMemberSort;
import io.camunda.search.sort.RoleSort;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.TenantMemberSort;
import io.camunda.search.sort.TenantSort;
import io.camunda.search.sort.UsageMetricsSort;
import io.camunda.search.sort.UserSort;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.search.sort.VariableSort;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.index.MappingRuleIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.index.UserIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.CorrelatedMessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobMetricsBatchTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.form.FormEntity;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity;
import io.camunda.webapps.schema.entities.usermanagement.GroupEntity;
import io.camunda.webapps.schema.entities.usermanagement.GroupMemberEntity;
import io.camunda.webapps.schema.entities.usermanagement.MappingRuleEntity;
import io.camunda.webapps.schema.entities.usermanagement.RoleEntity;
import io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity;
import io.camunda.webapps.schema.entities.usermanagement.TenantEntity;
import io.camunda.webapps.schema.entities.usermanagement.TenantMemberEntity;
import io.camunda.webapps.schema.entities.usermanagement.UserEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ServiceTransformers {

  private final Map<Class<?>, ServiceTransformer<?, ?>> transformers = new HashMap<>();

  private ServiceTransformers() {}

  public static ServiceTransformers newInstance(final IndexDescriptors indexDescriptors) {
    final var serviceTransformers = new ServiceTransformers();
    initializeTransformers(serviceTransformers, indexDescriptors);
    return serviceTransformers;
  }

  public <F extends FilterBase, S extends SortOption>
      TypedSearchQueryTransformer<F, S> getTypedSearchQueryTransformer(final Class<?> cls) {
    final ServiceTransformer<TypedSearchQuery<F, S>, SearchQueryRequest> transformer =
        getTransformer(cls);
    return (TypedSearchQueryTransformer<F, S>) transformer;
  }

  public <F extends FilterBase> FilterTransformer<F> getFilterTransformer(final Class<?> cls) {
    final ServiceTransformer<F, SearchQuery> transformer = getTransformer(cls);
    return (FilterTransformer<F>) transformer;
  }

  public <A extends AggregationResultBase>
      AggregationResultTransformer<A> getSearchAggregationResultTransformer(final Class<A> cls) {
    final ServiceTransformer<Map<String, AggregationResult>, A> transformer = getTransformer(cls);
    return (AggregationResultTransformer<A>) transformer;
  }

  public <A extends AggregationBase> AggregationTransformer<A> getAggregationTransformer(
      final Class<?> cls) {
    final ServiceTransformer<Tuple<A, ServiceTransformers>, List<SearchAggregator>> transformer =
        getTransformer(cls);
    return (AggregationTransformer<A>) transformer;
  }

  public FieldSortingTransformer getFieldSortingTransformer(final Class<?> cls) {
    final ServiceTransformer<String, String> fieldSortingTransformer = getTransformer(cls);
    return (FieldSortingTransformer) fieldSortingTransformer;
  }

  public <T, R> ServiceTransformer<T, R> getTransformer(final Class<?> cls) {
    if (!transformers.containsKey(cls)) {
      throw new IllegalArgumentException("No transformer found for class " + cls);
    }
    return (ServiceTransformer<T, R>) transformers.get(cls);
  }

  private void put(final Class<?> cls, final ServiceTransformer<?, ?> mapper) {
    transformers.put(cls, mapper);
  }

  public static void initializeTransformers(
      final ServiceTransformers mappers, final IndexDescriptors indexDescriptors) {

    final TypedSearchQueryTransformer<?, ?> searchQueryTransformer =
        new TypedSearchQueryTransformer<>(mappers);
    // query -> request
    Stream.of(
            AuthorizationQuery.class,
            BatchOperationQuery.class,
            BatchOperationItemQuery.class,
            CorrelatedMessageSubscriptionQuery.class,
            DecisionDefinitionQuery.class,
            DecisionInstanceQuery.class,
            DecisionRequirementsQuery.class,
            FlowNodeInstanceQuery.class,
            FormQuery.class,
            GroupQuery.class,
            GroupMemberQuery.class,
            IncidentQuery.class,
            JobQuery.class,
            MappingRuleQuery.class,
            MessageSubscriptionQuery.class,
            ProcessDefinitionMessageSubscriptionStatisticsQuery.class,
            ProcessDefinitionQuery.class,
            ProcessDefinitionFlowNodeStatisticsQuery.class,
            ProcessDefinitionInstanceStatisticsQuery.class,
            ProcessDefinitionInstanceVersionStatisticsQuery.class,
            ProcessInstanceQuery.class,
            ProcessInstanceFlowNodeStatisticsQuery.class,
            RoleQuery.class,
            RoleMemberQuery.class,
            SequenceFlowQuery.class,
            TenantQuery.class,
            TenantMemberQuery.class,
            UsageMetricsQuery.class,
            UsageMetricsTUQuery.class,
            UserTaskQuery.class,
            UserQuery.class,
            VariableQuery.class,
            ClusterVariableQuery.class,
            AuditLogQuery.class,
            IncidentProcessInstanceStatisticsByErrorQuery.class,
            IncidentProcessInstanceStatisticsByDefinitionQuery.class,
            GlobalJobStatisticsQuery.class,
            JobTypeStatisticsQuery.class)
        .forEach(cls -> mappers.put(cls, searchQueryTransformer));

    // document entity -> domain entity
    mappers.put(AuthorizationEntity.class, new AuthorizationEntityTransformer());
    mappers.put(BatchOperationEntity.class, new BatchOperationEntityTransformer());
    mappers.put(
        CorrelatedMessageSubscriptionEntity.class,
        new CorrelatedMessageSubscriptionEntityTransformer());
    mappers.put(DecisionDefinitionEntity.class, new DecisionDefinitionEntityTransformer());
    mappers.put(DecisionRequirementsEntity.class, new DecisionRequirementsEntityTransformer());
    mappers.put(DecisionInstanceEntity.class, new DecisionInstanceEntityTransformer());
    mappers.put(MessageSubscriptionEntity.class, new MessageSubscriptionEntityTransformer());
    mappers.put(FlowNodeInstanceEntity.class, new FlowNodeInstanceEntityTransformer());
    mappers.put(FormEntity.class, new FormEntityTransformer());
    mappers.put(GroupEntity.class, new GroupEntityTransformer());
    mappers.put(GroupMemberEntity.class, new GroupMemberEntityTransformer());
    mappers.put(IncidentEntity.class, new IncidentEntityTransformer());
    mappers.put(JobEntity.class, new JobEntityTransformer());
    mappers.put(MappingRuleEntity.class, new MappingRuleEntityTransformer());
    mappers.put(OperationEntity.class, new BatchOperationItemEntityTransformer());
    mappers.put(ProcessEntity.class, new ProcessDefinitionEntityTransfomer());
    mappers.put(ProcessInstanceForListViewEntity.class, new ProcessInstanceEntityTransformer());
    mappers.put(RoleEntity.class, new RoleEntityTransformer());
    mappers.put(RoleMemberEntity.class, new RoleMemberEntityTransformer());
    mappers.put(SequenceFlowEntity.class, new SequenceFlowEntityTransformer());
    mappers.put(TaskEntity.class, new UserTaskEntityTransformer());
    mappers.put(VariableEntity.class, new VariableEntityTransformer());
    mappers.put(ClusterVariableEntity.class, new ClusterVariableEntityTransformer());
    mappers.put(TenantEntity.class, new TenantEntityTransformer());
    mappers.put(TenantMemberEntity.class, new TenantMemberEntityTransformer());
    mappers.put(UserEntity.class, new UserEntityTransformer());
    mappers.put(AuditLogEntity.class, new AuditLogEntityTransformer());

    // domain field sorting -> database field sorting
    mappers.put(AuthorizationSort.class, new AuthorizationFieldSortingTransformer());
    mappers.put(BatchOperationSort.class, new BatchOperationFieldSortingTransformer());
    mappers.put(BatchOperationItemSort.class, new BatchOperationItemFieldSortingTransformer());
    mappers.put(
        CorrelatedMessageSubscriptionSort.class,
        new CorrelatedMessageSubscriptionFieldSortingTransformer());
    mappers.put(DecisionDefinitionSort.class, new DecisionDefinitionFieldSortingTransformer());
    mappers.put(DecisionRequirementsSort.class, new DecisionRequirementsFieldSortingTransformer());
    mappers.put(DecisionInstanceSort.class, new DecisionInstanceFieldSortingTransformer());
    mappers.put(FlowNodeInstanceSort.class, new FlowNodeInstanceFieldSortingTransformer());
    mappers.put(FormSort.class, new FormFieldSortingTransformer());
    mappers.put(GroupSort.class, new GroupFieldSortingTransformer());
    mappers.put(GroupMemberSort.class, new GroupMemberFieldSortingTransformer());
    mappers.put(IncidentSort.class, new IncidentFieldSortingTransformer());
    mappers.put(JobSort.class, new JobFieldSortingTransformer());
    mappers.put(MappingRuleSort.class, new MappingRuleFieldSortingTransformer());
    mappers.put(MessageSubscriptionSort.class, new MessageSubscriptionFieldSortingTransformer());
    mappers.put(ProcessDefinitionSort.class, new ProcessDefinitionFieldSortingTransformer());
    mappers.put(ProcessInstanceSort.class, new ProcessInstanceFieldSortingTransformer());
    mappers.put(RoleSort.class, new RoleFieldSortingTransformer());
    mappers.put(RoleMemberSort.class, new RoleMemberFieldSortingTransformer());
    mappers.put(TenantSort.class, new TenantFieldSortingTransformer());
    mappers.put(TenantMemberSort.class, new TenantMemberFieldSortingTransformer());
    mappers.put(UserTaskSort.class, new UserTaskFieldSortingTransformer());
    mappers.put(VariableSort.class, new VariableFieldSortingTransformer());
    mappers.put(ClusterVariableSort.class, new ClusterVariableFieldSortingTransformer());
    mappers.put(TenantSort.class, new TenantFieldSortingTransformer());
    mappers.put(UsageMetricsSort.class, new UsageMetricsFieldSortingTransformer());
    mappers.put(UserSort.class, new UserFieldSortingTransformer());
    mappers.put(AuditLogSort.class, new AuditLogSortTransformer());
    mappers.put(
        IncidentProcessInstanceStatisticsByDefinitionSort.class,
        new IncidentFieldSortingTransformer());

    // filters -> search query
    mappers.put(
        ProcessInstanceFilter.class,
        new ProcessInstanceFilterTransformer(
            mappers, indexDescriptors.get(ListViewTemplate.class)));
    mappers.put(
        UserTaskFilter.class,
        new UserTaskFilterTransformer(mappers, indexDescriptors.get(TaskTemplate.class)));
    mappers.put(VariableValueFilter.class, new VariableValueFilterTransformer());
    mappers.put(
        ClusterVariableFilter.class,
        new ClusterVariableFilterTransformer(indexDescriptors.get(ClusterVariableIndex.class)));
    mappers.put(DateValueFilter.class, new DateValueFilterTransformer());
    mappers.put(
        VariableFilter.class,
        new VariableFilterTransformer(indexDescriptors.get(VariableTemplate.class)));
    mappers.put(
        DecisionDefinitionFilter.class,
        new DecisionDefinitionFilterTransformer(indexDescriptors.get(DecisionIndex.class)));
    mappers.put(
        DecisionRequirementsFilter.class,
        new DecisionRequirementsFilterTransformer(
            indexDescriptors.get(DecisionRequirementsIndex.class)));
    mappers.put(
        DecisionInstanceFilter.class,
        new DecisionInstanceFilterTransformer(
            indexDescriptors.get(DecisionInstanceTemplate.class)));
    mappers.put(UserFilter.class, new UserFilterTransformer(indexDescriptors.get(UserIndex.class)));
    mappers.put(
        AuditLogFilter.class,
        new AuditLogFilterTransformer(indexDescriptors.get(AuditLogTemplate.class)));
    mappers.put(
        AuthorizationFilter.class,
        new AuthorizationFilterTransformer(indexDescriptors.get(AuthorizationIndex.class)));
    mappers.put(
        MappingRuleFilter.class,
        new MappingRuleFilterTransformer(indexDescriptors.get(MappingRuleIndex.class)));
    mappers.put(
        FlowNodeInstanceFilter.class,
        new FlownodeInstanceFilterTransformer(
            indexDescriptors.get(FlowNodeInstanceTemplate.class)));
    mappers.put(RoleFilter.class, new RoleFilterTransformer(indexDescriptors.get(RoleIndex.class)));
    mappers.put(
        RoleMemberFilter.class,
        new RoleMemberFilterTransformer(indexDescriptors.get(RoleIndex.class)));
    mappers.put(
        GroupFilter.class, new GroupFilterTransformer(indexDescriptors.get(GroupIndex.class)));
    mappers.put(
        GroupMemberFilter.class,
        new GroupMemberFilterTransformer(indexDescriptors.get(GroupIndex.class)));
    mappers.put(
        IncidentFilter.class,
        new IncidentFilterTransformer(indexDescriptors.get(IncidentTemplate.class)));
    mappers.put(
        io.camunda.search.filter.IncidentProcessInstanceStatisticsByDefinitionFilter.class,
        new io.camunda.search.clients.transformers.filter
            .IncidentProcessInstanceStatisticsByDefinitionFilterTransformer(
            indexDescriptors.get(IncidentTemplate.class)));
    mappers.put(FormFilter.class, new FormFilterTransformer(indexDescriptors.get(FormIndex.class)));
    mappers.put(
        ProcessDefinitionFilter.class,
        new ProcessDefinitionFilterTransformer(indexDescriptors.get(ProcessIndex.class)));
    mappers.put(RoleFilter.class, new RoleFilterTransformer(indexDescriptors.get(RoleIndex.class)));
    mappers.put(
        TenantFilter.class, new TenantFilterTransformer(indexDescriptors.get(TenantIndex.class)));
    mappers.put(
        TenantMemberFilter.class,
        new TenantMemberFilterTransformer(indexDescriptors.get(TenantIndex.class)));
    mappers.put(
        UsageMetricsFilter.class,
        new UsageMetricsFilterTransformer(indexDescriptors.get(UsageMetricTemplate.class)));
    mappers.put(
        UsageMetricsTUFilter.class,
        new UsageMetricsTUFilterTransformer(indexDescriptors.get(UsageMetricTUTemplate.class)));
    mappers.put(
        GlobalJobStatisticsFilter.class,
        new GlobalJobStatisticsFilterTransformer(
            indexDescriptors.get(JobMetricsBatchTemplate.class)));
    mappers.put(
        JobTypeStatisticsFilter.class,
        new JobTypeStatisticsFilterTransformer(
            indexDescriptors.get(JobMetricsBatchTemplate.class)));
    mappers.put(
        ProcessDefinitionStatisticsFilter.class,
        new ProcessDefinitionStatisticsFilterTransformer(
            mappers, indexDescriptors.get(ListViewTemplate.class)));
    mappers.put(
        ProcessInstanceStatisticsFilter.class,
        new ProcessInstanceStatisticsFilterTransformer(
            indexDescriptors.get(ListViewTemplate.class)));
    mappers.put(
        BatchOperationFilter.class,
        new BatchOperationFilterTransformer(indexDescriptors.get(BatchOperationTemplate.class)));
    mappers.put(
        BatchOperationItemFilter.class,
        new BatchOperationItemFilterTransformer(indexDescriptors.get(OperationTemplate.class)));
    mappers.put(
        SequenceFlowFilter.class,
        new SequenceFlowFilterTransformer(indexDescriptors.get(SequenceFlowTemplate.class)));
    mappers.put(JobFilter.class, new JobFilterTransformer(indexDescriptors.get(JobTemplate.class)));
    mappers.put(
        MessageSubscriptionFilter.class,
        new MessageSubscriptionFilterTransformer(
            indexDescriptors.get(MessageSubscriptionTemplate.class)));
    mappers.put(
        CorrelatedMessageSubscriptionFilter.class,
        new CorrelatedMessageSubscriptionFilterTransformer(
            indexDescriptors.get(CorrelatedMessageSubscriptionTemplate.class)));
    mappers.put(
        ProcessDefinitionInstanceVersionStatisticsFilter.class,
        new ProcessDefinitionInstanceVersionStatisticsFilterTransformer(
            indexDescriptors.get(ListViewTemplate.class)));
    // result config -> source config
    mappers.put(
        DecisionInstanceQueryResultConfig.class, new DecisionInstanceResultConfigTransformer());
    mappers.put(
        DecisionRequirementsQueryResultConfig.class,
        new DecisionRequirementsResultConfigTransformer());
    mappers.put(
        ProcessInstanceQueryResultConfig.class, new ProcessInstanceResultConfigTransformer());

    // aggregation
    mappers.put(
        ProcessDefinitionMessageSubscriptionStatisticsAggregation.class,
        new ProcessDefinitionMessageSubscriptionStatisticsAggregationTransformer());
    mappers.put(
        ProcessDefinitionFlowNodeStatisticsAggregation.class,
        new ProcessDefinitionFlowNodeStatisticsAggregationTransformer());
    mappers.put(
        ProcessInstanceFlowNodeStatisticsAggregation.class,
        new ProcessInstanceFlowNodeStatisticsAggregationTransformer());
    mappers.put(
        ProcessDefinitionLatestVersionAggregation.class,
        new ProcessDefinitionLatestVersionAggregationTransformer());
    mappers.put(UsageMetricsAggregation.class, new UsageMetricsAggregationTransformer());
    mappers.put(UsageMetricsTUAggregation.class, new UsageMetricsTUAggregationTransformer());
    mappers.put(
        ProcessDefinitionInstanceStatisticsAggregation.class,
        new ProcessDefinitionInstanceStatisticsAggregationTransformer());
    mappers.put(
        ProcessDefinitionInstanceVersionStatisticsAggregation.class,
        new ProcessDefinitionInstanceVersionStatisticsAggregationTransformer());
    mappers.put(
        IncidentProcessInstanceStatisticsByErrorAggregation.class,
        new IncidentProcessInstanceStatisticsByErrorAggregationTransformer());
    mappers.put(
        IncidentProcessInstanceStatisticsByDefinitionAggregation.class,
        new IncidentProcessInstanceStatisticsByDefinitionAggregationTransformer());
    mappers.put(
        DecisionDefinitionLatestVersionAggregation.class,
        new DecisionDefinitionLatestVersionAggregationTransformer());
    mappers.put(
        GlobalJobStatisticsAggregation.class, new GlobalJobStatisticsAggregationTransformer());
    mappers.put(JobTypeStatisticsAggregation.class, new JobTypeStatisticsAggregationTransformer());

    // aggregation result
    mappers.put(
        ProcessDefinitionMessageSubscriptionStatisticsAggregationResult.class,
        new ProcessDefinitionMessageSubscriptionStatisticsAggregationResultTransformer());
    mappers.put(
        ProcessDefinitionFlowNodeStatisticsAggregationResult.class,
        new ProcessDefinitionFlowNodeStatisticsAggregationResultTransformer());
    mappers.put(
        ProcessInstanceFlowNodeStatisticsAggregationResult.class,
        new ProcessInstanceFlowNodeStatisticsAggregationResultTransformer());
    mappers.put(
        ProcessDefinitionLatestVersionAggregationResult.class,
        new ProcessDefinitionLatestVersionAggregationResultTransformer());
    mappers.put(
        UsageMetricsAggregationResult.class, new UsageMetricsAggregationResultTransformer());
    mappers.put(
        UsageMetricsTUAggregationResult.class, new UsageMetricsTUAggregationResultTransformer());
    mappers.put(
        ProcessDefinitionInstanceStatisticsAggregationResult.class,
        new ProcessDefinitionInstanceStatisticsAggregationResultTransformer());
    mappers.put(
        ProcessDefinitionInstanceVersionStatisticsAggregationResult.class,
        new ProcessDefinitionInstanceVersionStatisticsAggregationResultTransformer());
    mappers.put(
        IncidentProcessInstanceStatisticsByErrorAggregationResult.class,
        new IncidentProcessInstanceStatisticsByErrorAggregationResultTransformer());
    mappers.put(
        IncidentProcessInstanceStatisticsByDefinitionAggregationResult.class,
        new IncidentProcessInstanceStatisticsByDefinitionAggregationResultTransformer());
    mappers.put(
        DecisionDefinitionLatestVersionAggregationResult.class,
        new DecisionDefinitionLatestVersionAggregationResultTransformer());
    mappers.put(
        GlobalJobStatisticsAggregationResult.class,
        new GlobalJobStatisticsAggregationResultTransformer());
    mappers.put(
        JobTypeStatisticsAggregationResult.class,
        new JobTypeStatisticsAggregationResultTransformer());
  }
}
