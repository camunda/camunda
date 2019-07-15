/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log.impl;

import io.zeebe.distributedlog.restore.log.LogReplicationAppender;
import java.util.ArrayList;
import java.util.List;

public class RecordingLogReplicationAppender implements LogReplicationAppender {
  private final List<Invocation> invocations = new ArrayList<>();

  public List<Invocation> getInvocations() {
    return invocations;
  }

  @Override
  public long append(long commitPosition, byte[] blockBuffer) {
    invocations.add(new Invocation(commitPosition, blockBuffer));
    return 1; // always return success
  }

  public void reset() {
    invocations.clear();
  }

  public static class Invocation {
    final long commitPosition;
    final byte[] serializedEvents;

    public Invocation(long commitPosition, byte[] serializedEvents) {
      this.commitPosition = commitPosition;
      this.serializedEvents = serializedEvents;
    }
  }
}
