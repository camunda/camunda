/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version25;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom25To26;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.version25.indexes.Version25CollectionIndex;
import org.camunda.optimize.upgrade.version25.indexes.Version25ProcessInstanceIndex;
import org.camunda.optimize.upgrade.version25.util.Version25WriterUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.isIn;


public class UpgradeCollectionEntitiesIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.5.0";

  private static final DecisionDefinitionIndex DECISION_DEFINITION_INDEX_OBJECT = new DecisionDefinitionIndex();
  private static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  private static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex();
  private static final Version25CollectionIndex COLLECTION_INDEX = new Version25CollectionIndex();
  private static final Version25ProcessInstanceIndex PROCESS_INSTANCE_INDEX = new Version25ProcessInstanceIndex();
  private static final DashboardIndex DASHBOARD_INDEX = new DashboardIndex();
  private static final CombinedReportIndex COMBINED_REPORT_INDEX = new CombinedReportIndex();


  private Version25WriterUtil writerUtil;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    writerUtil = new Version25WriterUtil(objectMapper, prefixAwareClient);

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      DECISION_DEFINITION_INDEX_OBJECT,
      SINGLE_DECISION_REPORT_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      COLLECTION_INDEX,
      PROCESS_INSTANCE_INDEX,
      COMBINED_REPORT_INDEX,
      DASHBOARD_INDEX
    ));

    setMetadataIndexVersion(FROM_VERSION);
  }

  @Test
  public void collectionEntitiesAreRemovedFromCollections() {
    // given
    final String test = writerUtil.createSingleProcessReport("Test");
    final String testCollection = writerUtil.createCollection("TestCollection", Collections.singletonList(test));


    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final GetResponse collectionResponse = getDocument(testCollection, ElasticsearchConstants.COLLECTION_INDEX_NAME);
    assertThat(collectionResponse.isExists(), is(true));
    assertThat(collectionResponse.getSourceAsString(), not(containsString("entities")));
  }


  @Test
  public void processReportWithoutCollection_notChanged() {
    // given
    final String testReportId = writerUtil.createSingleProcessReport("Test");

    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportDefinitionDto report = getDocumentAsDto(
      testReportId,
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      ReportDefinitionDto.class
    );

    assertThat(report, is(notNullValue()));
    assertThat(report.getCollectionId(), is(nullValue()));
  }

  @Test
  public void processReportWithOneCollection_setsCollectionId() {
    // given
    final String test = writerUtil.createSingleProcessReport("Test");
    final String testCollection = writerUtil.createCollection("TestCollection", Collections.singletonList(test));


    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<ReportDefinitionDto>> collectionEntitiesAsMap = getAllCollectionEntitiesAsMap(
      ReportDefinitionDto.class,
      ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME
    );

    final List<ReportDefinitionDto> collectionReports = collectionEntitiesAsMap.get(testCollection);

    assertThat(collectionReports, is(notNullValue()));
    assertThat(collectionReports.size(), is(1));
    assertThat(collectionReports.get(0).getCollectionId(), is(testCollection));

    final List<ReportDefinitionDto> privateReports = collectionEntitiesAsMap.get("private");
    assertThat(privateReports, is(notNullValue()));
    assertThat(privateReports.size(), is(1));
    assertThat(privateReports.get(0).getCollectionId(), is(nullValue()));
  }

  @Test
  public void processReportWithSeveralCollections_copiesReports() {
    // given
    final String test = writerUtil.createSingleProcessReport("Test");
    final String testCollection1 = writerUtil.createCollection("TestCollection1", Collections.singletonList(test));
    final String testCollection2 = writerUtil.createCollection("TestCollection2", Collections.singletonList(test));
    final String testCollection3 = writerUtil.createCollection("TestCollection3", Collections.singletonList(test));

    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<ReportDefinitionDto> allReports = getAllDocumentsAsDto(
      ReportDefinitionDto.class,
      ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME
    );

    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();

    assertThat(allReports.size(), is(4));

    assertThat(collectionEntities.get(testCollection1).size(), is(1));
    assertThat(collectionEntities.get(testCollection2).size(), is(1));
    assertThat(collectionEntities.get(testCollection3).size(), is(1));

    assertSingleReportCopiesAreSame(
      (SingleProcessReportDefinitionDto) collectionEntities.get(testCollection1).get(0),
      (SingleProcessReportDefinitionDto) collectionEntities.get(testCollection2).get(0)
    );
  }

  @Test
  public void decisionReportWithOneCollection_setsCollectionId() {
    // given
    final String test = writerUtil.createSingleDecisionReport("Test");
    final String testCollection = writerUtil.createCollection("TestCollection", Collections.singletonList(test));


    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<ReportDefinitionDto>> collectionEntitiesAsMap = getAllCollectionEntitiesAsMap(
      ReportDefinitionDto.class,
      SINGLE_DECISION_REPORT_INDEX_NAME
    );

    final List<ReportDefinitionDto> collectionReports = collectionEntitiesAsMap.get(testCollection);

    assertThat(collectionReports, is(notNullValue()));
    assertThat(collectionReports.size(), is(1));
    assertThat(collectionReports.get(0).getCollectionId(), is(testCollection));

    final List<ReportDefinitionDto> privateReports = collectionEntitiesAsMap.get("private");
    assertThat(privateReports, is(notNullValue()));
    assertThat(privateReports.size(), is(1));
    assertThat(privateReports.get(0).getCollectionId(), is(nullValue()));
  }

  @Test
  public void decisionReportWithSeveralCollections_copiesReports() {
    // given
    final String test = writerUtil.createSingleDecisionReport("Test");
    final String testCollection1 = writerUtil.createCollection("TestCollection1", Collections.singletonList(test));
    final String testCollection2 = writerUtil.createCollection("TestCollection2", Collections.singletonList(test));
    final String testCollection3 = writerUtil.createCollection("TestCollection3", Collections.singletonList(test));

    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<ReportDefinitionDto> allReports = getAllDocumentsAsDto(
      ReportDefinitionDto.class,
      SINGLE_DECISION_REPORT_INDEX_NAME
    );

    assertThat(allReports.size(), is(4));

    assertThat(
      allReports.stream().filter(report -> testCollection1.equals(report.getCollectionId())).count(),
      is(1L)
    );
    assertThat(
      allReports.stream().filter(report -> testCollection2.equals(report.getCollectionId())).count(),
      is(1L)
    );
    assertThat(
      allReports.stream().filter(report -> testCollection3.equals(report.getCollectionId())).count(),
      is(1L)
    );
  }

  @Test
  public void combinedReports_copyChildReportsFromDifferentCollections() {
    // given
    final String report1 = writerUtil.createSingleProcessReport("Report1");
    final String report2 = writerUtil.createSingleProcessReport("Report2");

    final String combinedReport = writerUtil.createCombinedProcessReport("CombinedReport", asList(report1, report2));

    final String collection1 = writerUtil.createCollection("collection1", Collections.singletonList(combinedReport));
    final String collection2 = writerUtil.createCollection("otherCollection", Collections.singletonList(report2));

    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();
    assertCollectionEntitiesSize(collection1, collectionEntities, 3);
    assertCombinedReportChildren(collection1, collectionEntities, 2);
    assertCollectionEntitiesSize(collection2, collectionEntities, 1);
    assertCollectionEntitiesSize("private", collectionEntities, 3);
  }


  @Test
  public void combinedReportWithoutCollection_noCopyingOfChildren() {
    // given
    final String privateReport = writerUtil.createSingleProcessReport("PrivateReport");
    final String report1 = writerUtil.createSingleProcessReport("Report1");
    final String report2 = writerUtil.createSingleProcessReport("Report2");

    final String combinedReport = writerUtil.createCombinedProcessReport(
      "CombinedReport",
      asList(report1, report2, privateReport)
    );

    final String collection1 = writerUtil.createCollection("collection1", Collections.singletonList(report1));
    final String collection2 = writerUtil.createCollection("otherCollection", Collections.singletonList(report2));

    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();
    assertCollectionEntitiesSize(collection1, collectionEntities, 1);
    assertCollectionEntitiesSize(collection2, collectionEntities, 1);
    assertCollectionEntitiesSize("private", collectionEntities, 4);
  }

  @Test
  public void combinedReports_copiedReportsHaveIdenticalData() {
    // given
    final String report1 = writerUtil.createSingleProcessReport("Report1");
    final String report2 = writerUtil.createSingleProcessReport("Report2");

    final String combinedReport = writerUtil.createCombinedProcessReport("CombinedReport", asList(report1, report2));
    final String collection1 = writerUtil.createCollection("collection1", asList(combinedReport, report1, report2));
    final String collection2 = writerUtil.createCollection("collection2", Collections.singletonList(combinedReport));

    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();

    final CombinedReportDefinitionDto originalCombinedReport = getDocumentAsDto(
      combinedReport,
      COMBINED_REPORT_INDEX_NAME,
      CombinedReportDefinitionDto.class
    );

    final CombinedReportDefinitionDto copiedCombinedReport =
      (CombinedReportDefinitionDto) collectionEntities.get(collection2).stream()
        .filter(entity -> entity instanceof CombinedReportDefinitionDto)
        .findFirst()
        .orElseThrow(() -> new UpgradeRuntimeException("No entity found"));

    assertThat(copiedCombinedReport.getId(), not(is(originalCombinedReport.getId())));
    assertThat(copiedCombinedReport.getName(), is(originalCombinedReport.getName()));
    assertThat(copiedCombinedReport.getOwner(), is(originalCombinedReport.getOwner()));
    assertThat(copiedCombinedReport.getCollectionId(), not(is(originalCombinedReport.getCollectionId())));
    assertThat(copiedCombinedReport.getLastModifier(), is(originalCombinedReport.getLastModifier()));
    assertThat(copiedCombinedReport.getLastModified(), is(originalCombinedReport.getLastModified()));
    assertThat(
      copiedCombinedReport.getData().getConfiguration(),
      is(originalCombinedReport.getData().getConfiguration())
    );
    assertThat(
      copiedCombinedReport.getData().getVisualization(),
      is(originalCombinedReport.getData().getVisualization())
    );
    // assert combined report item data
    assertThat(
      copiedCombinedReport.getData().getReports().size(),
      is(originalCombinedReport.getData().getReports().size())
    );
    assertThat(
      copiedCombinedReport.getData().getReportIds(),
      not(contains(originalCombinedReport.getData().getReportIds()))
    );
    final List<String> copiedReportColors = copiedCombinedReport.getData()
      .getReports().stream()
      .map(CombinedReportItemDto::getColor)
      .collect(Collectors.toList());
    final List<String> originalReportColors = originalCombinedReport.getData()
      .getReports().stream()
      .map(CombinedReportItemDto::getColor)
      .collect(Collectors.toList());
    assertThat(copiedReportColors, contains(originalReportColors.toArray()));

    // assert child single report data
    final SingleProcessReportDefinitionDto originalChildReport =
      originalCombinedReport.getData()
        .getReports()
        .stream()
        .map(item -> getDocumentAsDto(
          item.getId(),
          SINGLE_PROCESS_REPORT_INDEX_NAME,
          SingleProcessReportDefinitionDto.class
        ))
        .findFirst()
        .orElseThrow(() -> new UpgradeRuntimeException("No entity found"));

    final SingleProcessReportDefinitionDto coll2ChildReport1 =
      (SingleProcessReportDefinitionDto) collectionEntities.get(collection2).stream()
        .filter(entity -> entity instanceof SingleProcessReportDefinitionDto && "Report1".equals(entity.getName()))
        .findFirst()
        .orElseThrow(() -> new UpgradeRuntimeException("No entity found"));

    assertSingleReportCopiesAreSame(originalChildReport, coll2ChildReport1);
  }


  @Test
  public void combinedReportsInSeveralCollections_copiesReports() {
    // given
    final String report1 = writerUtil.createSingleProcessReport("Report1");
    final String report2 = writerUtil.createSingleProcessReport("Report2");

    final String combinedReport = writerUtil.createCombinedProcessReport("CombinedReport", asList(report1, report2));

    final String collection1 = writerUtil.createCollection("collection1", Collections.singletonList(combinedReport));
    final String collection2 = writerUtil.createCollection("collection2", Collections.singletonList(combinedReport));
    final String collection3 = writerUtil.createCollection("collection3", Collections.singletonList(combinedReport));
    final String collection4 = writerUtil.createCollection("collection4", Collections.singletonList(combinedReport));

    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();
    assertCollectionEntitiesSize(collection1, collectionEntities, 3);
    assertCombinedReportChildren(collection1, collectionEntities, 2);
    assertCollectionEntitiesSize(collection2, collectionEntities, 3);
    assertCombinedReportChildren(collection2, collectionEntities, 2);
    assertCollectionEntitiesSize(collection3, collectionEntities, 3);
    assertCombinedReportChildren(collection3, collectionEntities, 2);
    assertCollectionEntitiesSize(collection4, collectionEntities, 3);
    assertCombinedReportChildren(collection4, collectionEntities, 2);
    assertCollectionEntitiesSize("private", collectionEntities, 3);
  }

  private void assertCombinedReportChildren(final String collection,
                                            final Map<String, List<CollectionEntity>> collectionEntities,
                                            final int childrenSize) {
    final CombinedReportDefinitionDto resultCombinedReport =
      (CombinedReportDefinitionDto) collectionEntities.get(collection).stream()
        .filter(entity -> entity instanceof CombinedReportDefinitionDto)
        .findFirst()
        .orElse(null);

    assertThat(resultCombinedReport, is(notNullValue()));
    assertThat(resultCombinedReport.getData(), is(notNullValue()));
    assertThat(resultCombinedReport.getData().getReports().size(), is(childrenSize));
    resultCombinedReport.getData().getReports().stream()
      .map(CombinedReportItemDto::getId)
      .forEach(childId ->
                 assertThat(
                   childId,
                   isIn(collectionEntities.get(collection).stream().map(CollectionEntity::getId).toArray())
                 ));
  }

  private void assertDashboardChildren(final String collection,
                                       final Map<String, List<CollectionEntity>> collectionEntities,
                                       final int childrenSize) {
    final DashboardDefinitionDto resultCombinedReport =
      (DashboardDefinitionDto) collectionEntities.get(collection).stream()
        .filter(entity -> entity instanceof DashboardDefinitionDto)
        .findFirst()
        .orElse(null);

    assertThat(resultCombinedReport, is(notNullValue()));
    assertThat(resultCombinedReport.getReports(), is(notNullValue()));
    assertThat(resultCombinedReport.getReports().size(), is(childrenSize));
    resultCombinedReport.getReports().stream()
      .map(ReportLocationDto::getId)
      .forEach(childId ->
                 assertThat(
                   childId,
                   isIn(collectionEntities.get(collection).stream().map(CollectionEntity::getId).toArray())
                 ));
  }

  private void assertSingleReportCopiesAreSame(final SingleProcessReportDefinitionDto coll1ChildReport1,
                                               final SingleProcessReportDefinitionDto coll2ChildReport1) {
    assertThat(coll2ChildReport1.getId(), not(is(coll1ChildReport1.getId())));
    assertThat(coll2ChildReport1.getName(), is(coll1ChildReport1.getName()));
    assertThat(coll2ChildReport1.getOwner(), is(coll1ChildReport1.getOwner()));
    assertThat(coll2ChildReport1.getCollectionId(), not(is(coll1ChildReport1.getCollectionId())));
    assertThat(coll2ChildReport1.getLastModifier(), is(coll1ChildReport1.getLastModifier()));
    assertThat(coll2ChildReport1.getLastModified(), is(coll1ChildReport1.getLastModified()));
    assertThat(coll2ChildReport1.getReportType(), is(coll1ChildReport1.getReportType()));
    assertThat(coll2ChildReport1.getData().toString(), is(coll1ChildReport1.getData().toString()));
  }


  @Test
  public void dashboardInMultipleCollections_copiesItselfAndChildren() {
    // given
    final String privateReport = writerUtil.createSingleProcessReport("PrivateReport");
    final String reportColl1 = writerUtil.createSingleProcessReport("CollectionTestReport");
    final String reportColl2 = writerUtil.createSingleProcessReport("ReportColl2");

    final String testDashboard = writerUtil.createDashboard(
      "dashboard",
      asList(privateReport, reportColl1, reportColl2)
    );

    final String collection1 = writerUtil.createCollection("Collection1", asList(testDashboard, reportColl1));
    final String collection2 = writerUtil.createCollection("Collection2", asList(testDashboard, reportColl2));


    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();
    assertCollectionEntitiesSize(collection1, collectionEntities, 4);
    assertDashboardChildren(collection1, collectionEntities, 3);
    assertCollectionEntitiesSize(collection2, collectionEntities, 4);
    assertDashboardChildren(collection2, collectionEntities, 3);
    assertCollectionEntitiesSize("private", collectionEntities, 4);
  }

  @Test
  public void dashboards_copiedReportsHaveIdenticalData() {
    // given
    final String report1 = writerUtil.createSingleProcessReport("Report1");
    final String report2 = writerUtil.createSingleProcessReport("Report2");

    final String combinedReport = writerUtil.createCombinedProcessReport("CombinedReport", asList(report1, report2));
    final String testDashboard = writerUtil.createDashboard(
      "dashboard",
      asList(report1, combinedReport, report2)
    );

    final String collection1 = writerUtil.createCollection("collection1", asList(testDashboard, report2));
    final String collection2 = writerUtil.createCollection("collection2", Collections.singletonList(combinedReport));

    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();

    final DashboardDefinitionDto originalCombinedReport = getDocumentAsDto(
      testDashboard,
      DASHBOARD_INDEX_NAME,
      DashboardDefinitionDto.class
    );

    final DashboardDefinitionDto copiedDashboard =
      (DashboardDefinitionDto) collectionEntities.get(collection1).stream()
        .filter(entity -> entity instanceof DashboardDefinitionDto)
        .findFirst()
        .orElseThrow(() -> new UpgradeRuntimeException("No entity found"));

    assertThat(copiedDashboard.getId(), not(is(originalCombinedReport.getId())));
    assertThat(copiedDashboard.getName(), is(originalCombinedReport.getName()));
    assertThat(copiedDashboard.getOwner(), is(originalCombinedReport.getOwner()));
    assertThat(copiedDashboard.getCollectionId(), not(is(originalCombinedReport.getCollectionId())));
    assertThat(copiedDashboard.getLastModifier(), is(originalCombinedReport.getLastModifier()));
    assertThat(copiedDashboard.getLastModified(), is(originalCombinedReport.getLastModified()));

    assertThat(copiedDashboard.getReports().size(), is(originalCombinedReport.getReports().size()));
    for (int i = 0; i < copiedDashboard.getReports().size(); i++) {
      final ReportLocationDto copiedLoc = copiedDashboard.getReports().get(i);
      final ReportLocationDto originalLoc = originalCombinedReport.getReports().get(i);

      assertThat(copiedLoc.getId(), not(is(originalLoc.getId())));
      assertThat(copiedLoc.getPosition(), is(originalLoc.getPosition()));
      assertThat(copiedLoc.getDimensions(), is(originalLoc.getDimensions()));
      assertThat(copiedLoc.getConfiguration(), is(originalLoc.getConfiguration()));

    }

    // assert child single report data
    final SingleProcessReportDefinitionDto originalChildReport =
      originalCombinedReport.getReports().stream()
        .map(item -> getDocumentAsDto(
          item.getId(),
          SINGLE_PROCESS_REPORT_INDEX_NAME,
          SingleProcessReportDefinitionDto.class
        ))
        .findFirst()
        .orElseThrow(() -> new UpgradeRuntimeException("No entity found"));

    final SingleProcessReportDefinitionDto coll2ChildReport1 =
      (SingleProcessReportDefinitionDto) collectionEntities.get(collection2).stream()
        .filter(entity -> entity instanceof SingleProcessReportDefinitionDto && "Report1".equals(entity.getName()))
        .findFirst()
        .orElseThrow(() -> new UpgradeRuntimeException("No entity found"));

    assertSingleReportCopiesAreSame(originalChildReport, coll2ChildReport1);
  }


  @Test
  public void dashboardWithoutReportsInMultipleCollections_copiesItself() {
    // given
    final String testDashboard = writerUtil.createDashboard(
      "dashboard",
      Collections.emptyList()
    );

    final String collection1 = writerUtil.createCollection("Collection1", asList(testDashboard));
    final String collection2 = writerUtil.createCollection("Collection2", asList(testDashboard));


    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();
    assertCollectionEntitiesSize(collection1, collectionEntities, 1);
    assertDashboardChildren(collection1, collectionEntities, 0);
    assertCollectionEntitiesSize(collection2, collectionEntities, 1);
    assertDashboardChildren(collection2, collectionEntities, 0);
  }

  @Test
  public void privateEntities_childrenWithDifferentOwnerGetCopied() {
    // given
    final String privateReport = writerUtil.createSingleProcessReport("PrivateReport");
    final String reportDifferentOwner = writerUtil.createSingleProcessReport("ReportDifferentOwner", "wagi");
    final String reportColl1 = writerUtil.createSingleProcessReport("CollectionTestReport", "wagi");

    writerUtil.createDashboard("dashboard", asList(privateReport, reportColl1, reportDifferentOwner));
    writerUtil.createDashboard("testDashboard2", asList(privateReport, reportColl1, reportDifferentOwner));
    writerUtil.createCombinedProcessReport("anotherReport", asList(privateReport, reportDifferentOwner), "sepp");

    final String collection1 = writerUtil.createCollection("Collection1", Collections.singletonList(reportColl1));


    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();

    assertCollectionEntitiesSize(collection1, collectionEntities, 1);
    assertCollectionEntitiesSize("private", collectionEntities, 10);

    final long demoReports = collectionEntities.get("private").stream()
      .filter(entity -> entity instanceof ReportDefinitionDto)
      .map(ReportDefinitionDto.class::cast)
      .filter(report -> "demo".equals(report.getOwner()))
      .count();
    assertThat(demoReports, is(3L));

    final long wagiReports = collectionEntities.get("private").stream()
      .filter(entity -> entity instanceof ReportDefinitionDto)
      .map(ReportDefinitionDto.class::cast)
      .filter(report -> "wagi".equals(report.getOwner()))
      .count();
    assertThat(wagiReports, is(2L));

    final long seppReports = collectionEntities.get("private").stream()
      .filter(entity -> entity instanceof ReportDefinitionDto)
      .map(ReportDefinitionDto.class::cast)
      .filter(report -> "sepp".equals(report.getOwner()))
      .count();
    assertThat(seppReports, is(3L));
  }


  @Test
  public void testMultipleDashboardsAndCombinedReports() {
    // given
    final String privateReport = writerUtil.createSingleProcessReport("PrivateReport");
    final String reportColl1 = writerUtil.createSingleProcessReport("CollectionTestReport");
    final String reportColl2 = writerUtil.createSingleProcessReport("reportColl2");
    final String reportCombined1 = writerUtil.createSingleProcessReport("reportCombined1");
    final String decisionReport = writerUtil.createSingleDecisionReport("DecisionReport");

    final String combined = writerUtil.createCombinedProcessReport("Combined", asList(reportCombined1, reportColl2));

    final String dashboard = writerUtil.createDashboard(
      "dashboard",
      asList(privateReport, reportColl1, combined, decisionReport)
    );

    final String collection1 = writerUtil.createCollection("Collection1", asList(dashboard, combined, reportColl1));
    final String collection2 = writerUtil.createCollection("Collection2", asList(dashboard, reportColl2));
    final String collection3 = writerUtil.createCollection(
      "Collection3",
      asList(combined, reportCombined1, decisionReport)
    );


    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Map<String, List<CollectionEntity>> collectionEntities = getAllCollectionEntitiesAsMap();
    assertCollectionEntitiesSize(collection1, collectionEntities, 7);
    assertDashboardChildren(collection1, collectionEntities, 4);
    assertCombinedReportChildren(collection1, collectionEntities, 2);
    assertCollectionEntitiesSize(collection2, collectionEntities, 7);
    assertDashboardChildren(collection1, collectionEntities, 4);
    assertCombinedReportChildren(collection2, collectionEntities, 2);
    assertCollectionEntitiesSize(collection3, collectionEntities, 4);
    assertCombinedReportChildren(collection3, collectionEntities, 2);
    assertCollectionEntitiesSize("private", collectionEntities, 7);
  }


  private void assertCollectionEntitiesSize(final String collection1,
                                            final Map<String, List<CollectionEntity>> collectionEntities,
                                            final int size) {
    final List<CollectionEntity> collection1Entities = collectionEntities.get(collection1);
    assertThat(collection1Entities, is(notNullValue()));
    assertThat(collection1Entities.size(), is(size));
    collection1Entities.forEach(entity -> assertThat(
      entity.getCollectionId(),
      "private".equals(collection1) ? is(nullValue()) : is(collection1)
    ));
  }


  @SneakyThrows
  private GetResponse getDocument(final String id, final String indexName) {
    return prefixAwareClient.get(new GetRequest(indexName, indexName, id), RequestOptions.DEFAULT);
  }

  @SneakyThrows
  private <T> T getDocumentAsDto(final String id, final String indexName, final Class<T> clazz) {
    final GetResponse reportResponse = getDocument(id, indexName);
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), clazz
    );
  }

  private Map<String, List<CollectionEntity>> getAllCollectionEntitiesAsMap() {
    return getAllCollectionEntitiesAsMap(
      CollectionEntity.class,
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SINGLE_DECISION_REPORT_INDEX_NAME,
      COMBINED_REPORT_INDEX_NAME,
      DASHBOARD_INDEX_NAME
    );
  }

  @SneakyThrows
  private <T extends CollectionEntity> Map<String, List<T>> getAllCollectionEntitiesAsMap(final Class<T> clazz,
                                                                                          final String... indexName) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(indexName)
      .types(indexName)
      .source(searchSourceBuilder);

    final SearchResponse searchResponse = prefixAwareClient.search(searchRequest, RequestOptions.DEFAULT);

    return Arrays.stream(searchResponse.getHits().getHits())
      .map(hit -> deserializeToDto(clazz, hit))
      .collect(Collectors.groupingBy(entity -> Optional.ofNullable(entity.getCollectionId()).orElse("private")));
  }

  @SneakyThrows
  private <T> List<T> getAllDocumentsAsDto(final Class<T> clazz, final String... indexName) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(indexName)
      .types(indexName)
      .source(searchSourceBuilder);

    final SearchResponse searchResponse = prefixAwareClient.search(searchRequest, RequestOptions.DEFAULT);

    return Arrays.stream(searchResponse.getHits().getHits())
      .map(hit -> deserializeToDto(clazz, hit))
      .collect(Collectors.toList());
  }

  @SneakyThrows
  private <T> T deserializeToDto(final Class<T> clazz, final SearchHit hit) {
    return objectMapper.readValue(hit.getSourceAsString(), clazz);
  }

}
