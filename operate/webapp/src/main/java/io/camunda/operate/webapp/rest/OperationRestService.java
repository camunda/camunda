/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.OperationRestService.OPERATION_URL;

import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Operations")
@RestController
@RequestMapping(value = OPERATION_URL)
public class OperationRestService extends InternalAPIErrorController {

  public static final String OPERATION_URL = "/api/operations";

  @Autowired private OperationReader operationReader;

  @Operation(summary = "Get single operation")
  @GetMapping
  public List<OperationDto> getOperation(@RequestParam final String batchOperationId) {
    return operationReader.getOperationsByBatchOperationId(batchOperationId);
  }
}
