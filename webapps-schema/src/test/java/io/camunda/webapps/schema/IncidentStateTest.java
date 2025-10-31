/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema;

import static org.assertj.core.api.Assertions.*;

import io.camunda.webapps.schema.entities.incident.IncidentState;
import org.junit.jupiter.api.Test;

public class IncidentStateTest {

  @Test
  void shouldCreateValidIncidentStateFromAllPossibleValues() {
    assertThat(IncidentState.createFrom("CREATED")).isEqualTo(IncidentState.ACTIVE);
    assertThat(IncidentState.createFrom("RESOLVE")).isEqualTo(IncidentState.ACTIVE);
    assertThat(IncidentState.createFrom("MIGRATED")).isEqualTo(IncidentState.MIGRATED);
    assertThat(IncidentState.createFrom("RESOLVED")).isEqualTo(IncidentState.RESOLVED);
  }

  @Test
  void shouldFailCreatingIncidentIntentWithNullSource() {
    assertThat(IncidentState.createFrom(null)).isNull();
  }

  @Test
  void shouldFailCreatingIncidentIntentWithInvalidSource() {
    assertThat(IncidentState.createFrom("UNKNOWN_INTENT")).isNull();
  }
}
