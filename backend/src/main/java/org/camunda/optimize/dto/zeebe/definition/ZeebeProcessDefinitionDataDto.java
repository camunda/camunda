/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.zeebe.definition;

import io.camunda.zeebe.protocol.record.RecordValue;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@Data
public class ZeebeProcessDefinitionDataDto implements RecordValue {

  private byte[] resource;
  private String processDefinitionKey;
  private long version;
  private String checksum;
  private String resourceName;
  private String bpmnProcessId;

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }
}
