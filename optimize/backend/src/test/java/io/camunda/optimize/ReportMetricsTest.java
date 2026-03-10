/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.ReportMetrics.REPORT_CATEGORY_TAG;
import static io.camunda.optimize.ReportMetrics.REPORT_TYPE_TAG;
import static io.camunda.optimize.ReportMetrics.SAVED_TAG;
import static io.camunda.optimize.ReportMetrics.STATUS_TAG;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

class ReportMetricsTest {

  @Test
  void shouldBuildTagsForProcessReport() {
    // given
    final SingleProcessReportDefinitionRequestDto report =
        new SingleProcessReportDefinitionRequestDto();
    report.setId("report-123");

    // when
    final Tags tags = ReportMetrics.buildTags(report, "success");

    // then
    assertThat(tags.stream().filter(t -> t.getKey().equals(REPORT_CATEGORY_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("single"));
    assertThat(tags.stream().filter(t -> t.getKey().equals(REPORT_TYPE_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("process"));
    assertThat(tags.stream().filter(t -> t.getKey().equals(SAVED_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("true"));
    assertThat(tags.stream().filter(t -> t.getKey().equals(STATUS_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("success"));
  }

  @Test
  void shouldBuildTagsForDecisionReport() {
    // given
    final SingleDecisionReportDefinitionRequestDto report =
        new SingleDecisionReportDefinitionRequestDto();
    report.setId("report-456");

    // when
    final Tags tags = ReportMetrics.buildTags(report, "success");

    // then
    assertThat(tags.stream().filter(t -> t.getKey().equals(REPORT_TYPE_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("decision"));
  }

  @Test
  void shouldBuildTagsForUnsavedReport() {
    // given
    final SingleProcessReportDefinitionRequestDto report =
        new SingleProcessReportDefinitionRequestDto();
    // id is null - unsaved report

    // when
    final Tags tags = ReportMetrics.buildTags(report, "success");

    // then
    assertThat(tags.stream().filter(t -> t.getKey().equals(SAVED_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("false"));
  }

  @Test
  void shouldBuildTagsWithErrorStatus() {
    // given
    final SingleProcessReportDefinitionRequestDto report =
        new SingleProcessReportDefinitionRequestDto();

    // when
    final Tags tags = ReportMetrics.buildTags(report, "error");

    // then
    assertThat(tags.stream().filter(t -> t.getKey().equals(STATUS_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("error"));
  }

  @Test
  void shouldBuildFallbackTagsWithUnknownValues() {
    // when
    final Tags tags = ReportMetrics.buildFallbackTags("error");

    // then
    assertThat(tags.stream().filter(t -> t.getKey().equals(REPORT_CATEGORY_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("unknown"));
    assertThat(tags.stream().filter(t -> t.getKey().equals(REPORT_TYPE_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("unknown"));
    assertThat(tags.stream().filter(t -> t.getKey().equals(SAVED_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("false"));
    assertThat(tags.stream().filter(t -> t.getKey().equals(STATUS_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("error"));
  }

  @Test
  void shouldBuildTagsForCombinedReport() {
    // given
    final CombinedReportDefinitionRequestDto report = new CombinedReportDefinitionRequestDto();
    report.setId("combined-123");

    // when
    final Tags tags = ReportMetrics.buildTags(report, "success");

    // then
    assertThat(tags.stream().filter(t -> t.getKey().equals(REPORT_CATEGORY_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("combined"));
    assertThat(tags.stream().filter(t -> t.getKey().equals(REPORT_TYPE_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("process"));
    assertThat(tags.stream().filter(t -> t.getKey().equals(SAVED_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("true"));
    assertThat(tags.stream().filter(t -> t.getKey().equals(STATUS_TAG)).findFirst())
        .hasValueSatisfying(tag -> assertThat(tag.getValue()).isEqualTo("success"));
  }
}
