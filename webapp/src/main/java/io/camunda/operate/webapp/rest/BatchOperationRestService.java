/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.webapp.rest.dto.DtoCreator;
import java.util.List;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.es.reader.BatchOperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import static io.camunda.operate.webapp.rest.BatchOperationRestService.BATCH_OPERATIONS_URL;

@Api(tags = {"Batch operations"})
@SwaggerDefinition(tags = {
    @Tag(name = "Batch operations", description = "Batch operations")
})
@RestController
@RequestMapping(value = BATCH_OPERATIONS_URL)
public class BatchOperationRestService {

  public static final String BATCH_OPERATIONS_URL = "/api/batch-operations";

  @Autowired
  private BatchOperationReader batchOperationReader;

  @ApiOperation("Query batch operations")
  @PostMapping
  public List<BatchOperationDto> queryBatchOperations(@RequestBody BatchOperationRequestDto batchOperationRequestDto) {
    if (batchOperationRequestDto.getPageSize() == null) {
      throw new InvalidRequestException("pageSize parameter must be provided.");
    }
    if (batchOperationRequestDto.getSearchAfter() != null && batchOperationRequestDto.getSearchBefore() != null) {
      throw new InvalidRequestException("Only one of parameters must be present in request: either searchAfter or searchBefore.");
    }
    if (batchOperationRequestDto.getSearchBefore() != null && (batchOperationRequestDto.getSearchBefore().length != 2 || !CollectionUtil
        .allElementsAreOfType(String.class, batchOperationRequestDto.getSearchBefore()))) {
      throw new InvalidRequestException("searchBefore must be an array of two string values.");
    }

    if (batchOperationRequestDto.getSearchAfter() != null && (batchOperationRequestDto.getSearchAfter().length != 2 || !CollectionUtil
        .allElementsAreOfType(String.class, batchOperationRequestDto.getSearchAfter()))) {
      throw new InvalidRequestException("searchAfter must be an array of two string values.");
    }

    List<BatchOperationEntity> batchOperations = batchOperationReader.getBatchOperations(batchOperationRequestDto);
    return DtoCreator.create(batchOperations, BatchOperationDto.class);
  }

}
