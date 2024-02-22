/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.distribution;

import io.atomix.cluster.MemberId;
import java.util.Objects;

final class FixedDistributionMember {
  private final MemberId id;
  private final int priority;

  FixedDistributionMember(final MemberId id, final int priority) {
    this.id = id;
    this.priority = priority;
  }

  MemberId getId() {
    return id;
  }

  int getPriority() {
    return priority;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof FixedDistributionMember)) {
      return false;
    }

    final FixedDistributionMember member = (FixedDistributionMember) o;
    return id.equals(member.id);
  }

  @Override
  public String toString() {
    return "FixedDistributionMember{" + "id=" + id + ", priority=" + priority + '}';
  }
}
