/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import static io.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.FILTERED_INSTANCE_COUNT_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.FILTERED_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.MISSING_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.NESTED_VARIABLE_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.VARIABLES_INSTANCE_COUNT_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.VARIABLE_HISTOGRAM_AGGREGATION;
import static io.camunda.optimize.service.db.es.util.ProcessVariableHelperES.createFilterForUndefinedOrNullQueryBuilder;
import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;
import static java.util.stream.Collectors.toSet;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketAggregateBase;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketBase;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.VariableDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.VariableDistributedByValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.report.context.VariableAggregationContextES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByVariableInterpreterES
    extends AbstractProcessDistributedByInterpreterES {

  private static final String PARENT_FILTER_AGGREGATION = "matchAllFilter";

  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final DateAggregationServiceES dateAggregationService;
  private final VariableAggregationServiceES variableAggregationService;

  public ProcessDistributedByVariableInterpreterES(
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final DateAggregationServiceES dateAggregationService,
      final VariableAggregationServiceES variableAggregationService) {
    this.viewInterpreter = viewInterpreter;
    this.dateAggregationService = dateAggregationService;
    this.variableAggregationService = variableAggregationService;
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_VARIABLE);
  }

  @Override
  public boolean isKeyOfNumericType(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return VariableType.getNumericTypes().contains(getVariableType(context));
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQuery baseQueryBuilder) {
    final Aggregation.Builder.ContainerBuilder rnBuilder =
        new Aggregation.Builder().reverseNested(r -> r);

    getViewInterpreter()
        .createAggregations(context)
        .forEach((k, v) -> rnBuilder.aggregations(k, v.build()));
    final VariableAggregationContextES varAggContext =
        VariableAggregationContextES.builder()
            .variableName(getVariableName(context))
            .variableType(getVariableType(context))
            .variablePath(VARIABLES)
            .nestedVariableNameField(getNestedVariableNameField())
            .nestedVariableValueFieldLabel(
                getNestedVariableValueFieldLabel(getVariableType(context)))
            .indexNames(getProcessInstanceIndexAliasNames(context.getReportData()))
            .timezone(context.getTimezone())
            .customBucketDto(
                context.getReportData().getConfiguration().getDistributeByCustomBucket())
            .dateUnit(getDistributeByDateUnit(context))
            .baseQueryForMinMaxStats(baseQueryBuilder)
            .subAggregations(Map.of(VARIABLES_INSTANCE_COUNT_AGGREGATION, rnBuilder))
            .combinedRangeMinMaxStats(context.getCombinedRangeMinMaxStats().orElse(null))
            .filterContext(context.getFilterContext())
            .build();

    final Optional<Map<String, Aggregation.Builder.ContainerBuilder>> variableSubAggregation =
        variableAggregationService.createVariableSubAggregation(varAggContext);

    if (variableSubAggregation.isEmpty()) {
      // if the report contains no instances and is distributed by date variable, this agg will not
      // be present
      // as it is based on instance data
      return getViewInterpreter().createAggregations(context);
    }

    // Add a parent match all filter agg to be able to retrieve all undefined/null variables as a
    // sibling aggregation
    // to the nested existing variable filter

    final Map<String, Aggregation.Builder.ContainerBuilder> undefinedOrNullVariableAggregation =
        createUndefinedOrNullVariableAggregation(context);
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().filter(f -> f.matchAll(m -> m));
    undefinedOrNullVariableAggregation.forEach((k, v) -> builder.aggregations(k, v.build()));
    builder.aggregations(
        NESTED_VARIABLE_AGGREGATION,
        Aggregation.of(
            a ->
                a.nested(n -> n.path(VARIABLES))
                    .aggregations(
                        FILTERED_VARIABLES_AGGREGATION,
                        Aggregation.of(
                            aa ->
                                aa.filter(
                                        f ->
                                            f.bool(
                                                b ->
                                                    b.must(
                                                            m ->
                                                                m.term(
                                                                    t ->
                                                                        t.field(
                                                                                getNestedVariableNameField())
                                                                            .value(
                                                                                getVariableName(
                                                                                    context))))
                                                        .must(
                                                            m ->
                                                                m.term(
                                                                    t ->
                                                                        t.field(
                                                                                getNestedVariableTypeField())
                                                                            .value(
                                                                                getVariableType(
                                                                                        context)
                                                                                    .getId())))
                                                        .must(
                                                            m ->
                                                                m.exists(
                                                                    e ->
                                                                        e.field(
                                                                            getNestedVariableValueFieldLabel(
                                                                                VariableType
                                                                                    .STRING))))))
                                    .aggregations(
                                        variableSubAggregation.get().entrySet().stream()
                                            .collect(
                                                Collectors.toMap(
                                                    Map.Entry::getKey, e -> e.getValue().build())))
                                    .aggregations(
                                        FILTERED_INSTANCE_COUNT_AGGREGATION,
                                        Aggregation.of(aaa -> aaa.reverseNested(r -> r)))))));
    return Map.of(PARENT_FILTER_AGGREGATION, builder);
  }

  private Map<String, Aggregation.Builder.ContainerBuilder>
      createUndefinedOrNullVariableAggregation(
          final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .filter(
                f ->
                    f.bool(
                        createFilterForUndefinedOrNullQueryBuilder(
                                getVariableName(context), getVariableType(context))
                            .build()));
    getViewInterpreter()
        .createAggregations(context)
        .forEach((k, v) -> builder.aggregations(k, v.build()));
    return Map.of(MISSING_VARIABLES_AGGREGATION, builder);
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Aggregate parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION);
    if (parentFilterAgg == null) {
      // could not create aggregations, e.g. because baseline is invalid
      return Collections.emptyList();
    }

    final NestedAggregate nested =
        parentFilterAgg.filter().aggregations().get(NESTED_VARIABLE_AGGREGATION).nested();
    final FilterAggregate filteredVariables =
        nested.aggregations().get(FILTERED_VARIABLES_AGGREGATION).filter();
    Aggregate filteredParentAgg = filteredVariables.aggregations().get(FILTER_LIMITED_AGGREGATION);
    if (filteredParentAgg == null) {
      filteredParentAgg = nested.aggregations().get(FILTERED_VARIABLES_AGGREGATION);
    }
    Aggregate variableTerms = filteredParentAgg.filter().aggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredParentAgg.filter().aggregations().get(VARIABLE_HISTOGRAM_AGGREGATION);
    }

    final Map<String, Map<String, Aggregate>> bucketAggregations =
        variableAggregationService.retrieveResultBucketMap(
            filteredParentAgg.filter(),
            variableTerms,
            getVariableType(context),
            context.getTimezone());

    final List<CompositeCommandResult.DistributedByResult> distributedByResults = new ArrayList<>();

    for (final Map.Entry<String, Map<String, Aggregate>> keyToAggregationEntry :
        bucketAggregations.entrySet()) {
      final CompositeCommandResult.ViewResult viewResult =
          getViewInterpreter()
              .retrieveResult(
                  response,
                  variableAggregationService.retrieveSubAggregationFromBucketMapEntry(
                      keyToAggregationEntry),
                  context);
      distributedByResults.add(
          createDistributedByResult(keyToAggregationEntry.getKey(), null, viewResult));
    }

    addMissingVariableBuckets(distributedByResults, response, aggregations, context);
    addEmptyMissingDistributedByResults(distributedByResults, context);

    return distributedByResults;
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Map<String, Aggregate> aggregations) {
    final Aggregate parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION);
    if (parentFilterAgg == null) {
      // there are no distributedBy keys
      context.setAllDistributedByKeysAndLabels(new HashMap<>());
      return;
    }

    Set<String> allDistributedByKeys = new HashSet<>();
    final VariableType type = getVariableType(context);
    if (!VariableType.getNumericTypes().contains(type)) {
      // missing distrBy keys evaluation only required if it's not a range (number var) aggregation
      final NestedAggregate nestedAgg =
          parentFilterAgg.filter().aggregations().get(NESTED_VARIABLE_AGGREGATION).nested();
      final FilterAggregate filteredVarAgg =
          nestedAgg.aggregations().get(FILTERED_VARIABLES_AGGREGATION).filter();
      if (VariableType.DATE.equals(type)) {
        final Aggregate filterLimitedAgg =
            filteredVarAgg.aggregations().get(FILTER_LIMITED_AGGREGATION);
        allDistributedByKeys =
            dateAggregationService
                .mapDateAggregationsToKeyAggregationMap(
                    (MultiBucketAggregateBase<? extends MultiBucketBase>)
                        (filterLimitedAgg == null
                            ? filteredVarAgg.aggregations().get(VARIABLES_AGGREGATION)._get()
                            : filterLimitedAgg
                                .filter()
                                .aggregations()
                                .get(VARIABLES_AGGREGATION)
                                ._get()),
                    context.getTimezone())
                .keySet();
      } else {
        final StringTermsAggregate varNamesAgg =
            filteredVarAgg.aggregations().get(VARIABLES_AGGREGATION).sterms();
        allDistributedByKeys =
            varNamesAgg.buckets().array().stream().map(b -> b.key().stringValue()).collect(toSet());
      }
    }

    final FilterAggregate missingVarAgg =
        parentFilterAgg.filter().aggregations().get(MISSING_VARIABLES_AGGREGATION).filter();
    if (missingVarAgg.docCount() > 0) {
      // instances with missing value for the variable exist, so add an extra bucket for those
      allDistributedByKeys.add(MISSING_VARIABLE_KEY);
    }

    context.setAllDistributedByKeys(allDistributedByKeys);
  }

  private void addEmptyMissingDistributedByResults(
      final List<CompositeCommandResult.DistributedByResult> distributedByResults,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    context.getAllDistributedByKeysAndLabels().entrySet().stream()
        .filter(
            entry ->
                distributedByResults.stream()
                    .noneMatch(
                        distributedByResult -> distributedByResult.getKey().equals(entry.getKey())))
        .map(
            entry ->
                createDistributedByResult(
                    entry.getKey(),
                    entry.getValue(),
                    getViewInterpreter().createEmptyResult(context)))
        .forEach(distributedByResults::add);
  }

  private void addMissingVariableBuckets(
      final List<CompositeCommandResult.DistributedByResult> distributedByResults,
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final FilterAggregate parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION).filter();
    final FilterAggregate missingVarAgg =
        parentFilterAgg.aggregations().get(MISSING_VARIABLES_AGGREGATION).filter();

    if (missingVarAgg.docCount() > 0) {
      final CompositeCommandResult.ViewResult viewResult =
          getViewInterpreter().retrieveResult(response, missingVarAgg.aggregations(), context);
      distributedByResults.add(createDistributedByResult(MISSING_VARIABLE_KEY, null, viewResult));
    }
  }

  private String getVariableName(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return getVariableDistributedByValueDto(context).getName();
  }

  private VariableType getVariableType(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return getVariableDistributedByValueDto(context).getType();
  }

  private String getNestedVariableValueFieldLabel(final VariableType type) {
    return getNestedVariableValueFieldForType(type);
  }

  private VariableDistributedByValueDto getVariableDistributedByValueDto(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return ((VariableDistributedByDto) context.getReportData().getDistributedBy()).getValue();
  }

  private AggregateByDateUnit getDistributeByDateUnit(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getReportData().getConfiguration().getDistributeByDateVariableUnit();
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }
}
