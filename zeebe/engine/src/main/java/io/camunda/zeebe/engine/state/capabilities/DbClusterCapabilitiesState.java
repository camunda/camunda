/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.capabilities;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableClusterCapabilitiesState;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.ecv.Capability;
import io.camunda.zeebe.util.ecv.CapabilitySet;

public final class DbClusterCapabilitiesState implements MutableClusterCapabilitiesState {
  private static final String KEY = "ENABLED";

  private final ColumnFamily<DbString, EnabledCapabilitiesValue> columnFamily;
  private final DbString key = new DbString();
  private final EnabledCapabilitiesValue value = new EnabledCapabilitiesValue();
  private CapabilitySet supported = Capability.all();

  public DbClusterCapabilitiesState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLUSTER_CAPABILITIES, transactionContext, key, value);
  }

  @Override
  public CapabilitySet getEnabledCapabilities() {
    key.wrapString(KEY);
    final var stored = columnFamily.get(key);
    return stored == null ? CapabilitySet.EMPTY : CapabilitySet.decode(stored.getEncoded());
  }

  @Override
  public CapabilitySet getSupportedCapabilities() {
    return supported;
  }

  @Override
  public void addEnabledCapabilities(final CapabilitySet capabilities) {
    final var union = getEnabledCapabilities().union(capabilities);
    key.wrapString(KEY);
    value.setEncoded(union.encode());
    columnFamily.upsert(key, value);
  }

  @Override
  public void setSupportedCapabilities(final CapabilitySet supported) {
    this.supported = supported;
  }

  static final class EnabledCapabilitiesValue extends UnpackedObject implements DbValue {
    private final StringProperty encodedProperty = new StringProperty("encoded", "");

    EnabledCapabilitiesValue() {
      super(1);
      declareProperty(encodedProperty);
    }

    String getEncoded() {
      return BufferUtil.bufferAsString(encodedProperty.getValue());
    }

    void setEncoded(final String encoded) {
      encodedProperty.setValue(encoded);
    }
  }
}
