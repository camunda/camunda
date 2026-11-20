/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.real;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

public final class LegacyChild extends UnpackedObject {
  private final LongProperty id = new LongProperty("id", -1L);
  private final StringProperty label = new StringProperty("label", "");

  public LegacyChild() {
    super(2);
    declareProperty(id).declareProperty(label);
  }

  public void fill(final int seed) {
    id.setValue(seed);
    label.setValue("label-" + seed);
  }
}
