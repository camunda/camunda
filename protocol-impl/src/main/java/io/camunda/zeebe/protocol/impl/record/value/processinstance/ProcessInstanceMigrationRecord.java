/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;

public final class ProcessInstanceMigrationRecord extends UnifiedRecordValue
    implements ProcessInstanceMigrationRecordValue {

  private final LongProperty targetProcessDefinitionKeyProperty =
      new LongProperty("targetProcessDefinitionKey");

  public ProcessInstanceMigrationRecord() {
    declareProperty(targetProcessDefinitionKeyProperty);
  }

  @Override
  public long getTargetProcessDefinitionKey() {
    return targetProcessDefinitionKeyProperty.getValue();
  }

  public void setTargetProcessDefinitionKeyProperty(final long key) {
    targetProcessDefinitionKeyProperty.setValue(key);
  }

  /** hashCode relies on implementation provided by {@link ObjectValue#hashCode()} */
  @Override
  public int hashCode() {
    return super.hashCode();
  }

  /** equals relies on implementation provided by {@link ObjectValue#equals(Object)} */
  @Override
  public boolean equals(final Object o) {
    return super.equals(o);
  }
}
