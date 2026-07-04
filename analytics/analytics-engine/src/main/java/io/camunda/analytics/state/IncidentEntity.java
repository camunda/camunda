/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * The Model-A materialized row for one open incident, keyed by {@code elementInstanceKey}: its
 * {@code createMs} and {@code errorType} (set on {@code INCIDENT/CREATED}) and {@code resolveMs}
 * (set on {@code RESOLVED}). The resolution deriver reads the resolution duration and the incident
 * type straight off the row before it is evicted, so the type is a queryable property of the row.
 */
public final class IncidentEntity extends UnpackedObject implements DbValue {

  private static final long UNSET = -1L;

  private final LongProperty createMsProp = new LongProperty("createMs", UNSET);
  private final LongProperty resolveMsProp = new LongProperty("resolveMs", UNSET);
  private final StringProperty errorTypeProp = new StringProperty("errorType", "");

  public IncidentEntity() {
    super(3);
    declareProperty(createMsProp).declareProperty(resolveMsProp).declareProperty(errorTypeProp);
  }

  public IncidentEntity open(final long createMs, final String errorType) {
    reset();
    createMsProp.setValue(createMs);
    errorTypeProp.setValue(errorType);
    return this;
  }

  public IncidentEntity resolve(final long resolveMs) {
    resolveMsProp.setValue(resolveMs);
    return this;
  }

  public long createMs() {
    return createMsProp.getValue();
  }

  public long resolveMs() {
    return resolveMsProp.getValue();
  }

  public String errorType() {
    return BufferUtil.bufferAsString(errorTypeProp.getValue());
  }
}
