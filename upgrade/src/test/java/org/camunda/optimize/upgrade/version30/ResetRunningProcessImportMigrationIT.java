/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
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

public class ResetRunningProcessImportMigrationIT extends AbstractUpgrade30IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

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

    // when
    upgradePlan.execute();
    List<TimestampBasedImportIndexDto> timestampBasedImportIndexDtos =
      getAllDocumentsOfIndexAs(TIMESTAMP_BASED_IMPORT_INDEX_NAME, TimestampBasedImportIndexDto.class);

    // then
    assertThat(timestampBasedImportIndexDtos.size()).isEqualTo(2);
    assertThat(timestampBasedImportIndexDtos)
      .filteredOn(indexDto -> indexDto.getEsTypeIndexRefersTo().equals("runningProcessInstanceImportIndex"))
      .allMatch(indexDto -> indexDto.getTimestampOfLastEntity().isEqual(BEGINNING_OF_TIME));
    assertThat(timestampBasedImportIndexDtos)
      .filteredOn(indexDto -> indexDto.getEsTypeIndexRefersTo().equals("otherIndex"))
      .allMatch(indexDto -> indexDto.getTimestampOfLastEntity().isEqual(otherIndexExpectedTimestamp));
  }
}
