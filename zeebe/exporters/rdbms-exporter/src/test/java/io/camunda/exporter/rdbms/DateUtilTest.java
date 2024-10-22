/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

public class DateUtilTest {

  @Test
  public void testToOffsetDateTimeFromLong() {
    // Arrange
    final Long timestamp = 1633046400000L;

    // Act
    final OffsetDateTime result = DateUtil.toOffsetDateTime(timestamp);

    // Assert
    assertThat(result).isEqualTo(OffsetDateTime.parse("2021-10-01T00:00:00Z"));
  }

  @Test
  public void testToOffsetDateTimeFromString() {
    // Given
    final String timestamp = "2021-10-01T00:00:00Z";

    // When
    final OffsetDateTime result = DateUtil.toOffsetDateTime(timestamp);

    // Then
    assertThat(result).isEqualTo(OffsetDateTime.parse("2021-10-01T00:00:00Z"));
  }

  @Test
  public void testToOffsetDateTimeFromStringWithFormatter() {
    // Given
    final String timestamp = "2021-10-01T00:00:00+00:00";
    final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    // When
    final OffsetDateTime result = DateUtil.toOffsetDateTime(timestamp, formatter);

    // Then
    assertThat(result).isEqualTo(OffsetDateTime.parse("2021-10-01T00:00:00+00:00", formatter));
  }

  @Test
  public void testToOffsetDateTimeFromNullString() {
    // When
    final OffsetDateTime result = DateUtil.toOffsetDateTime((String) null);

    // Then
    assertNull(result);
  }

  @Test
  public void testToOffsetDateTimeFromBlankString() {
    // When
    final OffsetDateTime result = DateUtil.toOffsetDateTime("");

    // Then
    assertNull(result);
  }

  @Test
  public void testToOffsetDateTimeFromInvalidString() {
    // Given
    final String invalidTimestamp = "invalid-date";

    // When
    final OffsetDateTime result = DateUtil.toOffsetDateTime(invalidTimestamp);

    // Then
    assertNull(result);
  }
}
