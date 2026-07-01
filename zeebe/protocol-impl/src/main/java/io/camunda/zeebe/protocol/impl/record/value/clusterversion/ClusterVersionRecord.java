/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clusterversion;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVersionRecordValue;

public final class ClusterVersionRecord extends UnifiedRecordValue
    implements ClusterVersionRecordValue {

  private final IntegerProperty lineProp = new IntegerProperty("line", 0);
  private final IntegerProperty ordinalProp = new IntegerProperty("ordinal", 0);
  private final StringProperty gatedFieldProp = new StringProperty("gatedField", "");
  private final StringProperty flagNameProp = new StringProperty("flagName", "");

  public ClusterVersionRecord() {
    super(4);
    declareProperty(lineProp)
        .declareProperty(ordinalProp)
        .declareProperty(gatedFieldProp)
        .declareProperty(flagNameProp);
  }

  @Override
  public int getLine() {
    return lineProp.getValue();
  }

  public ClusterVersionRecord setLine(final int line) {
    lineProp.setValue(line);
    return this;
  }

  @Override
  public int getOrdinal() {
    return ordinalProp.getValue();
  }

  public ClusterVersionRecord setOrdinal(final int ordinal) {
    ordinalProp.setValue(ordinal);
    return this;
  }

  @Override
  public String getGatedField() {
    return bufferAsString(gatedFieldProp.getValue());
  }

  public ClusterVersionRecord setGatedField(final String gatedField) {
    if (gatedField == null) {
      return this;
    }
    gatedFieldProp.setValue(gatedField);
    return this;
  }

  @Override
  public String getFlagName() {
    return bufferAsString(flagNameProp.getValue());
  }

  public ClusterVersionRecord setFlagName(final String flagName) {
    if (flagName == null) {
      return this;
    }
    flagNameProp.setValue(flagName);
    return this;
  }
}
