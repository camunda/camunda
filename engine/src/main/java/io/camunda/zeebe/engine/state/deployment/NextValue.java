/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;

public final class NextValue extends UnpackedObject implements DbValue {

  // The next value indicate what version the next deployed process with this id should get
  private final LongProperty nextValueProp = new LongProperty("nextValue", -1L);

  // The latest value indicates the latest version of a deployed process we should start when no
  // version is specified.
  private final LongProperty latestValueProp = new LongProperty("latestValue", -1L);

  public NextValue() {
    declareProperty(nextValueProp).declareProperty(latestValueProp);
  }

  public long getMaximumVersion() {
    return nextValueProp.getValue();
  }

  public NextValue setMaximumVersion(final long value) {
    nextValueProp.setValue(value);
    return this;
  }

  public long getLatestVersion() {
    final long latestVersion = latestValueProp.getValue();
    // If the latestVersion is not set this is an older process and no instances have been deleted
    // for this process yet. As a result we should consider the next value to be the latest.
    if (latestVersion == -1L) {
      return getMaximumVersion();
    }
    return latestVersion;
  }

  public NextValue setLatestVersion(final long value) {
    latestValueProp.setValue(value);
    return this;
  }
}
