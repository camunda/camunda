/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.From26To27;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom26To27;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class DeleteScrollBasedIndexDocumentsIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.6.0";

  private static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  private static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex();
  private static final ImportIndexIndex IMPORT_INDEX_INDEX = new ImportIndexIndex();

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      IMPORT_INDEX_INDEX
    ));

    setMetadataIndexVersion(FROM_VERSION);

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
      "decisionDefinitionXmlImportIndex-camunda-bpm")));
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
      }));
  }

}