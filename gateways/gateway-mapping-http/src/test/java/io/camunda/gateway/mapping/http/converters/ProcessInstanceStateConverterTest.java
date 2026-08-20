/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.ProcessInstanceStateEnum;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import org.junit.jupiter.api.Test;

class ProcessInstanceStateConverterTest {

  @Test
  void shouldConvertActiveCompletedAndSuspendedDirectly() {
    assertThat(ProcessInstanceStateConverter.toInternalState(ProcessInstanceStateEnum.ACTIVE))
        .isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(ProcessInstanceStateConverter.toInternalState(ProcessInstanceStateEnum.COMPLETED))
        .isEqualTo(ProcessInstanceState.COMPLETED);
    assertThat(ProcessInstanceStateConverter.toInternalState(ProcessInstanceStateEnum.SUSPENDED))
        .isEqualTo(ProcessInstanceState.SUSPENDED);
  }

  @Test
  void shouldConvertTerminatedToCanceled() {
    assertThat(ProcessInstanceStateConverter.toInternalState(ProcessInstanceStateEnum.TERMINATED))
        .isEqualTo(ProcessInstanceState.CANCELED);
  }

  @Test
  void shouldConvertNullToNull() {
    assertThat(ProcessInstanceStateConverter.toInternalState(null)).isNull();
  }
}
