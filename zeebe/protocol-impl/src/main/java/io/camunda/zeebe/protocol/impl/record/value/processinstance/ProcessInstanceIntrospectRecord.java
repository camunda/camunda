/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceIntrospectRecordValue;
import java.util.List;

public class ProcessInstanceIntrospectRecord extends UnifiedRecordValue
    implements ProcessInstanceIntrospectRecordValue {

  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue ACTIONS_KEY = new StringValue("actions");
  private final LongProperty processInstanceKeyProperty =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY);
  private final ArrayProperty<ProcessInstanceIntrospectActionRecord> actionsProperty =
      new ArrayProperty<>(ACTIONS_KEY, ProcessInstanceIntrospectActionRecord::new);

  public ProcessInstanceIntrospectRecord() {
    super(2);
    declareProperty(processInstanceKeyProperty).declareProperty(actionsProperty);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessInstanceIntrospectRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProperty.setValue(key);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return -1L;
  }

  public ProcessInstanceIntrospectRecord addAction(
      final ProcessInstanceIntrospectActionRecordValue action) {
    actionsProperty.add().copy(action);
    return this;
  }

  @Override
  public List<ProcessInstanceIntrospectActionRecordValue> getActions() {
    return actionsProperty.stream()
        .map(
            action -> {
              final var copy = new ProcessInstanceIntrospectActionRecord();
              copy.copy(action);
              return (ProcessInstanceIntrospectActionRecordValue) copy;
            })
        .toList();
  }
}
