/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class DataAggregator {

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;

  public abstract Map<String, BatchOperationDto> requestAndAddMetadata(
      Map<String, BatchOperationDto> resultDtos, List<String> ids);

  public List<BatchOperationDto> enrichBatchEntitiesWithMetadata(
      final List<BatchOperationEntity> batchEntities) {

    if (CollectionUtils.isEmpty(batchEntities)) {
      return List.of();
    }

    /* using this map as starting point ensures that
     * 1. BatchOperations that have no completed operations yet are also included in the end result
     * 2. The sorting stays the same
     */
    final LinkedHashMap<String, BatchOperationDto> requestDtos =
        new LinkedHashMap<>(batchEntities.size());
    batchEntities.forEach(
        entity -> {
          requestDtos.put(entity.getId(), BatchOperationDto.createFrom(entity, objectMapper));
        });
    final List<String> idList = requestDtos.keySet().stream().toList();
    return requestAndAddMetadata(requestDtos, idList).values().stream().toList();
  }
}
