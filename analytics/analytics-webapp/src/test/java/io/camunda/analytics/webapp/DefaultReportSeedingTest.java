/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.serving.catalog.StandardReports;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The default saved reports are seeded create-if-absent by name: restarts never duplicate them, a
 * user-created report of another name is untouched, and the seeded sources reference datasets the
 * standard catalog actually declares.
 */
final class DefaultReportSeedingTest {

  private Fixture fixture;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldSeedDefaultReportsIdempotently() {
    // given a metadata plane bootstrapped with the standard datasets but no reports yet
    assertThat(fixture.metadataStore().reportSpecStore().search()).isEmpty();

    // when the defaults are seeded twice (two restarts)
    StandardReports.seedDefaults(fixture.metadataStore());
    StandardReports.seedDefaults(fixture.metadataStore());

    // then each default exists exactly once
    final List<ReportDefinition> reports = fixture.metadataStore().reportSpecStore().search();
    assertThat(reports)
        .extracting(ReportDefinition::name)
        .containsExactlyInAnyOrder("Duration by region", "Throughput by process");

    // and every seeded source reads a dataset (and meters) the standard catalog declares
    for (final ReportDefinition report : reports) {
      report
          .sources()
          .forEach(
              source ->
                  assertThat(fixture.catalog().require(source.datasetName()).meters())
                      .extracting(compiled -> compiled.meterName())
                      .containsAll(source.meters()));
    }
  }
}
