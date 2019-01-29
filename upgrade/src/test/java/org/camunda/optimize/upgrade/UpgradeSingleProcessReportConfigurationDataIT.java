package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.configuration.ReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.configuration.heatmap_target_value.HeatmapTargetValueEntryDto;
import org.camunda.optimize.dto.optimize.query.report.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.type.report.AbstractReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpgradeSingleProcessReportSettingsFrom23Step;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.getDefaultReportConfigurationAsMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class UpgradeSingleProcessReportConfigurationDataIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "2.3.0";
  private static final String TO_VERSION = "2.4.0";

  private static final String DEFAULT_COLOR = "#1991c8";
  private static final String CUSTOM_COLOR = "#DB3E00";
  private static final AbstractReportType SINGLE_PROCESS_REPORT_TYPE = new SingleProcessReportType();

  private static final String EMPTY_REPORT_ID = "c07c84e1-88b0-438a-baad-0808b9d7e1d1";
  private static final String REPORT_1_ID_COLOR = "e8cb089a-eba6-43cc-9c78-282f09d192fe";
  private static final String REPORT_21_ID_HEAT_RELATIVE_ABSOLUTE = "bdbd4672-7625-445a-bef1-9ffd0ee4529f";
  private static final String REPORT_211_ID_HEAT_FLOWNODE_DURATION_TARGETVALUE = "0b201336-bdf0-4989-aa74-aa3ad1aec343";
  private static final String REPORT_221_ID_LINE_FREQUENCY_TARGETVALUE = "8b7addf5-a64f-472c-bcf5-c20a89bbcb60";
  private static final String REPORT_221_ID_BAR_FREQUENCY_TARGETVALUE = "aa7da17b-56cc-4ffa-a6ad-b0715545b340";
  private static final String REPORT_222_ID_LINE_DURATION_TARGETVALUE = "b2ae68e5-d0e0-4be2-82fb-a870e6f54145";
  private static final String REPORT_222_ID_BAR_DURATION_TARGETVALUE = "52dd1490-500f-43e1-b0af-c649b1b9f64b";
  private static final String REPORT_23_COUNT_PI_FREQUENCY_GROUP_BY_NONE_TARGETVALUE = "cde03d27-7c50-4a4f-a8b8-528c7aefc928";
  private static final String REPORT_24_PI_DURATION_GROUP_BY_NONE_TARGETVALUE = "6afd9f54-de24-4499-b9d8-c90010ebf3ca";

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(METADATA_TYPE, SINGLE_PROCESS_REPORT_TYPE));

    addVersionToElasticsearch(FROM_VERSION);

    executeBulk("steps/configuration_upgrade/23-single-process-report-bulk");
  }

  @Test
  public void xmlStillPresent() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<SingleProcessReportDefinitionDto> reports = getAllSingleProcessReportDefinitionDtos();
    assertThat(reports.size(), is(10));
    reports.forEach(singleProcessReportDefinitionDto -> {
      if (!singleProcessReportDefinitionDto.getId().equals(EMPTY_REPORT_ID)) {
        assertThat(singleProcessReportDefinitionDto.getData().getConfiguration().getXml(), is(notNullValue()));
      } else {
        assertThat(singleProcessReportDefinitionDto.getData().getConfiguration().getXml(), is(nullValue()));
      }
    });
  }

  @Test
  public void colorFieldMigration() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_1_ID_COLOR).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_21_ID_HEAT_RELATIVE_ABSOLUTE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_211_ID_HEAT_FLOWNODE_DURATION_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_221_ID_LINE_FREQUENCY_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_221_ID_BAR_FREQUENCY_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_222_ID_LINE_DURATION_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_222_ID_BAR_DURATION_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_23_COUNT_PI_FREQUENCY_GROUP_BY_NONE_TARGETVALUE).getColor(),
      is(CUSTOM_COLOR)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_24_PI_DURATION_GROUP_BY_NONE_TARGETVALUE).getColor(),
      is(DEFAULT_COLOR)
    );
  }

  @Test
  public void heatMapRelativeAbsoluteMigration() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_21_ID_HEAT_RELATIVE_ABSOLUTE).getAlwaysShowAbsolute(),
      is(true)
    );
    assertThat(
      getSingleProcessReportDefinitionConfigurationById(REPORT_21_ID_HEAT_RELATIVE_ABSOLUTE).getAlwaysShowRelative(),
      is(true)
    );
  }

  @Test
  public void heatMapTargetValueMigration() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(
      REPORT_211_ID_HEAT_FLOWNODE_DURATION_TARGETVALUE
    );

    assertThat(configuration.getTargetValue(), is(getDefaultReportConfiguration().getTargetValue()));
    assertThat(configuration.getHeatmapTargetValue().getActive(), is(true));
    assertThat(configuration.getHeatmapTargetValue().getValues().size(), is(1));
    final HeatmapTargetValueEntryDto approveInvoiceTargetValue = configuration.getHeatmapTargetValue()
      .getValues()
      .get("approveInvoice");
    assertThat(approveInvoiceTargetValue.getUnit(), is(TargetValueUnit.WEEKS));
    assertThat(approveInvoiceTargetValue.getValue(), is("1"));
  }

  @Test
  public void lineFrequencyTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(
      REPORT_221_ID_LINE_FREQUENCY_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is("1.5"));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(false));
  }

  @Test
  public void barFrequencyTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(
      REPORT_221_ID_BAR_FREQUENCY_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountChart().getValue(), is("1.5"));
    assertThat(configuration.getTargetValue().getCountChart().getBelow(), is(false));

  }

  @Test
  public void lineDurationTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(
      REPORT_222_ID_LINE_DURATION_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is("1"));
    assertThat(configuration.getTargetValue().getDurationChart().getBelow(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getUnit(), is(TargetValueUnit.SECONDS));
  }

  @Test
  public void barDurationTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(
      REPORT_222_ID_BAR_DURATION_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getValue(), is("1"));
    assertThat(configuration.getTargetValue().getDurationChart().getBelow(), is(true));
    assertThat(configuration.getTargetValue().getDurationChart().getUnit(), is(TargetValueUnit.SECONDS));
  }

  @Test
  public void countPiFrequencyGroupByNoneTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(
      REPORT_23_COUNT_PI_FREQUENCY_GROUP_BY_NONE_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getCountProgress().getBaseline(), is("1"));
    assertThat(configuration.getTargetValue().getCountProgress().getTarget(), is("101"));
  }

  @Test
  public void piDurationGroupByNoneTargetValue() throws Exception {
    //given
    UpgradePlan upgradePlan = getReportConfigurationUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ReportConfigurationDto configuration = getSingleProcessReportDefinitionConfigurationById(
      REPORT_24_PI_DURATION_GROUP_BY_NONE_TARGETVALUE
    );

    assertThat(configuration.getTargetValue().getActive(), is(true));
    assertThat(configuration.getTargetValue().getDurationProgress().getBaseline().getValue(), is("1"));
    assertThat(
      configuration.getTargetValue().getDurationProgress().getBaseline().getUnit(),
      is(TargetValueUnit.SECONDS)
    );
    assertThat(configuration.getTargetValue().getDurationProgress().getTarget().getValue(), is("5"));
    assertThat(configuration.getTargetValue().getDurationProgress().getTarget().getUnit(), is(TargetValueUnit.SECONDS));
  }

  private ReportConfigurationDto getDefaultReportConfiguration() {
    String pathToMapping = "upgrade/main/UpgradeFrom23To24/default-report-configuration.json";
    String reportConfigurationStructureAsJson = SchemaUpgradeUtil.readClasspathFileAsString(pathToMapping);
    ReportConfigurationDto reportConfigurationAsMap;
    try {
      reportConfigurationAsMap = objectMapper.readValue(
        reportConfigurationStructureAsJson,
        ReportConfigurationDto.class
      );
    } catch (IOException e) {
      throw new UpgradeRuntimeException("Could not deserialize default report configuration structure as json!");
    }
    return reportConfigurationAsMap;
  }

  private String getReportIndexAlias() {
    return getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE.getType());
  }

  private UpgradePlan getReportConfigurationUpgradePlan() throws Exception {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new UpgradeSingleProcessReportSettingsFrom23Step(getDefaultReportConfigurationAsMap()))
      .build();
  }

  private ReportConfigurationDto getSingleProcessReportDefinitionConfigurationById(final String id) throws IOException {
    final GetResponse reportResponse = restClient.get(
      new GetRequest(getReportIndexAlias(), SINGLE_PROCESS_REPORT_TYPE.getType(), id), RequestOptions.DEFAULT
    );
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), SingleProcessReportDefinitionDto.class
    ).getData().getConfiguration();
  }

  private List<SingleProcessReportDefinitionDto> getAllSingleProcessReportDefinitionDtos() throws IOException {
    final SearchResponse searchResponse = restClient.search(
      new SearchRequest(getReportIndexAlias()),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), SingleProcessReportDefinitionDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.toList());
  }


}
