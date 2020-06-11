/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DeleteEventStateProcessingTimestampImportIndexDocumentsUpgradeIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "3.0.0";
  // This import is included in the bulk
  private static final String ACTIVITY_IMPORT_INDEX_DOC_ID = "activityImportIndex";

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

    executeBulk("steps/3.0/timestamp_based_imports/30-timestamp-based-imports-bulk");
  }

  @SneakyThrows
  @Test
  public void allCamundaTimestampImportIndexDocumentsAreRemoved() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();
    assertThat(getAllStoredTimestampImportDocs())
      .hasSize(4);

    // when
    upgradePlan.execute();

    // then the timestamp import index docs are deleted
    assertThat(getAllStoredTimestampImportDocs())
      .hasSize(1)
      .extracting(TimestampBasedImportIndexDto::getEsTypeIndexRefersTo)
      .containsExactlyInAnyOrder(ACTIVITY_IMPORT_INDEX_DOC_ID);
  }

  private List<TimestampBasedImportIndexDto> getAllStoredTimestampImportDocs() {
    return getAllDocumentsOfIndexAs(
      TIMESTAMP_BASED_IMPORT_INDEX.getIndexName(),
      TimestampBasedImportIndexDto.class
    );
  }

}
