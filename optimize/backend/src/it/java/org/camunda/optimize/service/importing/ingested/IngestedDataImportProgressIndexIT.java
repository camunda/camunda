/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.service.db.DatabaseConstants.ENGINE_ALIAS_OPTIMIZE;
import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static org.camunda.optimize.service.importing.ExternalVariableUpdateImportIndexHandler.EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;

import java.util.List;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import org.camunda.optimize.service.importing.ExternalVariableUpdateImportIndexHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class IngestedDataImportProgressIndexIT extends AbstractIngestedDataImportIT {

  @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
  @Test
  public void ingestedVariableDataImportProgressIsPersisted() {
    // given
    final ExternalProcessVariableRequestDto externalVariable =
        ingestionClient.createPrimitiveExternalVariable();
    ingestionClient.ingestVariables(List.of(externalVariable));

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();

    // then
    final long lastImportedExternalVariableTimestamp =
        databaseIntegrationTestExtension
            .getLastImportTimestampOfTimestampBasedImportIndex(
                EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID, ENGINE_ALIAS_OPTIMIZE)
            .toInstant()
            .toEpochMilli();

    assertThat(lastImportedExternalVariableTimestamp)
        .isEqualTo(getAllStoredExternalProcessVariables().get(0).getIngestionTimestamp());
  }

  @Test
  @SneakyThrows
  public void indexProgressIsRestoredAfterRestartOfOptimize() {
    // given
    startAndUseNewOptimizeInstance();
    final ExternalProcessVariableRequestDto externalVariable =
        ingestionClient.createPrimitiveExternalVariable();
    ingestionClient.ingestVariables(List.of(externalVariable));

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();

    final long lastImportedExternalVariableTimestamp =
        databaseIntegrationTestExtension
            .getLastImportTimestampOfTimestampBasedImportIndex(
                EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID, ENGINE_ALIAS_OPTIMIZE)
            .toInstant()
            .toEpochMilli();

    startAndUseNewOptimizeInstance();

    // then
    assertThat(
            embeddedOptimizeExtension
                .getIndexHandlerRegistry()
                .getExternalVariableUpdateImportIndexHandler())
        .extracting(ExternalVariableUpdateImportIndexHandler::getIndexStateDto)
        .extracting(TimestampBasedImportIndexDto::getTimestampOfLastEntity)
        .satisfies(
            timestampOfLastEntity ->
                assertThat(timestampOfLastEntity.toInstant().toEpochMilli())
                    .isEqualTo(lastImportedExternalVariableTimestamp));
  }

  private List<ExternalProcessVariableDto> getAllStoredExternalProcessVariables() {
    return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
        EXTERNAL_PROCESS_VARIABLE_INDEX_NAME, ExternalProcessVariableDto.class);
  }
}
