/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler.BEGINNING_OF_TIME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;

public class ResetRunningProcessImportMigrationIT extends AbstractUpgradeIT {
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

    upgradeDependencies.getEsClient()
      .getHighLevelClient()
      .indices()
      .refresh(new RefreshRequest(), RequestOptions.DEFAULT);

    executeBulk("steps/timestamp_based_import_index/30-timestamp-based-import-index-bulk");
  }

  @Test
  public void runningProcessImportIndexTimestampHasBeenReset() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();
    final OffsetDateTime otherIndexExpectedTimestamp = OffsetDateTime.parse("2020-01-01T01:00+01:00");
    final OffsetDateTime instanceIndexExpectedTimestamp = BEGINNING_OF_TIME;

    // when
    upgradePlan.execute();
    List<TimestampBasedImportIndexDto> timestampBasedImportIndexDtos =
      getAllDocumentsOfIndex(TIMESTAMP_BASED_IMPORT_INDEX_NAME, TimestampBasedImportIndexDto.class);

    // then
    assertThat(timestampBasedImportIndexDtos.size()).isEqualTo(2);
    assertThat(timestampBasedImportIndexDtos)
      .filteredOn(indexDto -> indexDto.getEsTypeIndexRefersTo().equals("runningProcessInstanceImportIndex"))
      .allMatch(indexDto -> indexDto.getTimestampOfLastEntity().isEqual(instanceIndexExpectedTimestamp));
    assertThat(timestampBasedImportIndexDtos)
      .filteredOn(indexDto -> indexDto.getEsTypeIndexRefersTo().equals("otherIndex"))
      .allMatch(indexDto -> indexDto.getTimestampOfLastEntity().isEqual(otherIndexExpectedTimestamp));
  }
}
