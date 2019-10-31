/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.From26To27;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom26To27;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UpgradePrivateAlertArchiveIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.6.0";

  private static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  private static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex();

  private static final String ALERT_ARCHIVE_NAME = "Alert Archive";
  private static final int EXPECTED_NUMBER_OF_REPORTS = 5;
  private static final int EXPECTED_NUMBER_OF_PRIVATE_REPORTS_WITH_ALERTS = 2;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    for (StrictIndexMappingCreator index : ALL_INDICES) {
      createAndPopulateOptimizeIndexWithTypeAndVersion(
        index,
        index.getIndexName(),
        index.getVersion() - 1
      );
    }
    setMetadataIndexVersionWithType(FROM_VERSION, METADATA_INDEX.getIndexName());

    executeBulk("steps/report_data/26-single-decision-report-bulk");
    executeBulk("steps/report_data/26-single-process-report-bulk");
    executeBulk("steps/alert_data/26-alert-bulk");
  }

  @Test
  public void movePrivateReportsWithAlertsToArchive() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();
    List<ReportDefinitionDto> allReports = getAllReports();
    Map<Optional<BaseCollectionDefinitionDto>, List<ReportDefinitionDto>> collectionMap = getCollectionToReportMap(
      allReports);
    Optional<BaseCollectionDefinitionDto> alertArchive = collectionMap.keySet()
      .stream()
      .filter(coll -> coll.isPresent() && coll.get().getName().equals(ALERT_ARCHIVE_NAME))
      .findFirst().orElse(Optional.empty());

    // then
    assertThat(allReports.size(), is(EXPECTED_NUMBER_OF_REPORTS));
    assertThat(alertArchive.isPresent(), is(true));
    assertThat(collectionMap.get(alertArchive).size(), is(EXPECTED_NUMBER_OF_PRIVATE_REPORTS_WITH_ALERTS));
  }

  private Map<Optional<BaseCollectionDefinitionDto>, List<ReportDefinitionDto>> getCollectionToReportMap(List<ReportDefinitionDto> allReports) {
    List<BaseCollectionDefinitionDto> allCollections = getAllCollections();
    return allReports.stream()
      .collect(groupingBy(reportDto -> getCollectionDtoWithId(allCollections, reportDto.getCollectionId())));
  }

  private Optional<BaseCollectionDefinitionDto> getCollectionDtoWithId(
    final List<BaseCollectionDefinitionDto> collections,
    final String id) {
    return id == null
      ? Optional.empty()
      : collections.stream()
      .filter(collectionDto -> collectionDto.getId().equals(id))
      .findFirst();
  }

  private List<ReportDefinitionDto> getAllReports() {
    List<ReportDefinitionDto> allReports = new ArrayList<>();
    allReports.addAll(getAllReports(
      SINGLE_PROCESS_REPORT_INDEX.getIndexName(),
      SingleProcessReportDefinitionDto.class
    ));
    allReports.addAll(getAllReports(
      SINGLE_DECISION_REPORT_INDEX.getIndexName(),
      SingleDecisionReportDefinitionDto.class
    ));
    return allReports;
  }

  @SneakyThrows
  private <T extends ReportDefinitionDto> List<T> getAllReports(String reportEsType, Class<T> returnClass) {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(reportEsType).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), returnClass
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(toList());
  }

  @SneakyThrows
  private List<BaseCollectionDefinitionDto> getAllCollections() {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(COLLECTION_INDEX_NAME).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), BaseCollectionDefinitionDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(toList());
  }
}
