/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random;

import java.util.concurrent.atomic.AtomicLong;

/** Helper class to generate unique ids */
public final class IDGenerator {

  private final AtomicLong id;

  public IDGenerator(final long startId) {
    id = new AtomicLong(startId);
  }

  public String nextId() {
    return "id_" + id.getAndIncrement();
  }
}
