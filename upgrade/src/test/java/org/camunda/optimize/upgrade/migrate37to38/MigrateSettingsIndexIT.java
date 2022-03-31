/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate37to38;

import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.es.schema.index.SettingsIndex;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateSettingsIndexIT extends AbstractUpgrade37IT {

  @Test
  public void migrateSettingsIndex() {
    // given
    executeBulk("steps/3.7/settingsIndex/37-settings-index.json");

    // when
    performUpgrade();

    // then
    SearchHit[] migratedInstances = getAllDocumentsOfIndex(
      new SettingsIndex().getIndexName()
    );
    assertThat(migratedInstances)
      .singleElement()
      .satisfies(doc -> assertThat(doc.getSourceAsMap())
        .containsEntry(SettingsResponseDto.Fields.sharingEnabled.name(),null)
        .containsEntry(SettingsResponseDto.Fields.lastModified.name(),"2022-03-24T17:33:18.974+0200")
        .containsEntry(SettingsResponseDto.Fields.lastModifier.name(),"kermit")
        .containsEntry(SettingsResponseDto.Fields.metadataTelemetryEnabled.name(),true)
      );
  }
}
