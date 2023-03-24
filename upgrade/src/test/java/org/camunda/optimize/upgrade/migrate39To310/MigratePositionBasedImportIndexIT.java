/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39To310;

import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.upgrade.migrate39To310.indices.PositionBasedImportIndexV2;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MigratePositionBasedImportIndexIT extends AbstractUpgrade310IT {

  @Test
  public void addZeebeRecordSequenceFields() {
    // given
    executeBulk("steps/3.9/importIndex/39-position-based-import-index.json");

    // when
    performUpgrade();

    // then
    final SearchHit[] positionBasedImportIndices = getAllDocumentsOfIndex(new PositionBasedImportIndexV2().getIndexName());
    assertThat(positionBasedImportIndices)
      .hasSize(2)
      .allSatisfy(doc -> assertThat(doc.getSourceAsMap())
        .containsAllEntriesOf(Map.of(
          PositionBasedImportIndexDto.Fields.sequenceOfLastEntity, 0,
          PositionBasedImportIndexDto.Fields.hasSeenSequenceField, false
        )));
  }
}
