/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate38to39;

import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateCollectionsIndexIT extends AbstractUpgrade38IT {

  @Test
  public void migrateCollectionsIndex() {
    // given
    executeBulk("steps/collectionsIndex/38-collections-index.json");

    // when
    performUpgrade();

    // then
    SearchHit[] migratedInstances = getAllDocumentsOfIndex(
      new CollectionIndex().getIndexName()
    );
    assertThat(migratedInstances)
      .hasSize(1)
      .allSatisfy(doc -> {
        final Map<String, Object> collectionAsMap = doc.getSourceAsMap();
        assertThat(collectionAsMap)
        .containsEntry(
          String.valueOf(BaseCollectionDefinitionDto.Fields.automaticallyCreated),
          false
        )
        .containsEntry(
          String.valueOf(BaseCollectionDefinitionDto.Fields.owner),
          "demo"
        )
        .containsEntry(
          String.valueOf(BaseCollectionDefinitionDto.Fields.lastModifier),
          "demo"
        )
        .containsEntry(
          String.valueOf(BaseCollectionDefinitionDto.Fields.created),
          "2022-06-08T15:44:30.479+0300"
        )
        .containsEntry(
          String.valueOf(BaseCollectionDefinitionDto.Fields.name),
          "Invoice Receipt"
        )
        .containsEntry(
          String.valueOf(BaseCollectionDefinitionDto.Fields.id),
          "invoice"
        )
        .containsEntry(
          String.valueOf(BaseCollectionDefinitionDto.Fields.lastModified),
          "2022-06-08T15:44:31.110+0300"
        );

        final HashMap<String, Object> data =
          (HashMap<String, Object>) collectionAsMap.getOrDefault(String.valueOf(BaseCollectionDefinitionDto.Fields.data),
                                                          new HashMap<>());
        assertThat(data)
          .hasSize(3)
          .containsKeys(
            "roles",
            "configuration",
            "scope");
    });
  }
}