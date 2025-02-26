/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.List;
import java.util.stream.Collectors;

public class ItemKeys extends UnpackedObject implements DbValue {

  private final ArrayProperty<LongValue> keysProp =
      new ArrayProperty<>("keys", LongValue::new);

  public ItemKeys() {
    super(1);
    declareProperty(keysProp);
  }

  public List<Long> getKeys() {
    return keysProp.stream().map(LongValue::getValue)
        .collect(Collectors.toList());
  }

  public void setKeys(final List<Long> keys) {
    keysProp.reset();
    keys.forEach(key -> keysProp.add().setValue(key));
  }

}
