/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.tasks.util.FormHistoricalArchiveDate;
import org.junit.jupiter.api.Test;

public class FromHistoricalArchiveDateIT {

  @Test
  void shouldReturnTheLatestDateDuringInitialization() {
    // should return the end date during initialization:
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-09-01", "", "1d", "yyyy-MM-dd"))
        .isEqualTo("2023-09-01");
  }

  @Test
  void shouldReturnTheHistoricalDates() {
    // should return the historical date since it has not passed the
    // rollover yet:
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-09-03-22", "2023-09-03-20", "2h", "yyyy-MM-dd-HH"))
        .isEqualTo("2023-09-03-20");
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-09-02", "2023-09-01", "1d", "yyyy-MM-dd"))
        .isEqualTo("2023-09-01");
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-09-03", "2023-09-01", "2d", "yyyy-MM-dd"))
        .isEqualTo("2023-09-01");
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-09-13", "2023-09-01", "2w", "yyyy-MM-dd"))
        .isEqualTo("2023-09-01");
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-09-29", "2023-09-01", "1M", "yyyy-MM-dd"))
        .isEqualTo("2023-09-01");
  }

  @Test
  void shouldReturnTheLatestDate() {

    // should return the end date since it has passed the rollover period:
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-09-03-22", "2023-09-03-20", "1h", "yyyy-MM-dd-HH"))
        .isEqualTo("2023-09-03-22");
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-09-03", "2023-09-01", "1d", "yyyy-MM-dd"))
        .isEqualTo("2023-09-03");
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-09-09", "2023-09-01", "1w", "yyyy-MM-dd"))
        .isEqualTo("2023-09-09");
    assertThat(
            FormHistoricalArchiveDate.getHistoricalArchiverDate(
                "2023-10-02", "2023-09-01", "1M", "yyyy-MM-dd"))
        .isEqualTo("2023-10-02");
  }
}
