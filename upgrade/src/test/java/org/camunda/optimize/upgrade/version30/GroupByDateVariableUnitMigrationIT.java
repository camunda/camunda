/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class GroupByDateVariableUnitMigrationIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "3.0.0";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      COMBINED_REPORT_INDEX,
      TIMESTAMP_BASED_IMPORT_INDEX,
      IMPORT_INDEX_INDEX
    ));
    setMetadataIndexVersion(FROM_VERSION);

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
}
