/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.logstreams.log.LogStream;
import java.util.Objects;

public class Leader {

  private final int nodeId;
  private final long term;
  private final LogStream logStream;

  public Leader(final int nodeId, final long term, final LogStream logStream) {
    this.nodeId = nodeId;
    this.term = term;
    this.logStream = logStream;
  }

  public int getNodeId() {
    return nodeId;
  }

  public long getTerm() {
    return term;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId, term);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Leader leader = (Leader) o;
    return nodeId == leader.nodeId && term == leader.term;
  }
}
