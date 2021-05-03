/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.stream;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.LongProperty;

public class ExporterPosition extends UnpackedObject implements DbValue {
  private final LongProperty positionProp = new LongProperty("exporterPosition");

  public ExporterPosition() {
    declareProperty(positionProp);
  }

  public void set(final long position) {
    positionProp.setValue(position);
  }

  public long get() {
    return positionProp.getValue();
  }
}
