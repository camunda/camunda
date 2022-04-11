/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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

  @Override
  public boolean isDuplicate() {
    // Process Records should never be duplicate in Zeebe
    return false;
  }
}
