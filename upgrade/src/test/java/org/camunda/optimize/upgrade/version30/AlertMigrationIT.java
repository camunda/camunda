/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_INDEX_NAME;

public class AlertMigrationIT extends AbstractUpgradeIT {
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
      IMPORT_INDEX_INDEX,
      ALERT_INDEX
    ));
    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/3.0/report_data/30-alert-bulk");
  }

  @Test
  public void alertsAreMigratedNoDataLost() {
    // given
    final List<Object> alertsBeforeMigration = getAllAlerts();
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(getAllAlerts())
      .hasSize(1)
      .isEqualTo(alertsBeforeMigration);
  }

  @Test
  public void optimizeIsAbleToParseNewAlerts() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();
    final List<AlertDefinitionDto> allAlerts = getAllDocumentsOfIndexAs(
      new AlertIndex().getIndexName(),
      AlertDefinitionDto.class
    );

    // then
    assertThat(allAlerts)
      .hasSize(1)
      .first()
      .extracting(AlertDefinitionDto::getThreshold).isEqualTo(100.0);
  }

  private List<Object> getAllAlerts() {
    return getAllDocumentsOfIndexAs(ALERT_INDEX_NAME, Object.class);
  }

}
