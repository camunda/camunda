/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version27;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom27To30;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class UpgradeWithBooleanVariableFilterIT extends AbstractUpgradeIT {
  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(ALL_INDEXES);
    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/report_data/2.7/27-single-process-report-bulk");
  }

  @Test
  public void testBooleanVariableFilter() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom27To30().buildUpgradePlan();

    // when
    upgradePlan.execute();
    List<SingleProcessReportDefinitionDto> allProcessReports =
      getAllProcessReports(SINGLE_PROCESS_REPORT_INDEX.getIndexName());

    List<SingleProcessReportDefinitionDto> reportsWithFilter = allProcessReports.stream().filter(r -> !r.getData().getFilter().isEmpty())
      .collect(Collectors.toList());

    reportsWithFilter
    .forEach(r -> {
      assertThat(
        ((BooleanVariableFilterDataDto)r.getData().getFilter().get(0).getData()).getData().getValue(),
        is(true)
      );
    });
  }

}
