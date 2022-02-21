/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate37to38;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.datasource.EventsDataSourceDto;
import org.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ENGINE_ALIAS_OPTIMIZE;

public class MigrateTimestampBasedIndexIT extends AbstractUpgrade37IT {

  @Test
  public void convertEngineFieldToDataSource() {
    // given
    executeBulk("steps/3.7/timestampimportindex/37-timestamp-import-index.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(
      new TimestampBasedImportIndex().getIndexName()
    ))
      .hasSize(7)
      .allSatisfy(doc -> assertThat(doc.getSourceAsMap()).doesNotContainKey("engine"));
    assertThat(getAllDocumentsOfIndexAs(
      new TimestampBasedImportIndex().getIndexName(),
      TimestampBasedImportIndexDto.class
    ))
      .hasSize(7)
      .extracting(
        TimestampBasedImportIndexDto::getEsTypeIndexRefersTo,
        ImportIndexDto::getDataSource,
        TimestampBasedImportIndexDto::getEngine
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("variableUpdateImportIndex", new EngineDataSourceDto("firstEngine"), "firstEngine"),
        Tuple.tuple("userOperationLogImportIndex", new EngineDataSourceDto("firstEngine"), "firstEngine"),
        Tuple.tuple(
          "userOperationLogImportIndex",
          new EngineDataSourceDto("secondEngine"),
          "secondEngine"
        ),
        Tuple.tuple(
          "eventStateProcessing-external",
          new EventsDataSourceDto(ENGINE_ALIAS_OPTIMIZE),
          ENGINE_ALIAS_OPTIMIZE
        ),
        Tuple.tuple(
          "eventStateProcessing-mycamundaprocess2",
          new EventsDataSourceDto(ENGINE_ALIAS_OPTIMIZE),
          ENGINE_ALIAS_OPTIMIZE
        ),
        Tuple.tuple(
          "eventStateProcessing-mycamundaprocess1",
          new EventsDataSourceDto(ENGINE_ALIAS_OPTIMIZE),
          ENGINE_ALIAS_OPTIMIZE
        ),
        Tuple.tuple(
          "externalVariableUpdateImportIndex",
          new IngestedDataSourceDto(ENGINE_ALIAS_OPTIMIZE),
          ENGINE_ALIAS_OPTIMIZE
        )
      );
  }

}
