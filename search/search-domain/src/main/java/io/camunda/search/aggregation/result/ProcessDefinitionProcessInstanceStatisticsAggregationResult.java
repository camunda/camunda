package io.camunda.search.aggregation.result;

import io.camunda.search.entities.ProcessDefinitionProcessInstanceStatisticsEntity;
import java.util.List;

public record ProcessDefinitionProcessInstanceStatisticsAggregationResult(
    List<ProcessDefinitionProcessInstanceStatisticsEntity> items, int totalItems)
    implements AggregationResultBase {}
