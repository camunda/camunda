/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.util.rest.ValidLongId;
import io.camunda.operate.webapp.operation.dto.CreateOperationRequestDto;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ProcessInstanceOperationRestService.PROCESS_INSTANCE_URL)
@Validated
@ConditionalOnRdbmsDisabled
public class ProcessInstanceOperationRestService {

  static final String PROCESS_INSTANCE_URL = "/api/process-instances";

  private final BatchOperationWriter batchOperationWriter;

  public ProcessInstanceOperationRestService(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @PostMapping("/{id}/operation")
  public BatchOperationEntity operation(
      @PathVariable @ValidLongId final String id,
      @RequestBody final CreateOperationRequestDto operationRequest) {
    return batchOperationWriter.scheduleSingleOperation(Long.parseLong(id), operationRequest);
  }
}
