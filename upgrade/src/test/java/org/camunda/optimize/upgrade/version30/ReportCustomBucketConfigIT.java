/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomNumberBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class ReportCustomBucketConfigIT extends AbstractUpgrade30IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.0/report_data/30-report-bulk");
  }

  @SneakyThrows
  @Test
  public void groupByDateVariableUnitSetToAutomatic() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    final SearchHit[] allReports =
      getAllDocumentsOfIndex(SINGLE_PROCESS_REPORT_INDEX_NAME, SINGLE_DECISION_REPORT_INDEX_NAME);

    // then
    assertThat(allReports).allSatisfy(this::assertDefaultGroupByDateVariableUnit);
  }

  @SneakyThrows
  @Test
  public void groupByNumberVariableCustomBucketHasDefaultValue() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    final SearchHit[] allReports =
      getAllDocumentsOfIndex(SINGLE_PROCESS_REPORT_INDEX_NAME, SINGLE_DECISION_REPORT_INDEX_NAME);

    // then
    assertThat(allReports).allSatisfy(this::assertDefaultGroupByNumberVariableBuckets);
  }

  @SuppressWarnings("unchecked")
  private void assertDefaultGroupByDateVariableUnit(final SearchHit document) {
    final Map<String, Object> data = (Map<String, Object>) document.getSourceAsMap()
      .get(AbstractReportIndex.DATA);
    final Map<String, Object> configuration =
      (Map<String, Object>) data.get(SingleReportDataDto.Fields.configuration.name());
    final String actualUnitName =
      (String) configuration.get(SingleReportConfigurationDto.Fields.groupByDateVariableUnit.name());
    assertThat(actualUnitName).isEqualTo(GroupByDateUnit.AUTOMATIC.toString());
  }

  @SuppressWarnings("unchecked")
  @SneakyThrows
  private void assertDefaultGroupByNumberVariableBuckets(final SearchHit document) {
    final CustomNumberBucketDto expectedCustomNumberBucketDto = new CustomNumberBucketDto();
    final Map<String, Object> data = (Map<String, Object>) document.getSourceAsMap()
      .get(AbstractReportIndex.DATA);
    final Map<String, Object> configuration =
      (Map<String, Object>) data.get(SingleReportDataDto.Fields.configuration.name());
    final Map<String, Object> actualCustomBucketField =
      (Map<String, Object>) configuration.get(SingleReportConfigurationDto.Fields.customNumberBucket.name());

    assertThat(actualCustomBucketField.get(CustomNumberBucketDto.Fields.active.name()))
      .isEqualTo(expectedCustomNumberBucketDto.isActive());
    assertThat(actualCustomBucketField.get(CustomNumberBucketDto.Fields.baseline.name()))
      .isEqualTo(expectedCustomNumberBucketDto.getBaseline().toString());
    assertThat(actualCustomBucketField.get(CustomNumberBucketDto.Fields.bucketSize.name()))
      .isEqualTo(expectedCustomNumberBucketDto.getBucketSize().toString());
  }
}
