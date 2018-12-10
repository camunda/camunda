package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.service.es.report.command.RawDecisionDataCommand;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;

public class RawDecisionDataReportEvaluationIT {
  private static final String INPUT_AMOUNT_ID = "clause1";
  private static final String INPUT_CATEGORY_ID = "InputClause_15qmk0v";
  private static final String INPUT_INVOICE_DATE_ID = "InputClause_0qixz9e";
  private static final String OUTPUT_AUDIT_ID = "OutputClause_1ur6jbl";
  private static final String OUTPUT_CLASSIFICATION_ID = "clause3";

  private static final String INPUT_VARIABLE_INVOICE_CATEGORY = "invoiceCategory";
  private static final String INPUT_VARIABLE_AMOUNT = "amount";
  private static final String INPUT_VARIABLE_INVOICE_DATE = "invoiceDate";
  public static final String OUTPUT_BEVERAGES = "OuputClause_99999";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  @Test
  public void reportEvaluation() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion = String.valueOf(decisionDefinitionDto.getVersion());

    final HashMap<String, InputVariableEntry> inputs = createInputs(200.0, "Misc");
    final HashMap<String, OutputVariableEntry> expectedOutputs = createOutputs(
      false, "day-to-day expense"
    );
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), inputs);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), decisionDefinitionVersion
    );
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    RawDataDecisionInstanceDto rawDataDecisionInstanceDto = result.getResult().get(0);
    assertThat(rawDataDecisionInstanceDto.getDecisionDefinitionKey(), is(decisionDefinitionDto.getKey()));
    assertThat(rawDataDecisionInstanceDto.getDecisionDefinitionId(), is(decisionDefinitionDto.getId()));
    assertThat(rawDataDecisionInstanceDto.getDecisionInstanceId(), is(notNullValue()));
    assertThat(rawDataDecisionInstanceDto.getEngineName(), is(notNullValue()));
    assertThat(rawDataDecisionInstanceDto.getEvaluationDateTime(), is(notNullValue()));

    final Map<String, InputVariableEntry> receivedInputVariables = rawDataDecisionInstanceDto.getInputVariables();
    assertInputVariablesMatchExcepted(inputs, receivedInputVariables);

    final Map<String, OutputVariableEntry> receivedOutputVariables = rawDataDecisionInstanceDto.getOutputVariables();
    assertOutputVariablesMatchExcepted(expectedOutputs, receivedOutputVariables);

    DecisionReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getDecisionDefinitionKey(), is(decisionDefinitionDto.getKey()));
    assertThat(resultDataDto.getDecisionDefinitionVersion(), is(decisionDefinitionVersion));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(DecisionViewOperation.RAW));
  }

  @Test
  public void reportEvaluationAllVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();

    final HashMap<String, InputVariableEntry> inputs = createInputs(200.0, "Misc");
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), inputs);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    assertThat(result.getData().getDecisionDefinitionVersion(), is(ALL_VERSIONS));
  }

  @Test
  public void reportEvaluationForSpecificKeyIsNotAffectedByOtherDecisionDefinitionInstances() {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDishDecisionDefinition();
    startDishDecisionInstance(decisionDefinitionEngineDto);

    reportEvaluation();
  }

  private void startDishDecisionInstance(final DecisionDefinitionEngineDto decisionDefinitionEngineDto) {
    engineRule.startDecisionInstance(decisionDefinitionEngineDto.getId(), new HashMap<String, Object>() {{
      put("season", "Fall");
      put("guestCount", 1);
      put("guestsWithChildren", true);
    }});
  }

  private DecisionDefinitionEngineDto deployDishDecisionDefinition() {
    return engineRule.deployDecisionDefinition("dmn/decide-dish.xml");
  }

  @Test
  public void reportEvaluationMultipleOutputs() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployDishDecisionDefinition();
    startDishDecisionInstance(decisionDefinitionDto);

    final HashMap<String, OutputVariableEntry> expectedOutputs = new HashMap<String, OutputVariableEntry>() {{
      put(
        OUTPUT_BEVERAGES,
        new OutputVariableEntry(
          // very good beer choice!
          OUTPUT_BEVERAGES, "Beverages", "String", "Aecht Schlenkerla Rauchbier", "Apple Juice"
        )
      );
    }};

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    RawDataDecisionInstanceDto rawDataDecisionInstanceDto = result.getResult().get(0);

    final Map<String, OutputVariableEntry> receivedOutputVariables = rawDataDecisionInstanceDto.getOutputVariables();
    assertOutputVariablesMatchExcepted(expectedOutputs, receivedOutputVariables);
  }


  @Test
  public void resultShouldBeOrderedByDescendingEvaluationDateByDefault() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(5));

    assertThat(
      result.getResult(),
      isInExpectedOrder(
        (currentItem, previousItem) -> currentItem.getEvaluationDateTime()
          .isAfter(previousItem.getEvaluationDateTime()),
        "The given list should be sorted in descending evaluationDateTime order!"
      )
    );
  }

  @Test
  public void resultShouldBeOrderedByIdProperty() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.getParameters().setSorting(new SortingDto(DecisionInstanceType.DECISION_INSTANCE_ID, SortOrder.ASC));
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(5));

    assertThat(
      result.getResult(),
      isInExpectedOrder(
        (currentItem, previousItem) -> currentItem.getDecisionInstanceId()
          .compareTo(previousItem.getDecisionInstanceId()) < 0,
        "The given list should be sorted in ascending decisionInstanceId order!"
      )
    );
  }

  @Test
  public void resultShouldBeOrderedByEvaluationDatePropertyAsc() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.getParameters().setSorting(new SortingDto(DecisionInstanceType.EVALUATION_DATE_TIME, SortOrder.ASC));
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(5));

    assertThat(
      result.getResult(),
      isInExpectedOrder(
        (currentItem, previousItem) -> currentItem.getEvaluationDateTime()
          .isBefore(previousItem.getEvaluationDateTime()),
        "The given list should be sorted in ascending evaluationDateTime order!"
      )
    );
  }

  @Test
  public void resultShouldBeOrderedByInputVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    // use of values like 20 and 100 to ensure ordering is done numeric and not alphanumeric
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(20.0, "Misc"));
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(200.0, "Misc"));
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(300.0, "Misc"));
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(400.0, "Misc"));
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(1000.0, "Misc"));

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.getParameters().setSorting(
      new SortingDto(RawDecisionDataCommand.INPUT_VARIABLE_PREFIX + INPUT_AMOUNT_ID, SortOrder.ASC)
    );
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(5));

    assertThat(
      result.getResult(),
      isInExpectedOrder(
        (currentItem, previousItem) -> {
          final Double currentItemAmountValue = Double.valueOf((String) currentItem.getInputVariables()
            .get(INPUT_AMOUNT_ID)
            .getValue());
          final Double previousItemAmountValue = Double.valueOf((String) previousItem.getInputVariables()
            .get(INPUT_AMOUNT_ID)
            .getValue());
          return currentItemAmountValue.compareTo(previousItemAmountValue) < 0;
        },
        "The given list should be sorted in ascending inputVariable:amount order!"
      )
    );
  }

  @Test
  public void resultShouldBeOrderedByOutputVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    // results in audit false
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(10.0, "Misc"));
    // results in audit true
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(2000.0, "Misc"));
    // results in audit false
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(100.0, "Misc"));
    // results in audit true
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(3000.0, "Misc"));
    // results in audit false
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(200.0, "Misc"));

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.getParameters().setSorting(
      new SortingDto(RawDecisionDataCommand.OUTPUT_VARIABLE_PREFIX + OUTPUT_AUDIT_ID, SortOrder.ASC)
    );
    RawDataDecisionReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(5));

    assertThat(
      result.getResult(),
      isInExpectedOrder(
        (currentItem, previousItem) -> {
          final Boolean currentItemAuditValue = Boolean.valueOf((String) currentItem.getOutputVariables()
            .get(OUTPUT_AUDIT_ID)
            .getFirstValue());
          final Boolean previousItemAuditValue = Boolean.valueOf((String) previousItem.getOutputVariables()
            .get(OUTPUT_AUDIT_ID)
            .getFirstValue());
          return currentItemAuditValue.compareTo(previousItemAuditValue) < 0;
        },
        "The given list should be sorted in ascending outputVariable:audit order!"
      )
    );
  }

  private Matcher<? super List<RawDataDecisionInstanceDto>> isInExpectedOrder(
    final BiFunction<RawDataDecisionInstanceDto, RawDataDecisionInstanceDto, Boolean> biFunction,
    final String reason
  ) {
    return new TypeSafeMatcher<List<RawDataDecisionInstanceDto>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(reason);
      }

      @Override
      protected boolean matchesSafely(List<RawDataDecisionInstanceDto> items) {
        for (int i = (items.size() - 1); i > 0; i--) {
          final RawDataDecisionInstanceDto currentItem = items.get(i);
          final RawDataDecisionInstanceDto previousItem = items.get(i - 1);
          if (biFunction.apply(currentItem, previousItem)) {
            return false;
          }
        }
        return true;
      }
    };
  }

  private void assertInputVariablesMatchExcepted(final HashMap<String, InputVariableEntry> expectedVariables,
                                                 final Map<String, InputVariableEntry> actualVariables) {
    assertThat(actualVariables.size(), is(expectedVariables.size()));
    assertThat(
      actualVariables.keySet(), containsInAnyOrder(expectedVariables.keySet().toArray())
    );
    expectedVariables.forEach((variableId, variableEntry) -> {
      final InputVariableEntry variable = actualVariables.get(variableId);
      assertThat(variable.getId(), is(variableEntry.getId()));
      assertThat(variable.getName(), is(variableEntry.getName()));
      assertThat(variable.getType(), is(variableEntry.getType()));
      assertThat(variable.getValue(), is(String.valueOf(variableEntry.getValue())));
    });
  }

  private void assertOutputVariablesMatchExcepted(final HashMap<String, OutputVariableEntry> expectedVariables,
                                                  final Map<String, OutputVariableEntry> actualVariables) {
    assertThat(actualVariables.size(), is(expectedVariables.size()));
    assertThat(
      actualVariables.keySet(), containsInAnyOrder(expectedVariables.keySet().toArray())
    );
    expectedVariables.forEach((variableId, variableEntry) -> {
      final OutputVariableEntry variable = actualVariables.get(variableId);
      assertThat(variable.getId(), is(variableEntry.getId()));
      assertThat(variable.getName(), is(variableEntry.getName()));
      assertThat(variable.getType(), is(variableEntry.getType()));
      assertThat(
        variable.getValues(),
        containsInAnyOrder(variableEntry.getValues().stream().map(String::valueOf).toArray())
      );
    });
  }

  private void startDecisionInstanceWithInputVars(final String id,
                                                  final HashMap<String, InputVariableEntry> inputVariables) {
    engineRule.startDecisionInstance(
      id,
      inputVariables.entrySet().stream().collect(toMap(
        entry -> getInputVariableNameForId(entry.getKey()),
        entry -> entry.getValue().getValue()
      ))
    );
  }

  private static String getInputVariableNameForId(String inputId) {
    switch (inputId) {
      case INPUT_AMOUNT_ID:
        return INPUT_VARIABLE_AMOUNT;
      case INPUT_CATEGORY_ID:
        return INPUT_VARIABLE_INVOICE_CATEGORY;
      case INPUT_INVOICE_DATE_ID:
        return INPUT_VARIABLE_INVOICE_DATE;
      default:
        throw new IllegalStateException("Unsupported inputVariableId: " + inputId);
    }
  }

  private HashMap<String, InputVariableEntry> createInputs(final double amountValue,
                                                           final String categoryValue) {
    return new HashMap<String, InputVariableEntry>() {{
      put(INPUT_AMOUNT_ID, new InputVariableEntry(INPUT_AMOUNT_ID, "Invoice Amount", "Double", amountValue));
      put(
        INPUT_CATEGORY_ID,
        new InputVariableEntry(INPUT_CATEGORY_ID, "Invoice Category", "String", categoryValue)
      );
    }};
  }

  private HashMap<String, OutputVariableEntry> createOutputs(final boolean auditValue,
                                                             final String classificationValue) {
    return new HashMap<String, OutputVariableEntry>() {{
      put(OUTPUT_AUDIT_ID, new OutputVariableEntry(OUTPUT_AUDIT_ID, "Audit", "Boolean", auditValue));
      put(
        OUTPUT_CLASSIFICATION_ID,
        new OutputVariableEntry(OUTPUT_CLASSIFICATION_ID, "Classification", "String", classificationValue)
      );
    }};
  }

  private RawDataDecisionReportResultDto evaluateReport(DecisionReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataDecisionReportResultDto.class);
  }


  private Response evaluateReportAndReturnResponse(DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

}
