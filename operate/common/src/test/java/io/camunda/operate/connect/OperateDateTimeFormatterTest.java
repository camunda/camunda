/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.config.operate.OperateElasticsearchProperties;
import io.camunda.config.operate.OperateOpensearchProperties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.conditions.DatabaseInfo;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OperateDateTimeFormatterTest {

  private static final String RFC1339_DATETIMESTRING = "2024-02-15T22:40:10.834+00:00";
  private static final String DEFAULT_DATETIMESTRING = "2024-02-15T22:40:10.834+0000";

  @Mock private OperateProperties mockOperateProperties;

  @Mock private OperateElasticsearchProperties mockElasticsearchProperties;

  @Mock private OperateOpensearchProperties mockOpensearchProperties;

  @Mock private DatabaseInfo mockDatabaseInfo;

  private OperateDateTimeFormatter underTest;

  @Test
  public void testElasticsearchConfig() {
    when(mockDatabaseInfo.isOpensearchDb()).thenReturn(false);
    when(mockOperateProperties.isRfc3339ApiDateFormat()).thenReturn(false);
    when(mockOperateProperties.getElasticsearch()).thenReturn(mockElasticsearchProperties);
    when(mockElasticsearchProperties.getDateFormat())
        .thenReturn(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    underTest = new OperateDateTimeFormatter(mockOperateProperties, mockDatabaseInfo);

    verify(mockOperateProperties, times(1)).getElasticsearch();
    verify(mockElasticsearchProperties, times(1)).getDateFormat();
    verifyNoInteractions(mockOpensearchProperties);
  }

  @Test
  public void testOpensearchConfig() {
    when(mockDatabaseInfo.isOpensearchDb()).thenReturn(true);
    when(mockOperateProperties.isRfc3339ApiDateFormat()).thenReturn(false);
    when(mockOperateProperties.getOpensearch()).thenReturn(mockOpensearchProperties);
    when(mockOpensearchProperties.getDateFormat())
        .thenReturn(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    underTest = new OperateDateTimeFormatter(mockOperateProperties, mockDatabaseInfo);

    verify(mockOperateProperties, times(1)).getOpensearch();
    verify(mockOpensearchProperties, times(1)).getDateFormat();
    verifyNoInteractions(mockElasticsearchProperties);
  }

  @Test
  public void testRfc3339Config() {
    when(mockDatabaseInfo.isOpensearchDb()).thenReturn(false);
    when(mockOperateProperties.isRfc3339ApiDateFormat()).thenReturn(true);
    when(mockOperateProperties.getElasticsearch()).thenReturn(mockElasticsearchProperties);
    when(mockElasticsearchProperties.getDateFormat())
        .thenReturn(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    underTest = new OperateDateTimeFormatter(mockOperateProperties, mockDatabaseInfo);

    // Validate the datetime strings were set correctly
    assertThat(underTest.getGeneralDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);
    assertThat(underTest.getApiDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.RFC3339_DATE_FORMAT);

    // Validate the datetime formatters were created correctly
    assertThat(underTest.getGeneralDateTimeFormatter().parse(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.getGeneralDateTimeFormatter().parse(RFC1339_DATETIMESTRING));
    assertThat(underTest.getApiDateTimeFormatter().parse(RFC1339_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.getApiDateTimeFormatter().parse(DEFAULT_DATETIMESTRING));

    // Validate the parse functions
    assertThat(underTest.parseGeneralDateTime(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class, () -> underTest.parseGeneralDateTime(RFC1339_DATETIMESTRING));
    assertThat(underTest.parseApiDateTime(RFC1339_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class, () -> underTest.parseApiDateTime(DEFAULT_DATETIMESTRING));

    // Validate the convert function
    assertThat(underTest.convertGeneralToApiDateTime(DEFAULT_DATETIMESTRING))
        .isEqualTo(RFC1339_DATETIMESTRING);
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.convertGeneralToApiDateTime(RFC1339_DATETIMESTRING));
  }

  @Test
  public void testDefaultConfig() {
    when(mockDatabaseInfo.isOpensearchDb()).thenReturn(false);
    when(mockOperateProperties.isRfc3339ApiDateFormat()).thenReturn(false);
    when(mockOperateProperties.getElasticsearch()).thenReturn(mockElasticsearchProperties);
    when(mockElasticsearchProperties.getDateFormat())
        .thenReturn(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    underTest = new OperateDateTimeFormatter(mockOperateProperties, mockDatabaseInfo);

    assertThat(underTest.getApiDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    // Validate the datetime strings were set correctly
    assertThat(underTest.getGeneralDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);
    assertThat(underTest.getApiDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    // Validate the datetime formatters were created correctly
    assertThat(underTest.getGeneralDateTimeFormatter().parse(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.getGeneralDateTimeFormatter().parse(RFC1339_DATETIMESTRING));
    assertThat(underTest.getApiDateTimeFormatter().parse(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.getApiDateTimeFormatter().parse(RFC1339_DATETIMESTRING));

    // Validate the parse functions
    assertThat(underTest.parseGeneralDateTime(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class, () -> underTest.parseGeneralDateTime(RFC1339_DATETIMESTRING));
    assertThat(underTest.parseApiDateTime(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class, () -> underTest.parseApiDateTime(RFC1339_DATETIMESTRING));

    // Validate the convert function
    assertThat(underTest.convertGeneralToApiDateTime(DEFAULT_DATETIMESTRING))
        .isEqualTo(DEFAULT_DATETIMESTRING);
  }
}
