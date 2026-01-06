/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.BatchOperationRestService.BATCH_OPERATIONS_URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.reader.BatchOperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.transform.DataAggregator;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Batch operations")
@RestController
@RequestMapping(value = BATCH_OPERATIONS_URL)
@ConditionalOnRdbmsDisabled
public class BatchOperationRestService extends InternalAPIErrorController {

  public static final String BATCH_OPERATIONS_URL = "/api/batch-operations";

  @Autowired private BatchOperationReader batchOperationReader;
  @Autowired private DataAggregator dataAggregator;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Operation(summary = "Query batch operations")
  @PostMapping
  public List<BatchOperationDto> queryBatchOperations(
      @RequestBody final BatchOperationRequestDto batchOperationRequestDto) {
    if (batchOperationRequestDto.getPageSize() == null) {
      throw new InvalidRequestException("pageSize parameter must be provided.");
    }
    if (batchOperationRequestDto.getSearchAfter() != null
        && batchOperationRequestDto.getSearchBefore() != null) {
      throw new InvalidRequestException(
          "Only one of parameters must be present in request: either searchAfter or searchBefore.");
    }
    if (batchOperationRequestDto.getSearchBefore() != null
        && (batchOperationRequestDto.getSearchBefore().length != 2)) {
      throw new InvalidRequestException("searchBefore must be an array of two values.");
    }

    if (batchOperationRequestDto.getSearchAfter() != null
        && (batchOperationRequestDto.getSearchAfter().length != 2)) {
      throw new InvalidRequestException("searchAfter must be an array of two values.");
    }

    final List<BatchOperationEntity> batchOperations =
        batchOperationReader.getBatchOperations(batchOperationRequestDto);
    return dataAggregator.enrichBatchEntitiesWithMetadata(batchOperations);
  }
}
