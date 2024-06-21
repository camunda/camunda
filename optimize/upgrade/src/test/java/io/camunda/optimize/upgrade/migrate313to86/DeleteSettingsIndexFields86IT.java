/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.db.DatabaseConstants;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class DeleteSettingsIndexFields86IT extends AbstractUpgrade86IT {

  @Test
  public void deleteTelemetryFieldAndLastModifierFieldsFromSettingIndex() {
    // given pre-upgrade
    executeBulk("steps/3.13/313-settings-data.json");
    assertThat(getAllDocumentsOfIndex(DatabaseConstants.SETTINGS_INDEX_NAME))
        .singleElement()
        .satisfies(
            hit -> {
              Map<String, Object> fields = hit.getSourceAsMap();
              assertThat(fields)
                  .hasSize(4)
                  .containsKeys("metadataTelemetryEnabled", "lastModifier");
            });

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(DatabaseConstants.SETTINGS_INDEX_NAME))
        .singleElement()
        .satisfies(
            hit -> {
              Map<String, Object> fields = hit.getSourceAsMap();
              assertThat(fields)
                  .hasSize(2)
                  .doesNotContainKeys("metadataTelemetryEnabled", "lastModifier");
            });
  }
}
