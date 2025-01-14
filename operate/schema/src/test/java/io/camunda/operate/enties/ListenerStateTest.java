/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.enties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.camunda.webapps.schema.entities.listener.ListenerState;
import org.junit.jupiter.api.Test;

public class ListenerStateTest {

  @Test
  public void convertStatesFromZeebeJobs() {
    final ListenerState actual1 = ListenerState.fromZeebeJobIntent("MIGRATED");
    assertEquals(actual1, ListenerState.ACTIVE);
    final ListenerState actual2 = ListenerState.fromZeebeJobIntent("FAILED");
    assertEquals(actual2, ListenerState.FAILED);
    final ListenerState actual3 = ListenerState.fromZeebeJobIntent("COMPLETED");
    assertEquals(actual3, ListenerState.COMPLETED);
    final ListenerState actual4 = ListenerState.fromZeebeJobIntent("TIMED_OUT");
    assertEquals(actual4, ListenerState.TIMED_OUT);
    final ListenerState actual5 = ListenerState.fromZeebeJobIntent("CANCELED");
    assertEquals(actual5, ListenerState.CANCELED);
    final ListenerState actual6 = ListenerState.fromZeebeJobIntent("ERROR_THROWN");
    assertEquals(actual6, ListenerState.FAILED);
    final ListenerState actual7 = ListenerState.fromZeebeJobIntent("TEST");
    assertEquals(actual7, ListenerState.UNKNOWN);
    final ListenerState actual8 = ListenerState.fromZeebeJobIntent(null);
    assertEquals(actual8, ListenerState.UNKNOWN);
  }
}
