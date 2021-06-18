/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.zeebe.definition;

import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@Data
public class ZeebeProcessDefinitionDataDto implements ProcessMetadataValue {

  private byte[] resource;
  private long processDefinitionKey;
  private int version;
  private byte[] checksum;
  private String resourceName;
  private String bpmnProcessId;

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }
}
