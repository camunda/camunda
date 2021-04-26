/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.ObjectProperty;

@Deprecated
public final class StoredRecord extends UnpackedObject implements DbValue {

  private final ObjectProperty<IndexedRecord> recordProp =
      new ObjectProperty<>("record", new IndexedRecord());
  private final EnumProperty<Purpose> purposeProp = new EnumProperty<>("purpose", Purpose.class);

  public StoredRecord(final IndexedRecord record, final Purpose purpose) {
    this();

    recordProp
        .getValue()
        .setKey(record.getKey())
        .setState(record.getState())
        .setValue(record.getValue());
    purposeProp.setValue(purpose);
  }

  /** deserialization constructor */
  public StoredRecord() {
    declareProperty(purposeProp).declareProperty(recordProp);
  }

  public IndexedRecord getRecord() {
    return recordProp.getValue();
  }

  public Purpose getPurpose() {
    return purposeProp.getValue();
  }

  public long getKey() {
    return recordProp.getValue().getKey();
  }

  @Deprecated
  public enum Purpose {
    // Order is important, as we use the ordinal for persistence
    DEFERRED
  }
}
