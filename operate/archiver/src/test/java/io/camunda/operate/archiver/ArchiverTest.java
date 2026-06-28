/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ArchiverTest {

  @InjectMocks private Archiver underTest;
  @Mock private OperateProperties operateProperties;
  @Mock private ArchiverProperties archiverProperties;

  @Test
  public void shouldFailStartupWhenDateFormatIsTooCoarseForInterval() {
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(archiverProperties.isRolloverEnabled()).thenReturn(true);
    // sub-day interval with a day-only format -> bucket end collapses, archiving would stall
    when(archiverProperties.getRolloverInterval()).thenReturn("4h");
    when(archiverProperties.getElsRolloverDateFormat()).thenReturn("date");

    assertThatThrownBy(() -> underTest.startArchiving())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldNotValidateWhenRolloverDisabled() {
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(archiverProperties.isRolloverEnabled()).thenReturn(false);
    // an invalid format/interval combination is irrelevant when rollover is disabled
    lenient().when(archiverProperties.getRolloverInterval()).thenReturn("4h");
    lenient().when(archiverProperties.getElsRolloverDateFormat()).thenReturn("date");

    // no archiving started, no validation performed -> no exception
    underTest.startArchiving();
  }
}
