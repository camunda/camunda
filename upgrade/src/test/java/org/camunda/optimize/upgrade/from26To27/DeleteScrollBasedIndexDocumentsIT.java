/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.from26To27;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom26To27;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class DeleteScrollBasedIndexDocumentsIT extends AbstractUpgradeIT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    for (StrictIndexMappingCreator index : ALL_INDICES) {
      createOptimizeIndexWithTypeAndVersion(
        index,
        index.getIndexName(),
        index.getVersion() - 1
      );
    }
    setMetadataIndexVersionWithType(FROM_VERSION, METADATA_INDEX.getIndexName());

    executeBulk("steps/imports/26-import-indexes");
  }

  @Test
  public void onlyScrollBasedIndexDocumentsAreDeletedFromIndex() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    Map<String, AllEntitiesBasedImportIndexDto> idsToImportIndexDtos = getAllImportIdsAndImports();
    assertThat(idsToImportIndexDtos.size(), is(3));
    assertThat(idsToImportIndexDtos.keySet(), not(hasItems(
      "processDefinitionXmlImportIndex-camunda-bpm",
      "decisionDefinitionXmlImportIndex-camunda-bpm"
    )));
  }

  @SneakyThrows
  private Map<String, AllEntitiesBasedImportIndexDto> getAllImportIdsAndImports() {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(IMPORT_INDEX_INDEX.getIndexName()).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .collect(Collectors.toMap(
        SearchHit::getId,
        doc -> {
          try {
            return objectMapper.readValue(
              doc.getSourceAsString(), AllEntitiesBasedImportIndexDto.class
            );
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      ));
  }

}