/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.enties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.camunda.webapps.schema.entities.operate.ListenerEventType;
import org.junit.jupiter.api.Test;

public class ListenerEventTypeTest {

  @Test
  public void convertStatesFromZeebeJobs() {
    final ListenerEventType actual1 = ListenerEventType.fromZeebeListenerEventType("START");
    assertEquals(actual1, ListenerEventType.START);
    final ListenerEventType actual2 = ListenerEventType.fromZeebeListenerEventType("TEST");
    assertEquals(actual2, ListenerEventType.UNSPECIFIED);
    final ListenerEventType actual3 = ListenerEventType.fromZeebeListenerEventType(null);
    assertEquals(actual3, ListenerEventType.UNSPECIFIED);
  }
}
