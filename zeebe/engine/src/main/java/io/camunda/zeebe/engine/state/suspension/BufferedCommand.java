/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.suspension;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;

/**
 * Column-family value wrapping a buffered forward-progress element command (POC #56552). The
 * embedded {@link ProcessInstanceRecord} carries the original element-command intent via its
 * internal {@code bufferedElementIntent} field.
 */
public final class BufferedCommand extends UnpackedObject implements DbValue {

  private static final StringValue RECORD_KEY = new StringValue("record");

  private final ObjectProperty<ProcessInstanceRecord> recordProp =
      new ObjectProperty<>(RECORD_KEY, new ProcessInstanceRecord());

  public BufferedCommand() {
    super(1);
    declareProperty(recordProp);
  }

  public ProcessInstanceRecord getRecord() {
    return recordProp.getValue();
  }

  public BufferedCommand setRecord(final ProcessInstanceRecord record) {
    // Full property copy (not the partial ProcessInstanceRecord#wrap) so that array properties
    // (elementInstancePath, processDefinitionPath, callingElementPath, tags) and the
    // bufferedElementIntent are preserved for exact replay on resume.
    recordProp.getValue().copyFrom(record);
    return this;
  }
}
