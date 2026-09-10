/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

public class ParentScopeKey extends UnpackedObject implements DbValue {
  private final LongProperty keyProp = new LongProperty("parentScopeKey", -1L);

  /**
   * Whether the scope has no variables of its own, in which case reading them can be skipped
   * without hitting the VARIABLES column family. Defaults to {@code false} so that records written
   * before this property existed are read as "may have local variables", which is correct, just
   * unoptimized.
   */
  private final BooleanProperty emptyProp = new BooleanProperty("empty", false);

  public ParentScopeKey() {
    super(2);
    declareProperty(keyProp).declareProperty(emptyProp);
  }

  public void set(final long key, final boolean empty) {
    keyProp.setValue(key);
    emptyProp.setValue(empty);
  }

  public long get() {
    return keyProp.getValue();
  }

  public boolean isEmpty() {
    return emptyProp.getValue();
  }
}
