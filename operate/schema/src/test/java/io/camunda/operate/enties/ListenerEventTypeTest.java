/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.enties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.camunda.webapps.schema.entities.listener.ListenerEventType;
import org.junit.jupiter.api.Test;

public class ListenerEventTypeTest {

  @Test
  public void convertStatesFromZeebeJobs() {
    final ListenerEventType actual1 = ListenerEventType.fromZeebeListenerEventType("START");
    assertEquals(actual1, ListenerEventType.START);
    final ListenerEventType actual2 = ListenerEventType.fromZeebeListenerEventType("END");
    assertEquals(actual2, ListenerEventType.END);
    final ListenerEventType actual3 = ListenerEventType.fromZeebeListenerEventType("COMPLETING");
    assertEquals(actual3, ListenerEventType.COMPLETING);
    final ListenerEventType actual4 = ListenerEventType.fromZeebeListenerEventType("ASSIGNING");
    assertEquals(actual4, ListenerEventType.ASSIGNING);
    final ListenerEventType actual5 = ListenerEventType.fromZeebeListenerEventType("TEST");
    assertEquals(actual5, ListenerEventType.UNKNOWN);
    final ListenerEventType actual6 = ListenerEventType.fromZeebeListenerEventType(null);
    assertEquals(actual6, ListenerEventType.UNSPECIFIED);
    final ListenerEventType actual7 = ListenerEventType.fromZeebeListenerEventType("UPDATING");
    assertEquals(actual7, ListenerEventType.UPDATING);
  }
}
