/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.FLAT_FLOW_NODE_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.FLAT_FLOW_NODE_INSTANCE_INDEX;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getFlatFlowNodeInstanceIndexAliasName;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.query.process.FlatFlowNodeInstanceDto;
import io.camunda.optimize.service.db.repository.IndexRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeInstanceWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(FlowNodeInstanceWriter.class);
  private final IndexRepository indexRepository;

  public FlowNodeInstanceWriter(final IndexRepository indexRepository) {
    this.indexRepository = indexRepository;
  }

  public List<ImportRequestDto> generateFlatFlowNodeInstanceImports(
      final List<FlatFlowNodeInstanceDto> flowNodeInstances) {
    final String importItemName = "flat flow node instances";
    LOG.debug("Creating imports for [{}].", importItemName);
    indexRepository.createMissingIndices(
        FLAT_FLOW_NODE_INSTANCE_INDEX,
        Set.of(FLAT_FLOW_NODE_INSTANCE_MULTI_ALIAS),
        flowNodeInstances.stream()
            .map(FlatFlowNodeInstanceDto::getProcessDefinitionKey)
            .collect(Collectors.toSet()));

    return flowNodeInstances.stream()
        .map(
            flowNodeInstance ->
                ImportRequestDto.builder()
                    .importName(importItemName)
                    .type(RequestType.INDEX)
                    .id(flowNodeInstance.getFlowNodeInstanceId())
                    .indexName(
                        getFlatFlowNodeInstanceIndexAliasName(
                            flowNodeInstance.getProcessDefinitionKey()))
                    .source(flowNodeInstance)
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .build())
        .toList();
  }
}
