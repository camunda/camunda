/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.zeebe.protocol.jackson.record.JobRecordValueBuilder.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import org.immutables.value.Value;
import org.immutables.value.Value.Default;

@Value.Immutable
@ZeebeStyle
@JsonDeserialize(as = ImmutableJobRecordValue.class)
public abstract class AbstractJobRecordValue implements JobRecordValue, DefaultJsonSerializable {
  @Default
  @Override
  public String getErrorCode() {
    return ErrorCode.NULL_VAL.name();
  }
}
