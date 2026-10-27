/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import org.agrona.DirectBuffer;

public final class VariableInstance extends UnpackedObject implements DbValue {

  private final LongProperty keyProp = new LongProperty("key");
  private final BinaryProperty valueProp = new BinaryProperty("value");
  private final ObjectProperty<VariableMetadataRecord> metadataProp =
      new ObjectProperty<>("metadata", new VariableMetadataRecord());

  public VariableInstance() {
    super(3);
    declareProperty(keyProp).declareProperty(valueProp).declareProperty(metadataProp);
  }

  public VariableMetadataRecord getMetadata() {
    return metadataProp.getValue();
  }

  public VariableInstance setMetadata(final VariableMetadataRecord metadata) {
    metadataProp.getValue().wrap(metadata);
    return this;
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public VariableInstance setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public VariableInstance setValue(final DirectBuffer value, final int offset, final int length) {
    valueProp.setValue(value, offset, length);
    return this;
  }

  public DirectBuffer getValue() {
    return valueProp.getValue();
  }
}
