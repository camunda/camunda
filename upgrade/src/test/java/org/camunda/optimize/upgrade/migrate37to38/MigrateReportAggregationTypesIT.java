/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate37to38;

import com.google.common.collect.ImmutableSet;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.PERCENTILE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.SUM;

public class MigrateReportAggregationTypesIT extends AbstractUpgrade37IT {

  @SneakyThrows
  @Test
  public void MigrateReportAggregationTypes() {
    // given
    executeBulk("steps/3.7/report/37-process-reports-with-date-filters.json");
    executeBulk("steps/3.7/report/37-decision-reports.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndexAs(new SingleProcessReportIndex().getIndexName(), ReportDefinitionDto.class))
      .hasSize(4)
      .extracting(
        ReportDefinitionDto::getId,
        reportDef -> ((SingleReportDataDto) reportDef.getData()).getConfiguration().getAggregationTypes()
      )
      .containsExactly(
        Tuple.tuple(
          "report-1",
          ImmutableSet.of(
            new AggregationDto(MAX),
            new AggregationDto(MIN),
            // The median is migrated to a P50 aggregation
            new AggregationDto(PERCENTILE, 50.),
            new AggregationDto(AVERAGE),
            new AggregationDto(SUM)
          )
        ),
        // The median is migrated to a P50 aggregation
        Tuple.tuple("report-2", ImmutableSet.of(new AggregationDto(PERCENTILE, 50.))),
        Tuple.tuple("report-3", Collections.emptySet()),
        Tuple.tuple("report-4", Collections.emptySet())
      );
    assertThat(getAllDocumentsOfIndexAs(new SingleDecisionReportIndex().getIndexName(), ReportDefinitionDto.class))
      .hasSize(2)
      .extracting(
        ReportDefinitionDto::getId,
        reportDef -> ((SingleReportDataDto) reportDef.getData()).getConfiguration().getAggregationTypes()
      )
      .containsExactly(
        Tuple.tuple(
          "report-1",
          ImmutableSet.of(
            new AggregationDto(MAX),
            new AggregationDto(MIN),
            // The median is migrated to a P50 aggregation
            new AggregationDto(PERCENTILE, 50.),
            new AggregationDto(AVERAGE),
            new AggregationDto(SUM)
          )
        ),
        Tuple.tuple("report-2", Collections.emptySet())
      );
  }
}
