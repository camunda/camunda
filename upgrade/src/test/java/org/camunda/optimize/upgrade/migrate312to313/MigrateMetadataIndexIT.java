/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate312to313;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import lombok.SneakyThrows;
import org.camunda.optimize.service.db.es.schema.index.MetadataIndexES;
import org.camunda.optimize.service.util.configuration.OptimizeProfile;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

public class MigrateMetadataIndexIT extends AbstractUpgrade313IT {

  @SneakyThrows
  @Test
  public void addMetadataOptimizeProfileFieldForC7() {
    // given
    // metadata index created in env setup

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(new MetadataIndexES().getIndexName()))
        .singleElement()
        .satisfies(doc -> checkMetadata(doc, OptimizeProfile.PLATFORM));
  }

  @SneakyThrows
  @ParameterizedTest
  @ValueSource(strings = {""})
  @NullSource
  public void addMetadataOptimizeProfileFieldForC8SelfManaged(final String testClientId) {
    // given
    executeBulk("steps/3.13/metadata/position-based-import-data.json");
    configurationService
        .getAuthConfiguration()
        .getCloudAuthConfiguration()
        .setClientId(testClientId);

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(new MetadataIndexES().getIndexName()))
        .singleElement()
        .satisfies(doc -> checkMetadata(doc, OptimizeProfile.CCSM));
  }

  @SneakyThrows
  @Test
  public void addMetadataOptimizeProfileFieldForC8Cloud() {
    // given
    executeBulk("steps/3.13/metadata/position-based-import-data.json");
    configurationService
        .getAuthConfiguration()
        .getCloudAuthConfiguration()
        .setClientId("aClientId");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(new MetadataIndexES().getIndexName()))
        .singleElement()
        .satisfies(doc -> checkMetadata(doc, OptimizeProfile.CLOUD));
  }

  private void checkMetadata(final SearchHit doc, final OptimizeProfile expectedProfile) {
    final Map<String, Object> sourceAsMap = doc.getSourceAsMap();
    assertThat(sourceAsMap).containsEntry("optimizeProfile", expectedProfile.getId());
    assertThat(sourceAsMap).containsKey("installationId");
    assertThat(sourceAsMap).containsEntry("schemaVersion", "3.13.0");
    assertThat(doc.getId()).isEqualTo("1");
    assertThat(sourceAsMap).hasSize(3);
  }
}
