/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.post;

import io.camunda.operate.zeebeimport.post.PendingIncidentsBatch;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PendingIncidentsBatchTest {

  @Test
  public void testDifferentReturnTypes() {
    Object lastProcessedPosition = Integer.valueOf(5);
    PendingIncidentsBatch batch = new PendingIncidentsBatch();
    batch.setLastProcessedPosition(lastProcessedPosition);
    assertTrue(batch.getLastProcessedPosition().equals(5L));

    Object lastProcessedPosition2 = Long.valueOf(5000000000L);
    PendingIncidentsBatch batch2 = new PendingIncidentsBatch();
    batch2.setLastProcessedPosition(lastProcessedPosition2);
    assertTrue(batch2.getLastProcessedPosition().equals(5000000000L));

    Object lastProcessedPosition3 = Byte.valueOf((byte)5);
    PendingIncidentsBatch batch3 = new PendingIncidentsBatch();
    batch3.setLastProcessedPosition(lastProcessedPosition3);
    assertTrue(batch3.getLastProcessedPosition().equals(5L));
  }

}
