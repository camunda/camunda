/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_NAME_BY_PROCESS_ID;
import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_NAME_LATEST_DEFINITION;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.aggregation.result.ProcessDefinitionLatestVersionAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.ProcessDefinitionEntity;
import java.util.List;
import java.util.Map;

public class ProcessDefinitionLatestVersionAggregationResultTransformer
    implements AggregationResultTransformer<ProcessDefinitionLatestVersionAggregationResult> {

  @Override
  public ProcessDefinitionLatestVersionAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {
    return new ProcessDefinitionLatestVersionAggregationResult(
        aggregations.get(AGGREGATION_NAME_BY_PROCESS_ID).aggregations().values().stream()
            .flatMap(
                aggregationResult -> {
                  final var latestDefinition =
                      aggregationResult.aggregations().get(AGGREGATION_NAME_LATEST_DEFINITION);
                  final var jsonHits = latestDefinition.jsonHits();
                  final var objectMapper = new ObjectMapper();
                  try {
                    final var list =
                        objectMapper.readValue(
                            jsonHits, new TypeReference<List<Map<String, Object>>>() {});
                    return list.stream()
                        .map(
                            itemAsMap ->
                                new ProcessDefinitionEntity(
                                    (Long) itemAsMap.get("key"),
                                    (String) itemAsMap.get("name"),
                                    (String) itemAsMap.get("bpmnProcessId"),
                                    (String) itemAsMap.get("bpmnXml"),
                                    (String) itemAsMap.get("resourceName"),
                                    (Integer) itemAsMap.get("version"),
                                    (String) itemAsMap.get("versionTag"),
                                    (String) itemAsMap.get("tenantId"),
                                    (String) itemAsMap.get("formId")));
                  } catch (final JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toList());
  }
}
