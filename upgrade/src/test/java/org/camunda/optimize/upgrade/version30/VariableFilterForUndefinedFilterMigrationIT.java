/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class VariableFilterForUndefinedFilterMigrationIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "3.0.0";
  private static final String PROCESS_REPORT_UNDEFINED_TRUE_ID = "ad3eec4f-5011-4e48-a630-d4a4e69fbbd8";
  private static final String PROCESS_REPORT_UNDEFINED_FALSE_ID = "bd3eec4f-5011-4e48-a630-d4a4e69fbbd8";
  private static final String DECISION_REPORT_UNDEFINED_TRUE_ID = "ad5a0c57-2690-4a20-9952-e72d6a50375c";
  private static final String DECISION_REPORT_UNDEFINED_FALSE_ID = "bd5a0c57-2690-4a20-9952-e72d6a50375c";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      COMBINED_REPORT_INDEX,
      TIMESTAMP_BASED_IMPORT_INDEX,
      IMPORT_INDEX_INDEX
    ));
    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/3.0/report_data/30-report-with-filterForUndefined-filters-bulk");
  }

  @SneakyThrows
  @Test
  public void reportFiltersAreMigrated() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // do some given state assert as the bulks are hard to read
    assertThat(getAllDocumentsOfIndex(SINGLE_PROCESS_REPORT_INDEX_NAME))
      .allSatisfy(report -> assertFilterForUndefinedFlagIsPresentInOptimize30Reports(report, 7));
    assertThat(getAllDocumentsOfIndex(SINGLE_DECISION_REPORT_INDEX_NAME))
      .allSatisfy(report -> assertFilterForUndefinedFlagIsPresentInOptimize30Reports(report, 14));

    // when
    upgradePlan.execute();

    // then
    final Optional<SingleProcessReportDefinitionDto> processReportWithNullFilter = getDocumentByIdAs(
      SINGLE_PROCESS_REPORT_INDEX_NAME, PROCESS_REPORT_UNDEFINED_TRUE_ID, SingleProcessReportDefinitionDto.class
    );
    assertThat(processReportWithNullFilter).isPresent();
    assertThat(processReportWithNullFilter.get().getData().getFilter())
      .filteredOn(filter -> filter instanceof VariableFilterDto)
      .hasSize(7)
      .extracting(ProcessFilterDto::getData)
      .allSatisfy(this::assertFilterForUndefinedTrueWasMigrated);

    final Optional<SingleProcessReportDefinitionDto> processReportWithoutNullFilter = getDocumentByIdAs(
      SINGLE_PROCESS_REPORT_INDEX_NAME, PROCESS_REPORT_UNDEFINED_FALSE_ID, SingleProcessReportDefinitionDto.class
    );
    assertThat(processReportWithoutNullFilter).isPresent();
    assertThat(processReportWithoutNullFilter.get().getData().getFilter())
      .filteredOn(filter -> filter instanceof VariableFilterDto)
      .hasSize(7)
      .extracting(ProcessFilterDto::getData)
      .allSatisfy(this::assertFilterForUndefinedFalseWasMigrated);

    final Optional<SingleDecisionReportDefinitionDto> decisionReportWithNullFilter = getDocumentByIdAs(
      SINGLE_DECISION_REPORT_INDEX_NAME, DECISION_REPORT_UNDEFINED_TRUE_ID, SingleDecisionReportDefinitionDto.class
    );
    assertThat(decisionReportWithNullFilter).isPresent();
    assertThat(decisionReportWithNullFilter.get().getData().getFilter())
      .filteredOn(filter -> filter instanceof InputVariableFilterDto || filter instanceof OutputVariableFilterDto)
      .hasSize(14)
      .extracting(DecisionFilterDto::getData)
      .allSatisfy(this::assertFilterForUndefinedTrueWasMigrated);

    final Optional<SingleDecisionReportDefinitionDto> decisionReportWithoutNullFilter = getDocumentByIdAs(
      SINGLE_DECISION_REPORT_INDEX_NAME, DECISION_REPORT_UNDEFINED_FALSE_ID, SingleDecisionReportDefinitionDto.class
    );
    assertThat(decisionReportWithoutNullFilter).isPresent();
    assertThat(decisionReportWithoutNullFilter.get().getData().getFilter())
      .filteredOn(filter -> filter instanceof InputVariableFilterDto || filter instanceof OutputVariableFilterDto)
      .hasSize(14)
      .extracting(DecisionFilterDto::getData)
      .allSatisfy(this::assertFilterForUndefinedFalseWasMigrated);
  }

  @SuppressWarnings("unchecked")
  private void assertFilterForUndefinedFlagIsPresentInOptimize30Reports(final SearchHit document,
                                                                        final int expectedFilterCount) {
    final Map<String, Object> data = (Map<String, Object>) document.getSourceAsMap()
      .get(SingleProcessReportIndexV2.DATA);
    final List<Map<String, Object>> filter = (List<Map<String, Object>>) data.get("filter");
    assertThat(filter).hasSize(expectedFilterCount);
    filter.forEach(filterEntry -> {
      final Map<String, Object> filterData = (Map<String, Object>) filterEntry.get("data");
      final Boolean filterForUndefined = (Boolean) filterData.get("filterForUndefined");
      assertThat(filterForUndefined).isNotNull();
    });
  }

  private void assertFilterForUndefinedFalseWasMigrated(final FilterDataDto filterDataDto) {
    if (filterDataDto instanceof DateVariableFilterDataDto) {
      assertThat(extractIsIncludeUndefined((DateVariableFilterDataDto) filterDataDto)).isFalse();
    } else if (filterDataDto instanceof BooleanVariableFilterDataDto) {
      assertThat(((BooleanVariableFilterDataDto) filterDataDto).getData().getValues())
        .hasSize(1)
        .allSatisfy(value -> assertThat(value).isNotNull());
    } else if (filterDataDto instanceof OperatorMultipleValuesVariableFilterDataDto) {
      assertThat(extractValues((OperatorMultipleValuesVariableFilterDataDto) filterDataDto))
        .hasSize(1)
        .allSatisfy(value -> assertThat(value).isNotNull());
    } else {
      fail("Unexpected filter data type " + filterDataDto.getClass());
    }
  }

  private void assertFilterForUndefinedTrueWasMigrated(final FilterDataDto filterDataDto) {
    if (filterDataDto instanceof DateVariableFilterDataDto) {
      assertThat(extractIsIncludeUndefined((DateVariableFilterDataDto) filterDataDto)).isTrue();
    } else if (filterDataDto instanceof BooleanVariableFilterDataDto) {
      assertThat(((BooleanVariableFilterDataDto) filterDataDto).getData().getValues())
        .hasSize(1)
        .containsOnlyNulls();
    } else if (filterDataDto instanceof OperatorMultipleValuesVariableFilterDataDto) {
      assertThat(extractValues((OperatorMultipleValuesVariableFilterDataDto) filterDataDto))
        .hasSize(1)
        .containsOnlyNulls();
    } else {
      fail("Unexpected filter data type " + filterDataDto.getClass());
    }
  }

  private List<String> extractValues(final OperatorMultipleValuesVariableFilterDataDto filterDataDto) {
    return filterDataDto.getData().getValues();
  }

  private boolean extractIsIncludeUndefined(final DateVariableFilterDataDto filterDataDto) {
    return filterDataDto.getData().isIncludeUndefined();
  }

}
