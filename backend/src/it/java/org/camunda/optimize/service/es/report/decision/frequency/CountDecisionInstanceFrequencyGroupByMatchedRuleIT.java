package org.camunda.optimize.service.es.report.decision.frequency;

import junitparams.JUnitParamsRunner;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.DecisionReportEvaluationResultDto;
import org.camunda.optimize.service.es.filter.FilterOperatorConstants;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.DecisionReportDataType;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createDoubleInputVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class CountDecisionInstanceFrequencyGroupByMatchedRuleIT extends AbstractDecisionDefinitionIT {

  private static final String INVOICE_RULE_1_ID = "DecisionRule_1of5a87";
  private static final String INVOICE_RULE_3_ID = "row-49839158-4";
  private static final String INVOICE_RULE_2_ID = "DecisionRule_1ak4z14";
  private static final String INVOICE_RULE_4_ID = "DecisionRule_0cuxolz";
  private static final String BEVERAGES_RULE_3_ID = "row-506282952-9";
  private static final String BEVERAGES_RULE_5_ID = "row-506282952-11";
  private static final String BEVERAGES_RULE_6_ID = "row-506282952-12";

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByMatchedRule() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    // rule 3
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc")
    );
    // rule 4
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployAndStartDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, decisionDefinitionVersion1
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(6L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(4));
    assertThat(new ArrayList<>(result.getData().keySet()), containsInAnyOrder(
      INVOICE_RULE_1_ID, INVOICE_RULE_2_ID, INVOICE_RULE_3_ID, INVOICE_RULE_4_ID
    ));
    assertThat(result.getData().get(INVOICE_RULE_1_ID), is(2L));
    assertThat(result.getData().get(INVOICE_RULE_2_ID), is(1L));
    assertThat(result.getData().get(INVOICE_RULE_3_ID), is(2L));
    assertThat(result.getData().get(INVOICE_RULE_4_ID), is(1L));
  }

  @Test
  public void testCustomOrderOnMatchedRuleKeyIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    // rule 3
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc")
    );
    // rule 4
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final DecisionReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(4));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      // expect ascending order
      contains(new ArrayList<>(resultMap.keySet()).stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnMatchedRuleValueIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    // rule 3
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc")
    );
    // rule 4
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final DecisionReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(4));
    final List<Long> bucketValues = new ArrayList<>(resultMap.values());
    assertThat(
      new ArrayList<>(bucketValues),
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void reportEvaluationMultiBucketsAllVersionsGroupByMatchedRule() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    // rule 3
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc")
    );
    // rule 4
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployDecisionDefinition();

    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto2.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto2.getId(), createInputs(200.0, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(8L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(4));
    assertThat(new ArrayList<>(result.getData().keySet()), containsInAnyOrder(
      INVOICE_RULE_1_ID, INVOICE_RULE_2_ID, INVOICE_RULE_3_ID, INVOICE_RULE_4_ID
    ));

    assertThat(result.getData().get(INVOICE_RULE_1_ID), is(4L));
    assertThat(result.getData().get(INVOICE_RULE_2_ID), is(1L));
    assertThat(result.getData().get(INVOICE_RULE_3_ID), is(2L));
    assertThat(result.getData().get(INVOICE_RULE_4_ID), is(1L));
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByMatchedRuleFilterByInput() {
    // given
    final double inputVariableValueToFilterFor = 300.0;
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(), createInputs(300.0, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .setFilter(createDoubleInputVariableFilter(
        INPUT_AMOUNT_ID, FilterOperatorConstants.GREATER_THAN_EQUALS, String.valueOf(inputVariableValueToFilterFor)
      ))
      .build();
    final DecisionReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    // only matched rule 2 is present
    assertThat(new ArrayList<>(result.getData().keySet()), containsInAnyOrder(INVOICE_RULE_2_ID));
    assertThat(result.getData().get(INVOICE_RULE_2_ID), is(1L));
  }

  @Test
  public void reportEvaluationOnCollectPolicyMultiBucketsAllVersionsGroupByMatchedRule() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDishDecisionDefinition();
    // triggers rule 3 and 5
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, true)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, true)
    );

    // triggers rule 6
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, false)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(3L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(3));
    assertThat(new ArrayList<>(result.getData().keySet()), containsInAnyOrder(
      BEVERAGES_RULE_3_ID, BEVERAGES_RULE_5_ID, BEVERAGES_RULE_6_ID
    ));
    assertThat(result.getData().get(BEVERAGES_RULE_3_ID), is(2L));
    assertThat(result.getData().get(BEVERAGES_RULE_5_ID), is(2L));
    assertThat(result.getData().get(BEVERAGES_RULE_6_ID), is(1L));
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByMatchedRuleOtherDefinitionsHaveNoSideEffect() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // other decision definition
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDishDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto2.getId(), createDishInputs("Winter", 8, true)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(2L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getData().values().stream().findFirst().get(), is(2L));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private DecisionDefinitionEngineDto deployDishDecisionDefinition() {
    return engineRule.deployDecisionDefinition("dmn/decide-dish.xml");
  }

  private HashMap<String, InputVariableEntry> createDishInputs(final String season,
                                                               final Integer guestCount,
                                                               final boolean withChildren) {
    return new HashMap<String, InputVariableEntry>() {{
      put(INPUT_SEASON_ID, new InputVariableEntry(INPUT_SEASON_ID, "Season", VariableType.STRING, season));
      put(
        INPUT_NUMBER_OF_GUESTS_ID,
        new InputVariableEntry(
          INPUT_NUMBER_OF_GUESTS_ID,
          "Number of Guests",
          VariableType.INTEGER,
          guestCount
        )
      );
      put(
        INPUT_GUEST_WITH_CHILDREN_ID,
        new InputVariableEntry(
          INPUT_GUEST_WITH_CHILDREN_ID,
          "Guests with children?",
          VariableType.BOOLEAN,
          withChildren
        )
      );
    }};
  }

  private DecisionReportEvaluationResultDto<DecisionReportMapResultDto> evaluateDecisionInstanceFrequencyByMatchedRule(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    return evaluateMapReport(reportData);
  }

}
