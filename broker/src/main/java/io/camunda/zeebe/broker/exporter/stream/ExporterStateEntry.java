/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;

public class ExporterStateEntry extends UnpackedObject implements DbValue {
  private final LongProperty positionProp = new LongProperty("exporterPosition");

  public ExporterStateEntry() {
    declareProperty(positionProp);
  }

  public long getPosition() {
    return positionProp.getValue();
  }

  public void setPosition(final long position) {
    positionProp.setValue(position);
  }
}
