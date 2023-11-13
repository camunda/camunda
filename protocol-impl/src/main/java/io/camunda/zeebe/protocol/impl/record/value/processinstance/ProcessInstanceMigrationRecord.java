/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;

public final class ProcessInstanceMigrationRecord extends UnifiedRecordValue
    implements ProcessInstanceMigrationRecordValue {

  private final LongProperty processInstanceKeyProperty = new LongProperty("processInstanceKey");

  public ProcessInstanceMigrationRecord() {
    declareProperty(processInstanceKeyProperty);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessInstanceMigrationRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }
}
