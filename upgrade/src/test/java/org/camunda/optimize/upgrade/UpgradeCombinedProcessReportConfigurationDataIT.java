package org.camunda.optimize.upgrade;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.configuration.ReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.service.es.schema.type.report.AbstractReportType;
import org.camunda.optimize.service.es.schema.type.report.CombinedReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpgradeCombinedReportSettingsFrom23Step;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.util.ReportUtil.buildSingleReportIdToVisualizationAndViewMap;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.getDefaultReportConfigurationAsMap;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class UpgradeCombinedProcessReportConfigurationDataIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "2.3.0";
  private static final String TO_VERSION = "2.4.0";

  private static final AbstractReportType COMBINED_REPORT_TYPE = new CombinedReportType();
  private static final AbstractReportType SINGLE_PROCESS_REPORT_TYPE = new SingleProcessReportType();

  private static final String REPORT_221_ID_LINE_FREQUENCY_TARGETVALUE = "c959b895-b5b1-439f-b6cd-c5f1794b4ed9";
  private static final String REPORT_221_ID_BAR_FREQUENCY_TARGETVALUE = "10894d3c-c95e-4efa-8f96-9dad97cf3224";
  private static final String REPORT_221_ID_NUMBER_FREQUENCY_TARGETVALUE = "e5674ee4-f709-4475-b564-feacc669c975";
  private static final String REPORT_222_ID_LINE_DURATION_TARGETVALUE = "d449a1ca-c60d-4d14-8117-ea88cb6341db";
  private static final String REPORT_222_ID_BAR_DURATION_TARGETVALUE = "a1531a9b-f667-4338-b050-7e81df642c10";
  private static final String REPORT_222_ID_NUMBER_DURATION_TARGETVALUE = "e762ea28-2f90-4038-8884-c4780170bd49";

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(METADATA_TYPE, SINGLE_PROCESS_REPORT_TYPE, COMBINED_REPORT_TYPE));

    addVersionToElasticsearch(FROM_VERSION);

    executeBulk("steps/configuration_upgrade/23-single-process-report-bulk");
    executeBulk("steps/configuration_upgrade/23-combined-report-bulk");
  }

  @Test
  public void lineFrequencyTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_221_ID_LINE_FREQUENCY_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is(3.0D));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(true));
  }

  @Test
  public void barFrequencyTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_221_ID_BAR_FREQUENCY_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is(2.0D));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(true));

  }

  @Test
  public void numberFrequencyTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_221_ID_NUMBER_FREQUENCY_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is(1.0D));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(true));
  }

  @Test
  public void lineDurationTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_222_ID_LINE_DURATION_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is(4.0D));
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
    final ReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_222_ID_BAR_DURATION_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is(3.0D));
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
    final ReportConfigurationDto configuration = getCombinedReportDefinitionConfigurationById(
      REPORT_222_ID_NUMBER_DURATION_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is(1.0D));
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
          "operation", ProcessViewOperation.MAX,
          "entity", ProcessViewEntity.PROCESS_INSTANCE,
          "property", ProcessViewProperty.FREQUENCY
        )
      )))
      .limit(10_000)
      .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new UpgradeCombinedReportSettingsFrom23Step(
        getDefaultReportConfigurationAsMap(),
        Stream.of(singleReportsViewAndVisualization, randomSingleReportsViewAndVisualizations)
          .flatMap(m -> m.entrySet().stream())
          .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue))
      ))
      .build();

    // when
    upgradePlan.execute();

    // then
    final SearchResponse search = restClient.search(new SearchRequest(getReportIndexAlias()), RequestOptions.DEFAULT);
    assertThat(search.getHits().getTotalHits(), is(6L));
  }

  private String getReportIndexAlias() {
    return getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE.getType());
  }

  private UpgradePlan getReportConfigurationUpgradePlan() throws Exception {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new UpgradeCombinedReportSettingsFrom23Step(
        getDefaultReportConfigurationAsMap(),
        buildSingleReportIdToVisualizationAndViewMap(new ConfigurationService())
      ))
      .build();
  }

  private ReportConfigurationDto getCombinedReportDefinitionConfigurationById(final String id) throws IOException {
    final GetResponse reportResponse = restClient.get(
      new GetRequest(getReportIndexAlias(), COMBINED_REPORT_TYPE.getType(), id), RequestOptions.DEFAULT
    );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), CombinedReportDefinitionDto.class
    ).getData().getConfiguration();
  }

}
