/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

public sealed interface MessageRoutingConfiguration {
  String name();

  static MessageRoutingConfiguration fixed(final int partitionCount) {
    return new FixedPartitionCount(partitionCount);
  }

  record Migrating(int oldPartitionCount, int newPartitionCount)
      implements MessageRoutingConfiguration {

    @Override
    public String name() {
      return "migrating";
    }
  }

  record FixedPartitionCount(int partitionCount) implements MessageRoutingConfiguration {

    @Override
    public String name() {
      return "fixed";
    }
  }
}
