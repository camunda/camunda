/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.service.es.schema.type.report.AbstractReportType;
import org.camunda.optimize.service.es.schema.type.report.CombinedReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpgradeCombinedReportSettingsStep;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getVersionedOptimizeIndexNameForTypeMapping;
import static org.camunda.optimize.upgrade.util.ReportUtil.buildSingleReportIdToVisualizationAndViewMap;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.getDefaultCombinedReportConfigurationAsMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

public class UpgradeCombinedProcessReportConfigurationDataIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "2.3.0";
  private static final String TO_VERSION = "2.4.0";

  private static final String DEFAULT_COLOR = "#1991c8";

  private static final AbstractReportType COMBINED_REPORT_TYPE = new CombinedReportType();
  private static final AbstractReportType SINGLE_PROCESS_REPORT_TYPE = new SingleProcessReportType();

  // report_{CONFIG_VERSION}_{MIGRATION CASE ID}_{DESCRIPTION}
  private static final String REPORT_220 = "949299d1-53a4-4eaf-a4df-a8a4f357d0b3";
  private static final String REPORT_230_221_ID_LINE_FREQUENCY_TARGETVALUE = "c959b895-b5b1-439f-b6cd-c5f1794b4ed9";
  private static final String REPORT_230_221_ID_BAR_FREQUENCY_TARGETVALUE = "10894d3c-c95e-4efa-8f96-9dad97cf3224";
  private static final String REPORT_230_221_ID_NUMBER_FREQUENCY_TARGETVALUE = "e5674ee4-f709-4475-b564-feacc669c975";
  private static final String REPORT_230_222_ID_LINE_DURATION_TARGETVALUE = "d449a1ca-c60d-4d14-8117-ea88cb6341db";
  private static final String REPORT_230_222_ID_BAR_DURATION_TARGETVALUE = "a1531a9b-f667-4338-b050-7e81df642c10";
  private static final String REPORT_230_222_ID_NUMBER_DURATION_TARGETVALUE = "e762ea28-2f90-4038-8884-c4780170bd49";
  private static final String REPORT_230_ID_MULTIPLE_COLORS = "e762ea28-2f90-4038-8884-c4780170bd49";
  private static final String REPORT_230_ID_MULTIPLE_INCONSISTENT_COLORS = "c75d977a-d70c-43ba-96d0-a909415a944a";
  private static final String REPORT_230_MISSING_VISUALIZATION = "b5ad7c59-0d34-4db2-a002-01e0275264bc";

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    COMBINED_REPORT_TYPE.setDynamicMappingsValue("false");

    initSchema(Lists.newArrayList(METADATA_TYPE, SINGLE_PROCESS_REPORT_TYPE, COMBINED_REPORT_TYPE));
    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/configuration_upgrade/22-single-process-report-bulk");
    executeBulk("steps/configuration_upgrade/23-single-process-report-bulk");
    executeBulk("steps/configuration_upgrade/22-combined-report-bulk");
    executeBulk("steps/configuration_upgrade/23-combined-report-bulk");
  }

  @Test
  public void combinedReportDynamicSettingIsStrictAfterUpgrade() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();
    assertThat(getDynamicMappingValue(), is("false"));

    // when
    upgradePlan.execute();

    // then
    assertThat(getDynamicMappingValue(), is("strict"));
  }

  @Test
  public void combinedReportMissingVisualizationSet() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportDataDto numberDataDto = getCombinedReportDataDto(REPORT_230_MISSING_VISUALIZATION);
    assertThat(numberDataDto.getVisualization(), is(ProcessVisualization.NUMBER));
  }

  @Test
  public void newReportsListWithColors() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportDataDto dataDto = getCombinedReportDataDto(REPORT_230_ID_MULTIPLE_COLORS);

    final String reportIdWithColor1 = "6afd9f54-de24-4499-b9d8-c90010ebf3ca";
    final String reportWithColor2 = "cde03d27-7c50-4a4f-a8b8-528c7aefc928";
    assertThat(dataDto.getReports().size(), is(2));
    assertThat(
      dataDto.getReports().stream().map(CombinedReportItemDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(reportIdWithColor1, reportWithColor2)
    );
    assertThat(
      dataDto.getReports().stream().filter(reportItemDto -> reportItemDto.getId().equals(reportIdWithColor1))
        .findFirst().get().getColor(),
      is("#1991c7")
    );
    assertThat(
      dataDto.getReports().stream().filter(reportItemDto -> reportItemDto.getId().equals(reportWithColor2))
        .findFirst().get().getColor(),
      is("#1991c8")
    );
  }

  @Test
  public void newReportsListMissingColorWillBeDefaultColor() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportDataDto dataDto = getCombinedReportDataDto(REPORT_230_ID_MULTIPLE_INCONSISTENT_COLORS);

    final String reportIdWithColor1 = "6afd9f54-de24-4499-b9d8-c90010ebf3ca";
    final String reportWithoutColor = "cde03d27-7c50-4a4f-a8b8-528c7aefc928";
    assertThat(dataDto.getReports().size(), is(2));
    assertThat(
      dataDto.getReports().stream().map(CombinedReportItemDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(reportIdWithColor1, reportWithoutColor)
    );
    assertThat(
      dataDto.getReports().stream().filter(reportItemDto -> reportItemDto.getId().equals(reportIdWithColor1))
        .findFirst().get().getColor(),
      is("#1991c7")
    );
    assertThat(
      dataDto.getReports().stream().filter(reportItemDto -> reportItemDto.getId().equals(reportWithoutColor))
        .findFirst().get().getColor(),
      is(DEFAULT_COLOR)
    );
  }

  @Test
  public void defaultConfigFor220CombinedReport() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(REPORT_220);

    assertThat(configuration, is(new CombinedReportConfigurationDto()));
  }

  @Test
  public void lineFrequencyTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_230_221_ID_LINE_FREQUENCY_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is("3"));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(true));
  }

  @Test
  public void barFrequencyTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_230_221_ID_BAR_FREQUENCY_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is("2"));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(true));

  }

  @Test
  public void numberFrequencyTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_230_221_ID_NUMBER_FREQUENCY_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is("1"));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(true));
  }

  @Test
  public void lineDurationTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_230_222_ID_LINE_DURATION_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is("4"));
    assertThat(configuration.getTargetValue().getDurationChart().getBelow(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getUnit(), is(TargetValueUnit.HOURS));
  }

  @Test
  public void barDurationTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_230_222_ID_BAR_DURATION_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is("3"));
    assertThat(configuration.getTargetValue().getDurationChart().getBelow(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getUnit(), is(TargetValueUnit.SECONDS));
  }

  @Test
  public void numberDurationTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final CombinedReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_230_222_ID_NUMBER_DURATION_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is("1"));
    assertThat(configuration.getTargetValue().getDurationChart().getUnit(), is(TargetValueUnit.HOURS));
    assertThat(configuration.getTargetValue().getDurationChart().getBelow(), is(true));
  }

  @Test
  public void ridiculousHighNumberOfParameters() throws Exception {
    //given
    final Map<String, Map> singleReportsViewAndVisualization = buildSingleReportIdToVisualizationAndViewMap(
      new ConfigurationService()
    );
    final Map<String, Map> randomSingleReportsViewAndVisualizations = Stream
      .generate(() -> new AbstractMap.SimpleImmutableEntry<>(UUID.randomUUID().toString(), ImmutableMap.of(
        "visualization", ProcessVisualization.TABLE.getId(),
        "view", ImmutableMap.of(
          "entity", ProcessViewEntity.PROCESS_INSTANCE,
          "property", ProcessViewProperty.FREQUENCY
        )
      )))
      .limit(10_000)
      .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new UpgradeCombinedReportSettingsStep(
        getDefaultCombinedReportConfigurationAsMap(),
        Stream.of(singleReportsViewAndVisualization, randomSingleReportsViewAndVisualizations)
          .flatMap(m -> m.entrySet().stream())
          .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue))
      ))
      .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse search = restClient.search(new SearchRequest(getReportIndexAlias()), RequestOptions.DEFAULT);
    assertThat(search.getHits().getTotalHits(), is(9L));
  }

  private String getDynamicMappingValue() throws IOException {
    final String indexName = getVersionedOptimizeIndexNameForTypeMapping(COMBINED_REPORT_TYPE);
    final Response response = restClient.getLowLevelClient().performRequest(
      new Request(HttpGet.METHOD_NAME, "/" + indexName + "/_mapping")
    );
    final String mappingWithIndexName = EntityUtils.toString(response.getEntity());
    final JsonNode mapping = objectMapper.readTree(mappingWithIndexName);
    return mapping.get(indexName).get("mappings").get(COMBINED_REPORT_TYPE.getType()).get("dynamic").asText();
  }

  private String getReportIndexAlias() {
    return getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE.getType());
  }

  private UpgradePlan getReportConfigurationUpgradePlan() throws Exception {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new UpgradeCombinedReportSettingsStep(
        getDefaultCombinedReportConfigurationAsMap(),
        buildSingleReportIdToVisualizationAndViewMap(new ConfigurationService())
      ))
      .build();
  }

  private CombinedReportConfigurationDto getCombinedReportDefinitionConfigurationById(final String id)
    throws IOException {
    return getCombinedReportDataDto(id).getConfiguration();
  }

  private CombinedReportDataDto getCombinedReportDataDto(final String id) throws IOException {
    final GetResponse reportResponse = restClient.get(
      new GetRequest(getReportIndexAlias(), COMBINED_REPORT_TYPE.getType(), id), RequestOptions.DEFAULT
    );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), CombinedReportDefinitionDto.class
    ).getData();
  }

}
