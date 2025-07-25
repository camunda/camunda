/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.entities;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.listener.ListenerState;
import org.junit.jupiter.api.Test;

public class ListenerStateTest {

  @Test
  public void convertStatesFromZeebeJobs() {
    final ListenerState actual1 = ListenerState.fromZeebeJobIntent("MIGRATED");
    assertThat(ListenerState.ACTIVE).isEqualTo(actual1);
    final ListenerState actual2 = ListenerState.fromZeebeJobIntent("FAILED");
    assertThat(ListenerState.FAILED).isEqualTo(actual2);
    final ListenerState actual3 = ListenerState.fromZeebeJobIntent("COMPLETED");
    assertThat(ListenerState.COMPLETED).isEqualTo(actual3);
    final ListenerState actual4 = ListenerState.fromZeebeJobIntent("TIMED_OUT");
    assertThat(ListenerState.TIMED_OUT).isEqualTo(actual4);
    final ListenerState actual5 = ListenerState.fromZeebeJobIntent("CANCELED");
    assertThat(ListenerState.CANCELED).isEqualTo(actual5);
    final ListenerState actual6 = ListenerState.fromZeebeJobIntent("ERROR_THROWN");
    assertThat(ListenerState.FAILED).isEqualTo(actual6);
    final ListenerState actual7 = ListenerState.fromZeebeJobIntent("TEST");
    assertThat(ListenerState.UNKNOWN).isEqualTo(actual7);
    final ListenerState actual8 = ListenerState.fromZeebeJobIntent(null);
    assertThat(ListenerState.UNKNOWN).isEqualTo(actual8);
  }
}
