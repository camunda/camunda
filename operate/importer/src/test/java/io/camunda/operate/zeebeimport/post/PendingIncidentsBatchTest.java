/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PendingIncidentsBatchTest {

  @Test
  public void testDifferentReturnTypes() {
    final Object lastProcessedPosition = Integer.valueOf(5);
    final PendingIncidentsBatch batch = new PendingIncidentsBatch();
    batch.setLastProcessedPosition(lastProcessedPosition);
    assertTrue(batch.getLastProcessedPosition().equals(5L));

    final Object lastProcessedPosition2 = Long.valueOf(5000000000L);
    final PendingIncidentsBatch batch2 = new PendingIncidentsBatch();
    batch2.setLastProcessedPosition(lastProcessedPosition2);
    assertTrue(batch2.getLastProcessedPosition().equals(5000000000L));

    final Object lastProcessedPosition3 = Byte.valueOf((byte) 5);
    final PendingIncidentsBatch batch3 = new PendingIncidentsBatch();
    batch3.setLastProcessedPosition(lastProcessedPosition3);
    assertTrue(batch3.getLastProcessedPosition().equals(5L));
  }
}
