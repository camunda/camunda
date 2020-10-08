/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate31To32;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom31To32;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_AUTOMATIC;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class CustomBucketMigrationIT extends AbstractUpgrade31IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.1/reports/31-process-report-bulk");
  }


  @Test
  @SuppressWarnings("unchecked")
  public void migrateProcessReport__distributedByCustomBucketAndDateUnitArePresent() throws IOException {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    Arrays.asList(getAllSingleProcessReports())
      .forEach(report -> {
        final Map<String, Object> data = (Map<String, Object>) report.getSourceAsMap()
          .get(AbstractReportIndex.DATA);
        final Map<String, Object> configuration =
          (Map<String, Object>) data.get(SingleReportDataDto.Fields.configuration.name());
        final Map<String, Object> distributeByCustomBucket =
          (Map<String, Object>) configuration.get(SingleReportConfigurationDto.Fields.distributeByCustomBucket.name());

        assertDefaultCustomBucketValues(distributeByCustomBucket);

        assertThat(configuration).containsEntry(
          SingleReportConfigurationDto.Fields.distributeByDateVariableUnit.name(),
          DATE_UNIT_AUTOMATIC
        );
      });
  }

  public void assertDefaultCustomBucketValues(Map<String, Object> distributeByCustomBucket) {
    CustomBucketDto defaultCustomBucketDto = CustomBucketDto.builder().build();

    assertThat(distributeByCustomBucket).containsEntry(
      CustomBucketDto.Fields.active.name(),
      defaultCustomBucketDto.isActive()
    );
    assertThat(distributeByCustomBucket).containsEntry(
      CustomBucketDto.Fields.baseline.name(),
      defaultCustomBucketDto.getBaseline().toString()
    );
    assertThat(distributeByCustomBucket).containsEntry(
      CustomBucketDto.Fields.baselineUnit.name(),
      defaultCustomBucketDto.getBaselineUnit()
    );
    assertThat(distributeByCustomBucket).containsEntry(
      CustomBucketDto.Fields.bucketSize.name(),
      defaultCustomBucketDto.getBucketSize().toString()
    );
    assertThat(distributeByCustomBucket).containsEntry(
      CustomBucketDto.Fields.bucketSizeUnit.name(),
      defaultCustomBucketDto.getBucketSizeUnit()
    );
  }

  private SearchHit[] getAllSingleProcessReports() throws IOException {
    return getAllDocumentsOfIndex(SINGLE_PROCESS_REPORT_INDEX_NAME);
  }
}
