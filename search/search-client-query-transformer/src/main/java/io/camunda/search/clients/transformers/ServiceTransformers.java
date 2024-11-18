/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.entity.DecisionInstanceEntityTransformer;
import io.camunda.search.clients.transformers.entity.ProcessInstanceEntityTransformer;
import io.camunda.search.clients.transformers.filter.AuthorizationFilterTransformer;
import io.camunda.search.clients.transformers.filter.ComparableValueFilterTransformer;
import io.camunda.search.clients.transformers.filter.DateValueFilterTransformer;
import io.camunda.search.clients.transformers.filter.DecisionDefinitionFilterTransformer;
import io.camunda.search.clients.transformers.filter.DecisionInstanceFilterTransformer;
import io.camunda.search.clients.transformers.filter.DecisionRequirementsFilterTransformer;
import io.camunda.search.clients.transformers.filter.FilterTransformer;
import io.camunda.search.clients.transformers.filter.FlownodeInstanceFilterTransformer;
import io.camunda.search.clients.transformers.filter.FormFilterTransformer;
import io.camunda.search.clients.transformers.filter.IncidentFilterTransformer;
import io.camunda.search.clients.transformers.filter.ProcessDefinitionFilterTransformer;
import io.camunda.search.clients.transformers.filter.ProcessInstanceFilterTransformer;
import io.camunda.search.clients.transformers.filter.UserFilterTransformer;
import io.camunda.search.clients.transformers.filter.UserTaskFilterTransformer;
import io.camunda.search.clients.transformers.filter.VariableFilterTransformer;
import io.camunda.search.clients.transformers.filter.VariableValueFilterTransformer;
import io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer;
import io.camunda.search.clients.transformers.result.DecisionInstanceResultConfigTransformer;
import io.camunda.search.clients.transformers.result.DecisionRequirementsResultConfigTransformer;
import io.camunda.search.clients.transformers.result.ProcessInstanceResultConfigTransformer;
import io.camunda.search.clients.transformers.sort.DecisionInstanceFieldSortingTransformer;
import io.camunda.search.clients.transformers.sort.ProcessInstanceFieldSortingTransformer;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.ComparableValueFilter;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.FormFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.result.DecisionInstanceQueryResultConfig;
import io.camunda.search.result.DecisionRequirementsQueryResultConfig;
import io.camunda.search.result.ProcessInstanceQueryResultConfig;
import io.camunda.search.sort.DecisionInstanceSort;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SortOption;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import java.util.HashMap;
import java.util.Map;

public final class ServiceTransformers {

  private final Map<Class<?>, ServiceTransformer<?, ?>> transformers = new HashMap<>();

  private ServiceTransformers() {}

  public static ServiceTransformers newInstance(final boolean isCamundaExporterEnabled) {
    final var serviceTransformers = new ServiceTransformers();
    initializeTransformers(serviceTransformers, isCamundaExporterEnabled);
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

  public <T, R> ServiceTransformer<T, R> getTransformer(final Class<?> cls) {
    return (ServiceTransformer<T, R>) transformers.get(cls);
  }

  private void put(final Class<?> cls, final ServiceTransformer<?, ?> mapper) {
    transformers.put(cls, mapper);
  }

  public static void initializeTransformers(
      final ServiceTransformers mappers, final boolean isCamundaExporterEnabled) {
    // query -> request
    mappers.put(ProcessInstanceQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(UserTaskQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(VariableQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(DecisionDefinitionQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(DecisionRequirementsQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(DecisionInstanceQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(UserQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(FormQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(AuthorizationQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(IncidentQuery.class, new TypedSearchQueryTransformer<>(mappers));
    mappers.put(
        FlowNodeInstanceQuery.class,
        new TypedSearchQueryTransformer<FlowNodeInstanceFilter, FlowNodeInstanceSort>(mappers));
    mappers.put(ProcessDefinitionQuery.class, new TypedSearchQueryTransformer<>(mappers));

    // document entity -> domain entity
    mappers.put(DecisionInstanceEntity.class, new DecisionInstanceEntityTransformer());
    mappers.put(ProcessInstanceForListViewEntity.class, new ProcessInstanceEntityTransformer());

    // domain field sorting -> database field sorting
    mappers.put(DecisionInstanceSort.class, new DecisionInstanceFieldSortingTransformer());
    mappers.put(ProcessInstanceSort.class, new ProcessInstanceFieldSortingTransformer());

    // filters -> search query
    mappers.put(ProcessInstanceFilter.class, new ProcessInstanceFilterTransformer());
    mappers.put(
        UserTaskFilter.class, new UserTaskFilterTransformer(mappers, isCamundaExporterEnabled));
    mappers.put(VariableValueFilter.class, new VariableValueFilterTransformer());
    mappers.put(DateValueFilter.class, new DateValueFilterTransformer());
    mappers.put(VariableFilter.class, new VariableFilterTransformer());
    mappers.put(DecisionDefinitionFilter.class, new DecisionDefinitionFilterTransformer());
    mappers.put(DecisionRequirementsFilter.class, new DecisionRequirementsFilterTransformer());
    mappers.put(DecisionInstanceFilter.class, new DecisionInstanceFilterTransformer());
    mappers.put(UserFilter.class, new UserFilterTransformer());
    mappers.put(AuthorizationFilter.class, new AuthorizationFilterTransformer());
    mappers.put(ComparableValueFilter.class, new ComparableValueFilterTransformer());
    mappers.put(FlowNodeInstanceFilter.class, new FlownodeInstanceFilterTransformer());
    mappers.put(IncidentFilter.class, new IncidentFilterTransformer(mappers));
    mappers.put(FormFilter.class, new FormFilterTransformer(mappers));
    mappers.put(ProcessDefinitionFilter.class, new ProcessDefinitionFilterTransformer(mappers));

    // result config -> source config
    mappers.put(
        DecisionInstanceQueryResultConfig.class, new DecisionInstanceResultConfigTransformer());
    mappers.put(
        DecisionRequirementsQueryResultConfig.class,
        new DecisionRequirementsResultConfigTransformer());
    mappers.put(
        ProcessInstanceQueryResultConfig.class, new ProcessInstanceResultConfigTransformer());
  }
}
