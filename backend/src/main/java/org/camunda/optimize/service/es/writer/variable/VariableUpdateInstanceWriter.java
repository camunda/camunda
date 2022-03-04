/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@AllArgsConstructor
@Component
@Slf4j
public class VariableUpdateInstanceWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<ImportRequestDto> generateVariableUpdateImports(final List<ProcessVariableDto> variableUpdates) {
    final List<VariableUpdateInstanceDto> variableUpdateInstances = variableUpdates.stream()
      .map(this::mapToVariableUpdateInstance)
      .collect(toList());

    String importItemName = "variable instances";
    log.debug("Creating imports for {} [{}].", variableUpdates.size(), importItemName);

    return variableUpdateInstances.stream()
      .map(this::createIndexRequestForVariableUpdate)
      .filter(Optional::isPresent)
      .map(request -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(request.get())
        .build())
      .collect(toList());
  }

  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
    log.info("Deleting variable updates for [{}] processInstanceIds", processInstanceIds.size());

    final BoolQueryBuilder filterQuery = boolQuery()
      .filter(termsQuery(VariableUpdateInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
      esClient,
      filterQuery,
      String.format("variable updates of %d process instances", processInstanceIds.size()),
      false,
      // use wildcarded index name to catch all indices that exist after potential rollover
      esClient.getIndexNameService()
        .getOptimizeIndexNameWithVersionWithWildcardSuffix(new VariableUpdateInstanceIndex())
    );
  }

  private VariableUpdateInstanceDto mapToVariableUpdateInstance(final ProcessVariableDto processVariable) {
    return VariableUpdateInstanceDto.builder()
      .instanceId(processVariable.getId())
      .name(processVariable.getName())
      .type(processVariable.getType())
      .value(processVariable.getValue() == null
               ? Collections.emptyList()
               : processVariable.getValue().stream().filter(Objects::nonNull).collect(toList()))
      .processInstanceId(processVariable.getProcessInstanceId())
      .tenantId(processVariable.getTenantId())
      .timestamp(processVariable.getTimestamp())
      .build();
  }

  private Optional<IndexRequest> createIndexRequestForVariableUpdate(VariableUpdateInstanceDto variableUpdateInstanceDto) {
    try {
      return Optional.of(new IndexRequest(VARIABLE_UPDATE_INSTANCE_INDEX_NAME)
                           .id(IdGenerator.getNextId())
                           .source(objectMapper.writeValueAsString(variableUpdateInstanceDto), XContentType.JSON));
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize Variable Instance: {}", variableUpdateInstanceDto, e);
      return Optional.empty();
    }
  }

}
