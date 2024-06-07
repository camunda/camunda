/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate313to314;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.junit.jupiter.api.Test;

public class DeleteSettingsIndexFields314IT extends AbstractUpgrade314IT {

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
