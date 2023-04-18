/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.management;

import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.management.AggregatedChangesRecordValue;
import org.agrona.concurrent.UnsafeBuffer;

public class AggregatedChangesRecord extends UnifiedRecordValue
    implements AggregatedChangesRecordValue {

  private static final String CHANGES_KEY = "changes";

  private final BinaryProperty changesProperty = new BinaryProperty(CHANGES_KEY);

  public AggregatedChangesRecord() {
    declareProperty(changesProperty);
  }

  @Override
  public byte[] changes() {
    return changesProperty.getValue().byteArray();
  }

  public AggregatedChangesRecord setChanges(final byte[] changes) {
    changesProperty.setValue(new UnsafeBuffer(changes));
    return this;
  }
}
