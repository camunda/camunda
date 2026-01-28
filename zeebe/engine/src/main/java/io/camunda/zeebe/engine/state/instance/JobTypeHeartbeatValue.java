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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

public final class JobTypeHeartbeatValue extends UnpackedObject implements DbValue {
  private final StringProperty workerProp = new StringProperty("worker");
  private final LongProperty lastSeenAtProp = new LongProperty("lastSeenAt");

  public JobTypeHeartbeatValue() {
    super(2);
    declareProperty(workerProp).declareProperty(lastSeenAtProp);
  }

  public void setWorker(final String worker) {
    workerProp.setValue(worker);
  }

  public String getWorker() {
    return workerProp.getValue();
  }

  public void setLastSeenAt(final long timestamp) {
    lastSeenAtProp.setValue(timestamp);
  }

  public long getLastSeenAt() {
    return lastSeenAtProp.getValue();
  }
}
