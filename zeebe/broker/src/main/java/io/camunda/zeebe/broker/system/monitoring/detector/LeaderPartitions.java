/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

/**
 * Tells whether this broker leads a partition. Detectors of signals that only leaders produce, such
 * as appends and exports, use it to ignore the meters a partition leaves behind after this broker
 * stepped down.
 */
@FunctionalInterface
public interface LeaderPartitions {

  /**
   * @param child a partition's component name, as {@link MeterReader#child} returns it
   */
  boolean isLeader(String child);
}
