/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate37to38;

import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.index.index.PositionBasedImportIndex;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class MigratePositionBasedIndexIT extends AbstractUpgrade37IT {

  @Test
  public void migratePositionBasedImportIndex() {
    // given
    executeBulk("steps/3.7/positionimportindex/37-position-import-index.json");

    // when
    performUpgrade();

    // then timestamp is set to epoch and the datasource field name is renamed
    assertThat(getAllDocumentsOfIndex(
      new PositionBasedImportIndex().getIndexName()
    ))
      .hasSize(2)
      .allSatisfy(doc -> assertThat(doc.getSourceAsMap())
        .containsEntry(
          ImportIndexDto.Fields.timestampOfLastEntity,
          DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT)
            .format(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()))
        )
        .doesNotContainKey("dataSourceDto")
      );
    assertThat(getAllDocumentsOfIndexAs(
      new PositionBasedImportIndex().getIndexName(), PositionBasedImportIndexDto.class
    )).hasSize(2).allSatisfy(index -> assertThat(index.getDataSource()).isNotNull());
  }

}
