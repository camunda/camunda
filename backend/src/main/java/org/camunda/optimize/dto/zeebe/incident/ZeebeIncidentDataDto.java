/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.zeebe.incident;

import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@Data
public class ZeebeIncidentDataDto implements IncidentRecordValue {

  private String errorMessage;
  private String bpmnProcessId;
  private String elementId;
  private long elementInstanceKey;
  private long processInstanceKey;
  private long processDefinitionKey;
  private long jobKey;
  private ErrorType errorType;
  private long variableScopeKey;

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }
}
