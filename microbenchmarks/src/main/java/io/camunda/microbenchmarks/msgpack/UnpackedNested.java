/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

/** Small {@link UnpackedObject}, nested in {@link UnpackedFull} and in {@link PojoFull}. */
public final class UnpackedNested extends UnpackedObject {

  private final LongProperty idProp = new LongProperty("id", -1L);
  private final StringProperty labelProp = new StringProperty("label", "");

  public UnpackedNested() {
    super(2);
    declareProperty(idProp).declareProperty(labelProp);
  }

  public UnpackedNested fill(final int seed) {
    idProp.setValue(seed);
    labelProp.setValue("label-" + seed);
    return this;
  }
}
