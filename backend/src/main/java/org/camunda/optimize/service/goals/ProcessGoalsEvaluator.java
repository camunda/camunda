/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.goals;

import com.google.common.collect.Iterables;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalResultDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.util.DateFilterQueryUtil;
import org.camunda.optimize.service.es.report.command.util.DurationScriptUtil;
import org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.InstanceIndexUtil;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.PercentilesAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@RequiredArgsConstructor
@Component
@Slf4j
public class ProcessGoalsEvaluator {

  private static final String PROC_DEF_KEY_AGG = "procDefKeyAggregation";
  private static final String PERCENTILES_AGG = "percentilesAggregation";

  private final OptimizeElasticsearchClient esClient;

  public List<ProcessDurationGoalResultDto> evaluateGoalsForProcess(final ProcessGoalsDto processGoals) {
    return evaluateGoals(Map.of(
      processGoals.getProcessDefinitionKey(),
      processGoals
    )).get(processGoals.getProcessDefinitionKey());
  }

  public Map<String, List<ProcessDurationGoalResultDto>> evaluateGoals(final Map<String, ProcessGoalsDto> goalsByDefKey) {
    Map<String, PercentileToResultMap> percentilesByDefKey = new HashMap<>();
    final Iterable<List<String>> partitions = Iterables.partition(goalsByDefKey.keySet(), 1000);
    // We partition the requests as the number of processes is boundless and percentiles cannot be part of a
    // composite aggregation
    for (List<String> defKeysInPartition : partitions) {
      addPercentileResultsForProcessesInPartition(
        goalsByDefKey,
        percentilesByDefKey,
        defKeysInPartition
      );
    }
    return extractPercentileResults(goalsByDefKey, percentilesByDefKey);
  }

  @NotNull
  private Map<String, List<ProcessDurationGoalResultDto>> extractPercentileResults(final Map<String, ProcessGoalsDto> goalsByDefKey,
                                                                                   final Map<String, PercentileToResultMap> percentilesByDefKey) {
    Map<String, List<ProcessDurationGoalResultDto>> results = new HashMap<>();
    goalsByDefKey.forEach((defKey, goals) -> {
      final List<ProcessDurationGoalDto> goalsForKey = goals.getDurationGoals();
      final PercentileToResultMap percentileForKey = percentilesByDefKey.get(defKey);
      List<ProcessDurationGoalResultDto> resultsForKey;
      if (percentileForKey == null) {
        resultsForKey = goalsForKey.stream()
          .map(goal -> new ProcessDurationGoalResultDto(goal.getType(), null, null))
          .collect(Collectors.toList());
      } else {
        resultsForKey = goalsForKey.stream()
          .map(goal -> {
            final Map<Double, Double> percentileToResultsMap = percentileForKey.getPercentileKeyAndValue();
            final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
            final long targetDuration =
              now.minus(goal.getValue(), ChronoUnit.valueOf(goal.getUnit().name())).until(now, ChronoUnit.MILLIS);
            final long actualDuration = Math.round(percentileToResultsMap.get(goal.getPercentile()));
            return new ProcessDurationGoalResultDto(
              goal.getType(),
              actualDuration,
              actualDuration <= targetDuration
            );
          })
          .collect(Collectors.toList());
      }
      results.put(defKey, resultsForKey);
    });
    return results;
  }

  private void addPercentileResultsForProcessesInPartition(final Map<String, ProcessGoalsDto> goalsByDefKey,
                                                           final Map<String, PercentileToResultMap> percentilesByDefKey,
                                                           final List<String> defKeysInPartition) {
    final double[] percentilesToAggregate = determinePercentilesToAggregate(goalsByDefKey, defKeysInPartition);
    // In the unlikely case that all the goals are empty for all keys in batch, we add empty results
    if (percentilesToAggregate.length == 0) {
      defKeysInPartition.forEach(defKey -> percentilesByDefKey.put(defKey, new PercentileToResultMap()));
    } else {
      executeSearchQuery(defKeysInPartition, percentilesToAggregate)
        .ifPresent(searchResponse -> {
          final Terms procDefKeyTerms = searchResponse.getAggregations().get(PROC_DEF_KEY_AGG);
          procDefKeyTerms.getBuckets()
            .forEach(bucket -> {
              ParsedTDigestPercentiles percentiles = bucket.getAggregations().get(PERCENTILES_AGG);
              final PercentileToResultMap resultMap = new PercentileToResultMap();
              Arrays.stream(percentilesToAggregate).forEach(
                percentile -> {
                  final Double percentileValue = ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull(
                    percentiles,
                    percentile
                  );
                  resultMap.percentileKeyAndValue.put(percentile, percentileValue);
                }
              );
              percentilesByDefKey.put(bucket.getKeyAsString(), resultMap);
            });
        });
    }
  }

  private Optional<SearchResponse> executeSearchQuery(final List<String> defKeysInPartition,
                                                      final double[] percentilesToAggregate) {
    final BoolQueryBuilder query = boolQuery().must(termsQuery(PROCESS_DEFINITION_KEY, defKeysInPartition));
    // This filter applies to the end date field, ensuring that only completed instances are considered for goal
    // evaluation
    DateFilterQueryUtil.addFilters(query, List.of(new RollingDateFilterDataDto(
      new RollingDateFilterStartDto(30L, DateUnit.DAYS)
    )), END_DATE, ZoneId.systemDefault());
    final PercentilesAggregationBuilder percentilesAgg = percentiles(PERCENTILES_AGG)
      .percentiles(percentilesToAggregate)
      .script(DurationScriptUtil.getDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(), DURATION, START_DATE
      ));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(terms(PROC_DEF_KEY_AGG).field(PROCESS_DEFINITION_KEY).subAggregation(percentilesAgg))
      .size(0);

    try {
      return Optional.of(esClient.search(new SearchRequest(PROCESS_INSTANCE_MULTI_ALIAS).source(searchSourceBuilder)));
    } catch (IOException | ElasticsearchStatusException e) {
      if (e instanceof ElasticsearchStatusException && InstanceIndexUtil.isInstanceIndexNotFoundException((ElasticsearchStatusException) e)) {
        log.info("No process instances for processes with keys {}, so cannot evaluate process", defKeysInPartition);
        return Optional.empty();
      }
      String reason = String.format(
        "Was not able to evaluate process goals for batch with keys [%s]",
        defKeysInPartition
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private double[] determinePercentilesToAggregate(final Map<String, ProcessGoalsDto> goalsByDefKey,
                                                   final List<String> defKeysInPartition) {
    return defKeysInPartition
      .stream()
      .flatMap(key -> goalsByDefKey.get(key).getDurationGoals().stream().map(ProcessDurationGoalDto::getPercentile))
      .distinct()
      .mapToDouble(Double::doubleValue)
      .toArray();
  }

  @Data
  private static class PercentileToResultMap {
    private Map<Double, Double> percentileKeyAndValue = new HashMap<>();
  }

}
