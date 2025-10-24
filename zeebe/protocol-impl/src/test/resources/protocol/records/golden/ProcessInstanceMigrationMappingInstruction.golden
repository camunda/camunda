/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public class ProcessInstanceMigrationMappingInstruction extends ObjectValue
    implements ProcessInstanceMigrationMappingInstructionValue {

  private final StringProperty sourceElementIdProperty = new StringProperty("sourceElementId", "");
  private final StringProperty targetElementIdProperty = new StringProperty("targetElementId", "");

  public ProcessInstanceMigrationMappingInstruction() {
    super(2);
    declareProperty(sourceElementIdProperty).declareProperty(targetElementIdProperty);
  }

  @Override
  public String getSourceElementId() {
    return BufferUtil.bufferAsString(getSourceElementIdBuffer());
  }

  public ProcessInstanceMigrationMappingInstruction setSourceElementId(
      final String sourceElementId) {
    sourceElementIdProperty.setValue(sourceElementId);
    return this;
  }

  public ProcessInstanceMigrationMappingInstruction setSourceElementId(
      final DirectBuffer sourceElementId) {
    sourceElementIdProperty.setValue(sourceElementId);
    return this;
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(getTargetElementIdBuffer());
  }

  public ProcessInstanceMigrationMappingInstruction setTargetElementId(
      final String targetElementId) {
    targetElementIdProperty.setValue(targetElementId);
    return this;
  }

  public ProcessInstanceMigrationMappingInstruction setTargetElementId(
      final DirectBuffer targetElementId) {
    targetElementIdProperty.setValue(targetElementId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getSourceElementIdBuffer() {
    return sourceElementIdProperty.getValue();
  }

  @JsonIgnore
  public DirectBuffer getTargetElementIdBuffer() {
    return targetElementIdProperty.getValue();
  }

  public void copy(final ProcessInstanceMigrationMappingInstruction other) {
    sourceElementIdProperty.setValue(other.getSourceElementIdBuffer());
    targetElementIdProperty.setValue(other.getTargetElementIdBuffer());
  }
}
