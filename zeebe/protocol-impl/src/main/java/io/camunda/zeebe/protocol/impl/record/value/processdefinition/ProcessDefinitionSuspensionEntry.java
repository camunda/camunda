/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processdefinition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionEntryValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class ProcessDefinitionSuspensionEntry extends ObjectValue
    implements ProcessDefinitionSuspensionEntryValue {

  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1);
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final EnumProperty<ProcessDefinitionSuspensionOutcome> outcomeProp =
      new EnumProperty<>("outcome", ProcessDefinitionSuspensionOutcome.class);

  public ProcessDefinitionSuspensionEntry() {
    super(3);
    declareProperty(processDefinitionKeyProp)
        .declareProperty(versionProp)
        .declareProperty(outcomeProp);
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public ProcessDefinitionSuspensionEntry setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  public ProcessDefinitionSuspensionEntry setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  @Override
  public ProcessDefinitionSuspensionOutcome getOutcome() {
    return outcomeProp.getValue();
  }

  public ProcessDefinitionSuspensionEntry setOutcome(
      final ProcessDefinitionSuspensionOutcome outcome) {
    outcomeProp.setValue(outcome);
    return this;
  }
}
