/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessInstanceRequestValidatorTest {
  @Mock private CreateRequestOperationValidator createRequestOperationValidator;
  @Mock private CreateBatchOperationRequestValidator createBatchOperationRequestValidator;

  private ProcessInstanceRequestValidator processInstanceRequestValidator;

  @BeforeEach
  public void setup() {
    processInstanceRequestValidator =
        new ProcessInstanceRequestValidator(
            createRequestOperationValidator, createBatchOperationRequestValidator);
  }

  @Test
  public void validateFlowNodeStatisticsRequestWithNoProcessId() {
    final ListViewQueryDto request = new ListViewQueryDto();

    assertThrows(
        InvalidRequestException.class,
        () -> processInstanceRequestValidator.validateFlowNodeStatisticsRequest(request));
  }

  @Test
  public void validateFlowNodeStatisticsRequestWithBpmnProcessIdButNoVersion() {
    final ListViewQueryDto request = new ListViewQueryDto().setBpmnProcessId("demoProcess");

    assertThrows(
        InvalidRequestException.class,
        () -> processInstanceRequestValidator.validateFlowNodeStatisticsRequest(request));
  }

  @Test
  public void validateFlowNodeStatisticsRequestWithMoreThanOneProcessDefinitionKey() {
    final ListViewQueryDto request = new ListViewQueryDto().setProcessIds(List.of("1", "2"));

    assertThrows(
        InvalidRequestException.class,
        () -> processInstanceRequestValidator.validateFlowNodeStatisticsRequest(request));
  }

  @Test
  public void validateFlowNodeStatisticsRequestWithProcessDefinitionKeyAndBpmnProcessId() {
    final ListViewQueryDto request =
        new ListViewQueryDto()
            .setBpmnProcessId("demoProcess")
            .setProcessVersion(1)
            .setProcessIds(List.of("1"));

    assertThrows(
        InvalidRequestException.class,
        () -> processInstanceRequestValidator.validateFlowNodeStatisticsRequest(request));
  }
}
