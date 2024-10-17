/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.definition;

import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;

public class ZeebeProcessDefinitionRecordDto
    extends ZeebeRecordDto<ZeebeProcessDefinitionDataDto, ProcessIntent> {

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeProcessDefinitionRecordDto;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeProcessDefinitionRecordDto)) {
      return false;
    }
    final ZeebeProcessDefinitionRecordDto other = (ZeebeProcessDefinitionRecordDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }
}
